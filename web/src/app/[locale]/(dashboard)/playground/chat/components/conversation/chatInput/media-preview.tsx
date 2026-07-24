'use client';

import { memo, useCallback } from 'react';
import { X, Play, FileVideo, FileImage } from 'lucide-react';
import { Button } from '@/components/common/button';

/**
 * 媒体文件类型定义
 */
export interface MediaFile {
  id: string;
  type: 'image' | 'video';
  url: string;
  name: string;
  size?: number;
  thumbnail?: string; // 视频缩略图 URL
}

/**
 * MediaPreview 组件属性
 */
export interface MediaPreviewProps {
  files: MediaFile[];
  onRemove: (id: string) => void;
  onPreview?: (file: MediaFile) => void;
  maxItems?: number;
  className?: string;
}

/**
 * 媒体预览组件
 *
 * 职责:
 * - 展示上传的图片和视频缩略图
 * - 支持删除已上传的文件
 * - 支持点击预览文件
 *
 * 性能优化:
 * - 使用 React.memo 避免不必要的重渲染
 * - 使用 useCallback 缓存回调函数
 * - 按文件 id 作为 key 优化列表渲染
 */
const MediaPreviewComponent = ({
  files,
  onRemove,
  onPreview,
  maxItems = 20,
  className = ''
}: MediaPreviewProps) => {
  // 如果没有文件则不渲染
  if (files.length === 0) {
    return null;
  }

  // 限制显示的文件数量
  const displayFiles = files.slice(0, maxItems);
  const remainingCount = files.length - maxItems;

  return (
    <div className={`w-full ${className}`}>
      {/* 弹性布局容器 - 自动换行 */}
      <div className="flex flex-wrap gap-2 p-2">
        {displayFiles.map((file) => (
          <MediaItem
            key={file.id}
            file={file}
            onRemove={onRemove}
            onPreview={onPreview}
          />
        ))}

        {/* 显示剩余文件数量 */}
        {remainingCount > 0 && (
          <div className="w-20 h-20 relative aspect-square rounded-lg border-2 border-dashed border-muted-foreground/25 bg-muted/10 flex items-center justify-center">
            <span className="text-sm text-muted-foreground font-medium">
              +{remainingCount}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * 单个媒体项组件属性
 */
interface MediaItemProps {
  file: MediaFile;
  onRemove: (id: string) => void;
  onPreview?: (file: MediaFile) => void;
}

/**
 * 单个媒体项组件
 *
 * 职责:
 * - 渲染图片或视频缩略图
 * - 悬停时显示删除按钮
 * - 视频显示播放图标
 *
 * 设计说明:
 * - 使用 aspect-square 保持 1:1 比例
 * - 使用 group 实现悬停效果
 * - 使用绝对定位的遮罩层显示操作按钮
 */
const MediaItem = memo(({ file, onRemove, onPreview }: MediaItemProps) => {
  const handleRemove = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onRemove(file.id);
    },
    [file.id, onRemove]
  );

  const handlePreview = useCallback(() => {
    onPreview?.(file);
  }, [file, onPreview]);

  return (
    <div
      className="relative w-20 h-20 aspect-square rounded-lg overflow-hidden border border-border bg-muted group cursor-pointer transition-all duration-200 hover:shadow-md"
      onClick={handlePreview}
    >
      {/* 缩略图内容 */}
      {file.type === 'image' ? (
        <img
          src={file.url}
          alt={file.name}
          className="w-20 h-20 object-cover"
          loading="lazy"
        />
      ) : (
        <div className="relative w-full h-full">
          {/* 视频缩略图或占位符 */}
          {file.thumbnail ? (
            <img
              src={file.thumbnail}
              alt={file.name}
              className="w-20 h-20 object-cover"
              loading="lazy"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center bg-muted">
              <FileVideo className="w-8 h-8 text-muted-foreground" />
            </div>
          )}

          {/* 视频播放图标 */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center">
              <Play className="w-5 h-5 text-white fill-white ml-0.5" />
            </div>
          </div>
        </div>
      )}

      {/* 悬停遮罩层 */}
      <div className="absolute w-20 h-20 inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity duration-200" />

      {/* 删除按钮 */}
      <Button
        variant="destructive"
        size="icon"
        className="absolute top-1 right-1 h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity duration-200 shadow-lg cursor-pointer"
        onClick={handleRemove}
      >
        <X className="h-3 w-3" />
      </Button>

      {/* 文件名提示 (可选) */}
      <div className="absolute bottom-0 left-0 right-0 px-2 py-1 bg-black/60 text-white text-xs truncate opacity-0 group-hover:opacity-100 transition-opacity duration-200">
        {file.name}
      </div>
    </div>
  );
});

MediaItem.displayName = 'MediaItem';

export const MediaPreview = memo(MediaPreviewComponent);
