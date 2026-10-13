"use client";

import { X, Download } from "lucide-react";
import { downloadImage } from "@/lib/utils/image";

interface ImageOverlayProps {
  src: string;
  onClose: () => void;
}

/**
 * 全屏图片预览 Overlay
 * 点击背景或右上角 × 关闭，右上角提供下载按钮
 */
export function ImageOverlay({ src, onClose }: ImageOverlayProps) {
  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    await downloadImage(src, `bella-image-${Date.now()}.png`);
  };

  return (
    <div
      className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="relative max-w-4xl max-h-[90vh] w-full"
        onClick={(e) => e.stopPropagation()}
      >
        <img
          src={src}
          alt="Preview"
          className="w-full h-auto object-contain rounded-lg"
        />
        {/* 操作按钮组 */}
        <div className="absolute top-3 right-3 flex gap-2">
          <button
            className="bg-black/60 hover:bg-black/80 text-white p-2 rounded-full transition-colors"
            onClick={handleDownload}
            title="下载图片"
          >
            <Download className="h-4 w-4" />
          </button>
          <button
            className="bg-black/60 hover:bg-black/80 text-white p-2 rounded-full transition-colors"
            onClick={onClose}
            title="关闭"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
