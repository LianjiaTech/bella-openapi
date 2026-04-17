"use client";

import React, { useState, useRef, useMemo, useCallback } from "react";
import { TopBar } from "@/components/layout";
import { Card } from "@/components/common/card";
import { Button } from "@/components/common/button";
import { Textarea } from "@/components/common/textarea";
import { Alert, AlertDescription } from "@/components/common/alert";
import { Label } from "@/components/common/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/common/tabs";
import { ModelSelector } from "../../components";
import { useAuth } from "@/components/providers/auth-provider";
import { ImageOverlay, ImageResultCard } from "../components";
import { parseFeatures, parsePriceRows } from "@/lib/utils/image";
import { Badge } from "@/components/common/badge";
import { getTagColor } from "@/lib/constants/theme";
import {
  Upload,
  X,
  Link as LinkIcon,
  Image as ImageIcon,
  Trash2,
  Wand2,
} from "lucide-react";
import { editImages } from "@/lib/api/playground";

interface ImageItem {
  id: string;
  file?: File;
  url?: string;
  preview: string;
  isLoading?: boolean;
  error?: string;
}

interface ResultImage {
  b64_json?: string;
  url?: string;
  revised_prompt?: string;
  output_format?: string;
  quality?: string;
  size?: string;
}

export default function ImageEditsPage() {
  const { user } = useAuth();
  const [model, setModel] = useState("");
  const [prompt, setPrompt] = useState("一只可爱的小猫在草地上玩耍，阳光明媚");
  const [size, setSize] = useState("1024x1024");
  // TODO: 多图生成开放后改回 useState(1)
  const n = 1;

  const [selectedModel, setSelectedModel] = useState<import("@/lib/types/openapi").Model | undefined>(undefined);
  const features = useMemo(() => parseFeatures(selectedModel?.features), [selectedModel]);
  const supportsQuality = features.has("quality") || features.has("highQuality");
  const supportsStyle = features.has("style") || features.has("multipleStyles");
  const priceRows = useMemo(() => parsePriceRows(selectedModel?.priceDetails?.displayPrice), [selectedModel]);
  const handleModelChange = useCallback((m: import("@/lib/types/openapi").Model | undefined) => setSelectedModel(m), []);

  const [images, setImages] = useState<ImageItem[]>([]);
  const [urlInput, setUrlInput] = useState("");
  const [isUrlLoading, setIsUrlLoading] = useState(false);
  const [selectedTab, setSelectedTab] = useState("upload");
  const [response, setResponse] = useState<{ data: ResultImage[]; created?: number } | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const generateId = () => Math.random().toString(36).substring(2, 11);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    files.forEach((file) => {
      const id = generateId();
      const reader = new FileReader();
      reader.onload = (event) => {
        const dataUrl = event.target?.result as string;
        setImages((prev) => [...prev, { id, file, preview: dataUrl }]);
      };
      reader.readAsDataURL(file);
    });

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const removeImage = (id: string) => {
    setImages((prev) => prev.filter((img) => img.id !== id));
  };

  const clearAllImages = () => {
    setImages([]);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const fetchImageFromUrl = async () => {
    if (!urlInput.trim()) {
      setError("请输入有效的图片URL");
      return;
    }

    setIsUrlLoading(true);
    setError("");

    const id = generateId();
    setImages((prev) => [
      ...prev,
      { id, url: urlInput, preview: "", isLoading: true },
    ]);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    try {
      const res = await fetch("/api/fetch-image", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: urlInput }),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.error || `获取图像失败: ${res.status}`);
      }

      const data = await res.json();

      if (data.success) {
        const base64Response = await fetch(data.imageData);
        const blob = await base64Response.blob();
        const fileName = urlInput.split("/").pop() || "image.png";
        const file = new File([blob], fileName, { type: blob.type });

        setImages((prev) =>
          prev.map((img) =>
            img.id === id
              ? { ...img, file, preview: data.imageData, isLoading: false }
              : img
          )
        );
        setUrlInput("");
      } else {
        throw new Error(data.error || "获取图像失败");
      }
    } catch (err) {
      clearTimeout(timeoutId);
      const errorMessage =
        err instanceof DOMException && err.name === "AbortError"
          ? "请求超时：无法获取图片，请检查URL是否正确"
          : err instanceof Error
          ? err.message
          : "获取图像失败";

      setImages((prev) =>
        prev.map((img) =>
          img.id === id ? { ...img, isLoading: false, error: errorMessage } : img
        )
      );
    } finally {
      setIsUrlLoading(false);
    }
  };

  const handleUrlKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      fetchImageFromUrl();
    }
  };

  const hasValidInput = () =>
    prompt.trim() &&
    images.length > 0 &&
    !images.some((img) => img.isLoading || img.error);

  const generateImages = async () => {
    if (!prompt.trim()) { setError("请输入描述文字"); return; }
    if (images.length === 0) { setError("请上传图片或提供图片URL"); return; }
    if (images.some((img) => img.isLoading)) { setError("请等待所有图片加载完成"); return; }
    if (images.some((img) => img.error)) { setError("请移除加载失败的图片"); return; }

    setIsLoading(true);
    setError("");

    try {
      const formData = new FormData();
      formData.append("prompt", prompt);
      if (model) formData.append("model", model);
      if (user?.userId) formData.append("user", String(user.userId));
      formData.append("n", String(n));
      formData.append("size", size);

      images.filter((img) => img.file).forEach((img) => formData.append("image", img.file!));
      images.filter((img) => img.url && !img.file).forEach((img) => formData.append("image_url", img.url!));

      const data = await editImages(formData);
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "未知错误");
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      generateImages();
    }
  };

  const ImageGrid = ({ items }: { items: ImageItem[] }) => (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
      {items.map((image) => (
        <div key={image.id} className="relative group border rounded-lg overflow-hidden bg-muted">
          {image.isLoading ? (
            <div className="w-full h-24 flex items-center justify-center">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
            </div>
          ) : image.error ? (
            <div className="w-full h-24 flex items-center justify-center bg-destructive/10 p-2">
              <p className="text-xs text-destructive text-center break-words">{image.error}</p>
            </div>
          ) : (
            <div className="w-full h-24 overflow-hidden">
              <img
                src={image.preview}
                alt="Preview"
                className="w-full h-full object-cover cursor-pointer hover:scale-110 transition-transform duration-200"
                onClick={() => setSelectedImage(image.preview)}
              />
            </div>
          )}
          <button
            onClick={() => removeImage(image.id)}
            className="absolute top-1 right-1 bg-background/90 text-destructive p-1 rounded opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      ))}
    </div>
  );

  return (
    <div className="flex flex-col h-full">
      <TopBar title="图生图 Playground" description="AI 图像编辑" />

      <main className="flex flex-1 overflow-hidden">
        {/* 左侧内容区 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">

            {/* 参考图片 */}
            <Card className="p-6">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Upload className="h-5 w-5 text-primary" />
                  <h3 className="font-semibold">参考图片</h3>
                </div>
                {images.length > 0 && (
                  <Button variant="ghost" size="sm" onClick={clearAllImages} className="text-destructive hover:text-destructive">
                    <Trash2 className="h-4 w-4 mr-1" />
                    清空
                  </Button>
                )}
              </div>

              <Tabs value={selectedTab} onValueChange={setSelectedTab}>
                <TabsList className="mb-4">
                  <TabsTrigger value="upload" className="flex items-center gap-1">
                    <ImageIcon className="h-4 w-4" />
                    上传图片
                  </TabsTrigger>
                  <TabsTrigger value="url" className="flex items-center gap-1">
                    <LinkIcon className="h-4 w-4" />
                    图片 URL
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="upload">
                  <label className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 p-8 cursor-pointer hover:border-primary/50 transition-colors mb-4">
                    <Upload className="mb-3 h-10 w-10 text-muted-foreground" />
                    <p className="mb-1 text-sm font-medium">点击上传参考图片</p>
                    <p className="text-xs text-muted-foreground">支持 JPG, PNG, WebP 格式，可多选</p>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      multiple
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                  </label>
                  {images.length > 0 && <ImageGrid items={images} />}
                </TabsContent>

                <TabsContent value="url">
                  <div className="flex gap-2 mb-4">
                    <input
                      type="text"
                      placeholder="https://example.com/image.png"
                      className="flex-1 px-3 py-2 border border-input rounded-md text-sm bg-background focus:outline-none focus:ring-1 focus:ring-ring"
                      value={urlInput}
                      onChange={(e) => setUrlInput(e.target.value)}
                      onKeyDown={handleUrlKeyDown}
                      disabled={isUrlLoading}
                    />
                    <Button onClick={fetchImageFromUrl} disabled={isUrlLoading || !urlInput.trim()} size="sm">
                      {isUrlLoading ? (
                        <>
                          <div className="animate-spin mr-1 h-3 w-3 border-2 border-b-transparent border-white rounded-full" />
                          加载中
                        </>
                      ) : "加载图片"}
                    </Button>
                  </div>
                  {images.length > 0 ? (
                    <ImageGrid items={images} />
                  ) : (
                    <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
                      <ImageIcon className="h-10 w-10 text-muted-foreground/30 mb-2" />
                      <p className="text-sm">输入图片 URL 并点击加载</p>
                    </div>
                  )}
                </TabsContent>
              </Tabs>
            </Card>

            {/* 编辑描述 */}
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Wand2 className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">图像修改描述</h3>
              </div>
              <Textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={"描述你想如何修改这张图片...\n\n例如: 保持主体不变，将背景改为夕阳下的海滩"}
                className="min-h-[150px] resize-none"
                disabled={isLoading}
              />
              <div className="mt-2 mb-4 text-xs text-muted-foreground">
                已选 {images.length} 张图片 · Ctrl+Enter 快速生成
              </div>
              <Button
                className="w-full"
                size="lg"
                onClick={generateImages}
                disabled={isLoading || !hasValidInput()}
              >
                {isLoading ? (
                  <>
                    <div className="animate-spin mr-2 h-4 w-4 border-2 border-b-transparent border-white rounded-full" />
                    生成中...
                  </>
                ) : "生成修改后的图像"}
              </Button>
            </Card>

            {/* 错误提示 */}
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {/* 编辑结果 */}
            <Card className="p-6">
              <h3 className="mb-4 font-semibold">编辑结果</h3>
              {isLoading ? (
                <div className="flex items-center justify-center h-48 text-muted-foreground">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary mx-auto mb-3" />
                    <p className="text-sm">正在编辑图像，请稍候...</p>
                  </div>
                </div>
              ) : response?.data?.length ? (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {response.data.map((image, index) => (
                      <ImageResultCard
                        key={index}
                        index={index}
                        url={image.url}
                        b64Json={image.b64_json}
                        revisedPrompt={image.revised_prompt}
                        onExpand={setSelectedImage}
                      />
                    ))}
                  </div>
                  {response.created && (
                    <p className="text-xs text-muted-foreground">
                      生成时间: {new Date(response.created * 1000).toLocaleString()}
                    </p>
                  )}
                </div>
              ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {Array.from({ length: n }).map((_, i) => (
                    <div key={i} className="flex aspect-square items-center justify-center rounded-lg bg-muted">
                      <ImageIcon className="h-16 w-16 text-muted-foreground/30" />
                    </div>
                  ))}
                </div>
              )}
            </Card>

          </div>
        </div>

        {/* 右侧配置面板 */}
        <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
          <div className="space-y-6">

            {/* 模型配置 */}
            <div>
              <h3 className="mb-4 font-semibold">模型配置</h3>
              <div className="space-y-4">

                <div>
                  <Label className="mb-2 block">模型选择</Label>
                  <ModelSelector
                    value={model}
                    onValueChange={setModel}
                    onModelChange={handleModelChange}
                    endpoint="/v1/images/edits"
                  />
                </div>

                <div>
                  <Label className="mb-2 block">图像尺寸</Label>
                  <Select value={size} onValueChange={setSize}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="256x256">256 × 256</SelectItem>
                      <SelectItem value="512x512">512 × 512</SelectItem>
                      <SelectItem value="1024x1024">1024 × 1024 (方形)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {supportsQuality && (
                  <div>
                    <Label className="mb-2 block">图像质量</Label>
                    <Select defaultValue="standard">
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="hd">HD（高清）</SelectItem>
                        <SelectItem value="standard">Standard（标准）</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {supportsStyle && (
                  <div>
                    <Label className="mb-2 block">风格</Label>
                    <Select defaultValue="vivid">
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="vivid">Vivid（鲜艳）</SelectItem>
                        <SelectItem value="natural">Natural（自然）</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {/* TODO: 多图生成 — 待模型能力对齐后开放，当前固定 n=1 */}
                <div>
                  <Label className="mb-2 block">生成数量: {n}</Label>
                </div>
                {/*
                <div>
                  <Slider
                    min={1}
                    max={4}
                    step={1}
                    value={[n]}
                    onValueChange={(val) => setN(val[0])}
                  />
                  <p className="mt-2 text-xs text-muted-foreground">一次生成 1–4 张图片</p>
                </div>
                */}

              </div>
            </div>

            {/* 模型信息 */}
            {selectedModel && (
              <div className="border-t pt-6">
                <h4 className="mb-3 text-sm font-medium">模型信息</h4>
                <div className="space-y-3 text-xs text-muted-foreground">
                  {selectedModel.ownerName && (
                    <div className="flex justify-between">
                      <span>提供方</span>
                      <span className="font-medium text-foreground">{selectedModel.ownerName}</span>
                    </div>
                  )}
                  {features.size > 0 && (
                    <div>
                      <div className="mb-1.5 text-muted-foreground">支持特性</div>
                      <div className="flex flex-wrap gap-1">
                        {Array.from(features).map((f) => (
                          <Badge
                            key={f}
                            variant="outline"
                            className={`text-[10px] px-1.5 py-0 font-normal ${getTagColor(f)}`}
                          >
                            {f}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  )}
                  {priceRows.map(({ label, lines }) => (
                    <div key={label}>
                      <div className="font-medium text-foreground mb-0.5">{label}</div>
                      {lines.map((line, i) => (
                        <div key={i} className="pl-2">{line}</div>
                      ))}
                    </div>
                  ))}
                  {!selectedModel.ownerName && features.size === 0 && priceRows.length === 0 && (
                    <p>暂无详细信息</p>
                  )}
                </div>
              </div>
            )}

          </div>
        </div>
      </main>

      {selectedImage && (
        <ImageOverlay src={selectedImage} onClose={() => setSelectedImage(null)} />
      )}
    </div>
  );
}
