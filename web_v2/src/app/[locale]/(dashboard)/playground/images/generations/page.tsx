"use client";

import React, { useState, useMemo, useCallback } from "react";
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
import { ModelSelector } from "../../components";
import { useAuth } from "@/components/providers/auth-provider";
import { ImageOverlay, ImageResultCard } from "../components";
import { parseFeatures, parsePriceRows } from "@/lib/utils/image";
import { Badge } from "@/components/common/badge";
import { getTagColor } from "@/lib/constants/theme";
import { Wand2, ImageIcon } from "lucide-react";

interface ImageData {
  b64_json?: string;
  url?: string;
  revised_prompt?: string;
  output_format?: string;
  quality?: string;
  size?: string;
}

interface ImagesResponse {
  background?: string;
  created?: number;
  data: ImageData[];
}

export default function ImageGenerationsPage() {
  const { user } = useAuth();
  const [model, setModel] = useState("");
  const [prompt, setPrompt] = useState("一只可爱的小猫在草地上玩耍，阳光明媚");
  const [size, setSize] = useState("1024x1024");
  const [quality, setQuality] = useState("standard");
  const [style, setStyle] = useState("vivid");
  // TODO: 多图生成开放后改回 useState(1)
  const n = 1;
  const [response, setResponse] = useState<ImagesResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  const [selectedModel, setSelectedModel] = useState<import("@/lib/types/openapi").Model | undefined>(undefined);
  const features = useMemo(() => parseFeatures(selectedModel?.features), [selectedModel]);
  const supportsQuality = features.has("quality") || features.has("highQuality");
  const supportsStyle = features.has("style") || features.has("multipleStyles");
  const priceRows = useMemo(() => parsePriceRows(selectedModel?.priceDetails?.displayPrice), [selectedModel]);
  const handleModelChange = useCallback((m: import("@/lib/types/openapi").Model | undefined) => setSelectedModel(m), []);

  const generateImages = async () => {
    if (!prompt.trim()) return;

    setIsLoading(true);
    setError("");
    setResponse(null);

    try {
      const protocol = typeof window !== "undefined" ? window.location.protocol : "http:";
      const host = window.location.host;

      const body: Record<string, unknown> = {
        prompt,
        model,
        n,
        size,
        quality,
        style,
      };
      if (user?.userId) body.user = user.userId;

      const res = await fetch(`${protocol}//${host}/v1/images/generations`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        credentials: "include",
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(
          `请求失败: ${res.status} ${errData.error?.message || "未知错误"}`
        );
      }

      const data = await res.json();
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

  return (
    <div className="flex flex-col h-full">
      <TopBar title="文生图" description="AI 图像生成" />

      <main className="flex flex-1 overflow-hidden">
        {/* 左侧内容区 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">

            {/* 提示词输入 */}
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Wand2 className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">图像描述</h3>
              </div>
              <Textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={"描述你想生成的图像...\n\n例如: 一只在太空中漂浮的宇航猫，周围是星云和星星，赛博朋克风格，8k 高清"}
                className="min-h-[150px] resize-none"
                disabled={isLoading}
              />
              <Button
                className="mt-4 w-full"
                size="lg"
                onClick={generateImages}
                disabled={isLoading || !prompt.trim()}
              >
                {isLoading ? "生成中..." : "生成图像"}
              </Button>
            </Card>

            {/* 错误提示 */}
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {/* 生成结果 */}
            <Card className="p-6">
              <h3 className="mb-4 font-semibold">生成结果</h3>
              {isLoading ? (
                <div className="flex items-center justify-center h-48 text-muted-foreground">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary mx-auto mb-3" />
                    <p className="text-sm">正在生成图像，请稍候...</p>
                  </div>
                </div>
              ) : response?.data?.length ? (
                <div className="space-y-4">
                  <div className="grid gap-4 md:grid-cols-2">
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
                    endpoint="/v1/images/generations"
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
                      <SelectItem value="1792x1024">1792 × 1024 (横向)</SelectItem>
                      <SelectItem value="1024x1792">1024 × 1792 (纵向)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {supportsQuality && (
                  <div>
                    <Label className="mb-2 block">图像质量</Label>
                    <Select value={quality} onValueChange={setQuality}>
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
                    <Select value={style} onValueChange={setStyle}>
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
