/**
 * MediaLightbox 组件
 *
 * 职责：
 * - 提供图片和视频的全屏预览功能
 * - 支持在媒体列表中切换浏览
 * - 处理图片和视频两种媒体类型
 * - 提供统一的预览交互体验（键盘、点击关闭等）
 *
 * 代码设计：
 * - 使用 yet-another-react-lightbox 作为底层实现
 * - 将 ImageBlock 和 VideoBlock 转换为 lightbox 支持的数据格式
 * - 图片类型：直接使用 src 属性
 * - 视频类型：使用自定义渲染，嵌入 video 标签
 * - 支持键盘导航（左右箭头切换，ESC 关闭）
 *
 * Re-render 优化：
 * - 使用 React.memo 包装组件，避免父组件更新时不必要的重渲染
 * - 只有当 isOpen/currentIndex/media 真正变化时才会重新渲染
 * - Lightbox 组件本身已经做了内部优化
 */

import React from 'react'
import Lightbox from 'yet-another-react-lightbox'
import 'yet-another-react-lightbox/styles.css'
import type { ImageBlock, VideoBlock } from '@/lib/chat/store/types'

interface MediaItem {
  type: 'image' | 'video'
  src: string
  alt?: string
  thumbnail?: string
}

interface MediaLightboxProps {
  /** 是否打开预览 */
  isOpen: boolean
  /** 当前预览的媒体索引 */
  currentIndex: number
  /** 媒体列表（图片和视频） */
  media: MediaItem[]
  /** 关闭预览的回调 */
  onClose: () => void
}

const MediaLightbox = React.memo(function MediaLightbox({
  isOpen,
  currentIndex,
  media,
  onClose,
}: MediaLightboxProps) {
  // 将媒体数据转换为 lightbox 支持的格式
  const slides = media.map((item) => {
    if (item.type === 'image') {
      return {
        src: item.src,
        alt: item.alt,
      }
    } else {
      // 视频类型：使用自定义渲染
      return {
        type: 'custom-video' as any,
        src: item.src,
        alt: item.alt,
        thumbnail: item.thumbnail,
      }
    }
  })

  return (
    <Lightbox
      open={isOpen}
      close={onClose}
      index={currentIndex}
      slides={slides}
      render={{
        slide: ({ slide }) => {
          // 自定义视频渲染
          if ((slide as any).type === 'custom-video') {
            return (
              <div className="flex items-center justify-center w-full h-full">
                <video
                  src={slide.src}
                  controls
                  autoPlay
                  className="max-w-full max-h-full"
                  poster={(slide as any).thumbnail}
                >
                  您的浏览器不支持视频播放
                </video>
              </div>
            )
          }
          // 图片使用默认渲染
          return undefined
        },
      }}
    />
  )
})

export default MediaLightbox

/**
 * 工具函数：将 ImageBlock 和 VideoBlock 转换为 MediaItem
 */
export function convertBlocksToMedia(
  imageBlocks: ImageBlock[],
  videoBlocks: VideoBlock[]
): MediaItem[] {
  const imageItems: MediaItem[] = imageBlocks.map((block) => ({
    type: 'image',
    src: block.url,
    alt: block.alt,
  }))

  const videoItems: MediaItem[] = videoBlocks.map((block) => ({
    type: 'video',
    src: block.url,
    alt: block.alt,
    thumbnail: block.thumbnail,
  }))

  // 合并图片和视频，保持原始顺序
  return [...imageItems, ...videoItems]
}
