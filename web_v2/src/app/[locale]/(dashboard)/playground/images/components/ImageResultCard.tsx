"use client";

import { Maximize2, Download } from "lucide-react";
import { downloadImage } from "@/lib/utils/image";

interface ImageResultCardProps {
  /** 图片 URL 或 base64 data URI（二选一） */
  url?: string;
  b64Json?: string;
  /** 修正后的提示词 */
  revisedPrompt?: string;
  /** 点击放大回调 */
  onExpand: (src: string) => void;
  /** 序号，用于生成文件名 */
  index: number;
}

/**
 * 生成结果图片卡片
 * - 支持 url 和 base64 两种图片格式
 * - hover 时浮现放大和下载按钮
 * - 显示修正后的 prompt（若有）
 */
export function ImageResultCard({
  url,
  b64Json,
  revisedPrompt,
  onExpand,
  index,
}: ImageResultCardProps) {
  const src = url ?? (b64Json ? `data:image/png;base64,${b64Json}` : null);
  if (!src) return null;

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    await downloadImage(src, `bella-image-${index + 1}-${Date.now()}.png`);
  };

  return (
    <div className="relative group rounded-lg overflow-hidden border bg-muted">
      <img
        src={src}
        alt={`Generated image ${index + 1}`}
        className="w-full h-auto cursor-pointer"
        onClick={() => onExpand(src)}
        loading="lazy"
      />

      {/* hover 操作按钮组 */}
      <div className="absolute top-2 right-2 flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          className="bg-background/90 hover:bg-background text-foreground p-1.5 rounded-md shadow-sm transition-colors"
          onClick={handleDownload}
          title="下载图片"
        >
          <Download className="h-3.5 w-3.5" />
        </button>
        <button
          className="bg-background/90 hover:bg-background text-foreground p-1.5 rounded-md shadow-sm transition-colors"
          onClick={() => onExpand(src)}
          title="放大查看"
        >
          <Maximize2 className="h-3.5 w-3.5" />
        </button>
      </div>

      {/* 修正后的提示词 */}
      {revisedPrompt && (
        <div className="p-3 text-xs text-muted-foreground border-t bg-background/50">
          <span className="font-medium text-foreground">修正后的提示词：</span>
          {revisedPrompt}
        </div>
      )}
    </div>
  );
}
