"use client";

import React, { useRef } from "react";
import { Upload, Link as LinkIcon, FileText, Code } from "lucide-react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/common/tabs";
import { Input } from "@/components/common/input";
import { Textarea } from "@/components/common/textarea";
import { Button } from "@/components/common/button";
import { Label } from "@/components/common/label";
import { ImageInputAreaProps, ImageInputMethod } from "../types";

/**
 * 图片输入区域组件
 * 支持：上传图片 / 图片URL / 文件ID / Base64 四种方式
 */
export function ImageInputArea({
  inputState,
  onInputStateChange,
  onRecognize,
  onClear,
  isLoading,
}: ImageInputAreaProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { inputMethod, uploadImageBase64, imageBase64, imageUrl, fileId } =
    inputState;

  // 获取当前输入方式对应的预览 URL
  const previewUrl =
    inputMethod === "upload"
      ? uploadImageBase64
      : inputMethod === "base64"
      ? getBase64PreviewUrl(imageBase64)
      : inputMethod === "url"
      ? imageUrl
      : "";

  // 处理文件选择
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      onInputStateChange({});
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      onInputStateChange({});
      return;
    }

    const reader = new FileReader();
    reader.onloadend = () => {
      onInputStateChange({ uploadImageBase64: reader.result as string });
    };
    reader.readAsDataURL(file);
    // 重置 input，允许重复选同一文件
    e.target.value = "";
  };

  // 拖拽上传
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (!file || !file.type.startsWith("image/")) return;
    const syntheticEvent = {
      target: { files: [file] },
    } as unknown as React.ChangeEvent<HTMLInputElement>;
    handleFileChange(syntheticEvent);
  };

  // 切换 Tab 时重置对应字段
  const handleTabChange = (value: string) => {
    onInputStateChange({ inputMethod: value as ImageInputMethod });
  };

  // 清除时同时清除文件 input
  const handleClear = () => {
    if (fileInputRef.current) fileInputRef.current.value = "";
    onClear();
  };

  return (
    <div className="flex flex-col h-full">
      {/* 图片输入区 */}
      <div className="rounded-lg border border-border bg-card flex flex-col flex-1 overflow-hidden">
        <Tabs value={inputMethod} onValueChange={handleTabChange} className="flex flex-col flex-1 overflow-hidden">
          {/* Tab 切换栏 */}
          <div className="px-4 pt-4 flex-shrink-0">
            <TabsList className="w-full grid grid-cols-4">
              <TabsTrigger value="upload" className="text-xs gap-1">
                <Upload className="h-3.5 w-3.5" />
                上传图片
              </TabsTrigger>
              <TabsTrigger value="url" className="text-xs gap-1">
                <LinkIcon className="h-3.5 w-3.5" />
                图片 URL
              </TabsTrigger>
              <TabsTrigger value="fileId" className="text-xs gap-1">
                <FileText className="h-3.5 w-3.5" />
                文件 ID
              </TabsTrigger>
              <TabsTrigger value="base64" className="text-xs gap-1">
                <Code className="h-3.5 w-3.5" />
                Base64
              </TabsTrigger>
            </TabsList>
          </div>

          <div className="p-4 flex-1 overflow-y-auto flex flex-col">
            {/* Tab: 上传图片 */}
            <TabsContent value="upload" className="mt-0 flex-1 flex flex-col">
              <div
                className="rounded-lg border-2 border-dashed border-border hover:border-primary/50 transition-colors cursor-pointer flex-1 flex flex-col items-center justify-center"
                onDrop={handleDrop}
                onDragOver={(e) => e.preventDefault()}
                onClick={() => fileInputRef.current?.click()}
              >
                {uploadImageBase64 ? (
                  <div className="flex items-center justify-center p-3 w-full h-full">
                    <img
                      src={uploadImageBase64}
                      alt="预览"
                      className="max-h-full max-w-full object-contain rounded"
                    />
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center py-10 text-center">
                    <Upload className="h-10 w-10 text-muted-foreground mb-3" />
                    <p className="text-sm text-muted-foreground">
                      点击上传或拖拽图片到此处
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">
                      支持 JPG、PNG 等格式，最大 10MB
                    </p>
                  </div>
                )}
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                className="hidden"
              />
            </TabsContent>

            {/* Tab: 图片 URL */}
            <TabsContent
              value="url"
              className={`mt-0 flex-1 flex flex-col gap-3 ${imageUrl ? "justify-start" : "justify-center"}`}
            >
              <div className="space-y-1.5">
                <Label htmlFor="ocr-image-url" className="text-xs">
                  图片 URL
                </Label>
                <Input
                  id="ocr-image-url"
                  type="url"
                  placeholder="https://example.com/image.jpg"
                  value={imageUrl}
                  onChange={(e) => onInputStateChange({ imageUrl: e.target.value })}
                />
              </div>
              {imageUrl && (
                <div className="rounded-lg border border-border flex-1 flex items-center justify-center p-3">
                  <img
                    src={imageUrl}
                    alt="预览"
                    className="max-h-full max-w-full object-contain rounded"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = "none";
                    }}
                  />
                </div>
              )}
            </TabsContent>

            {/* Tab: 文件 ID */}
            <TabsContent value="fileId" className="mt-0 flex-1 flex flex-col justify-center gap-1.5">
              <Label htmlFor="ocr-file-id" className="text-xs">
                文件 ID
              </Label>
              <Input
                id="ocr-file-id"
                placeholder="输入文件服务中的文件 ID"
                value={fileId}
                onChange={(e) => onInputStateChange({ fileId: e.target.value })}
              />
            </TabsContent>

            {/* Tab: Base64 */}
            <TabsContent
              value="base64"
              className={`mt-0 flex-1 flex flex-col gap-3 ${imageBase64 ? "justify-start" : "justify-center"}`}
            >
              <div className="space-y-1.5">
                <Label htmlFor="ocr-base64" className="text-xs">
                  Base64 字符串
                </Label>
                <Textarea
                  id="ocr-base64"
                  placeholder="粘贴图片的 Base64 编码字符串"
                  value={imageBase64}
                  onChange={(e) =>
                    onInputStateChange({ imageBase64: e.target.value.trim() })
                  }
                  className="font-mono text-xs min-h-28 resize-none"
                />
              </div>
              {imageBase64 && getBase64PreviewUrl(imageBase64) && (
                <div className="rounded-lg border border-border flex-1 flex items-center justify-center p-3">
                  <img
                    src={getBase64PreviewUrl(imageBase64)!}
                    alt="预览"
                    className="max-h-full max-w-full object-contain rounded"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = "none";
                    }}
                  />
                </div>
              )}
            </TabsContent>
          </div>
        </Tabs>

        {/* 操作按钮 - 固定在底部 */}
        <div className="px-4 pb-4 flex gap-2 flex-shrink-0 border-t border-border pt-3">
          <Button
            className="flex-1"
            onClick={onRecognize}
            disabled={isLoading}
          >
            {isLoading ? "识别中..." : "开始识别"}
          </Button>
          <Button
            variant="outline"
            onClick={handleClear}
            disabled={isLoading}
          >
            清除
          </Button>
        </div>
      </div>
    </div>
  );
}

/**
 * 将 base64 字符串转换为可用于 img src 的 data URL
 * 已含前缀则直接返回，否则补充 data:image/png;base64, 前缀
 */
function getBase64PreviewUrl(base64: string): string | null {
  if (!base64) return null;
  if (base64.startsWith("data:image")) return base64;
  return `data:image/png;base64,${base64}`;
}
