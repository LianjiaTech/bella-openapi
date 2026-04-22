/**
 * UserBubble 组件
 *
 * 职责：
 * - 渲染用户发送的消息气泡
 * - 展示右对齐的蓝色气泡样式
 * - 支持多模态内容：文本 + 图片/视频
 * - 从 TextBlock 的 segments 数组中提取文本内容
 * - 从 ImageBlock 中展示图片
 * - 从 VideoBlock 中展示视频（独立类型，类型安全）
 * - 支持点击图片/视频进行全屏预览（lightbox）
 *
 * 代码设计：
 * - 使用 Store 的 MessageBlock 类型，与数据结构对齐
 * - 文本提取：segments.join('') 获取已固化的完整文本
 * - 图片/视频展示：使用网格布局展示媒体文件
 * - 类型守卫：通过 block.type 精确判断，无需正则匹配
 * - 用户消息为 status='done'，不会再变化，无需考虑流式更新
 * - 媒体预览：使用 MediaLightbox 组件，点击媒体时打开预览
 *
 * Re-render 优化：
 * - 本组件为纯展示组件，不订阅 store
 * - 由父组件 MessageItem 精确订阅 messageMap[id]
 * - 使用 React.memo 阻止父组件 re-render 时的连带 re-render
 * - ImageItem 和 VideoItem 子组件也使用 memo 避免不必要的重渲染
 * - 只有当 message props 引用真正变化时才会重新渲染
 * - 用户消息是静态内容(status='done'),添加 memo 后理论上只渲染一次
 * - 使用 useCallback 缓存事件处理函数，避免子组件不必要的 re-render
 * - 使用 useMemo 缓存媒体数据转换结果
 */

import React, { useState, useCallback, useMemo } from 'react'
import type { TextBlock, ImageBlock, VideoBlock, MessageBlock } from '@/lib/chat/store/types'
import { User, Play, FileVideo } from 'lucide-react'
import MediaLightbox, { convertBlocksToMedia } from './MediaLightbox'

interface UserBubbleProps {
  message: {
    id: string
    role: "user"
    blocks: MessageBlock[]
  }
}

const UserBubble = React.memo(function UserBubble({ message }: UserBubbleProps) {
  // 媒体预览状态管理
  const [isLightboxOpen, setIsLightboxOpen] = useState(false)
  const [currentMediaIndex, setCurrentMediaIndex] = useState(0)

  // 提取所有 text block 的文本内容
  // TextBlock 使用三层缓冲架构，用户消息已固化到 segments 数组
  const textContent = message.blocks
    .filter((block): block is TextBlock => block.type === "text")
    .map((block) => block.segments.join(''))
    .join("")

  // 提取所有 image block
  const imageBlocks = message.blocks.filter(
    (block): block is ImageBlock => block.type === "image"
  )

  // 提取所有 video block
  const videoBlocks = message.blocks.filter(
    (block): block is VideoBlock => block.type === "video"
  )

  // 将 blocks 转换为 lightbox 需要的媒体数据格式（使用 useMemo 缓存）
  const mediaItems = useMemo(
    () => convertBlocksToMedia(imageBlocks, videoBlocks),
    [imageBlocks, videoBlocks]
  )

  // 打开媒体预览（使用 useCallback 避免子组件不必要的 re-render）
  const handleMediaClick = useCallback((index: number) => {
    setCurrentMediaIndex(index)
    setIsLightboxOpen(true)
  }, [])

  // 关闭媒体预览
  const handleCloseLightbox = useCallback(() => {
    setIsLightboxOpen(false)
  }, [])

  return (
    <>
      <div className="flex justify-end items-start gap-2">
        <div className="max-w-[70%] px-4 py-2 flex flex-col gap-2">
          {/* 媒体网格 */}
          {(imageBlocks.length > 0 || videoBlocks.length > 0) && (
            <div className="flex flex-wrap gap-2">
              {/* 图片 */}
              {imageBlocks.map((block, index) => (
                <ImageItem
                  key={block.id}
                  block={block}
                  onClick={() => handleMediaClick(index)}
                />
              ))}
              {/* 视频 */}
              {videoBlocks.map((block, index) => (
                <VideoItem
                  key={block.id}
                  block={block}
                  onClick={() => handleMediaClick(imageBlocks.length + index)}
                />
              ))}
            </div>
          )}
          {/* 文本内容 */}
          {textContent && (
            <p className="whitespace-pre-wrap break-words text-sm">{textContent}</p>
          )}
        </div>

        {/* 用户头像 */}
        <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
          <User className="w-5 h-5 text-white" />
        </div>
      </div>

      {/* 媒体预览 Lightbox */}
      <MediaLightbox
        isOpen={isLightboxOpen}
        currentIndex={currentMediaIndex}
        media={mediaItems}
        onClose={handleCloseLightbox}
      />
    </>
  )
})

/**
 * ImageItem 组件
 *
 * 职责：
 * - 渲染单个图片
 * - 支持 lazy loading
 * - 支持点击预览
 *
 * 设计说明：
 * - 使用 aspect-square 保持 1:1 比例
 * - 使用 object-cover 确保图片填充容器
 * - 添加 cursor-pointer 和 hover 效果提示可点击
 * - 点击时触发 onClick 回调打开 lightbox 预览
 *
 * Re-render 优化：
 * - 使用 React.memo 避免父组件 re-render 时的连带更新
 * - 用户消息是静态内容，理论上只渲染一次
 */
const ImageItem = React.memo(function ImageItem({
  block,
  onClick
}: {
  block: ImageBlock
  onClick: () => void
}) {
  return (
    <div
      className="relative w-20 h-20 aspect-square rounded-lg overflow-hidden border border-border bg-muted cursor-pointer hover:opacity-80 transition-opacity"
      onClick={onClick}
    >
      <img
        src={block.url}
        alt={block.alt || 'User uploaded image'}
        className="w-20 h-20 object-cover"
        loading="lazy"
      />
    </div>
  )
})

/**
 * VideoItem 组件
 *
 * 职责：
 * - 渲染单个视频缩略图
 * - 显示播放图标和时长
 * - 支持点击预览
 *
 * 设计说明：
 * - 使用 aspect-square 保持 1:1 比例
 * - 优先使用 block.thumbnail 作为缩略图
 * - 如果没有缩略图，显示视频图标占位符
 * - 显示时长信息（如果有）
 * - 播放图标始终显示在中心
 * - 添加 cursor-pointer 和 hover 效果提示可点击
 * - 点击时触发 onClick 回调打开 lightbox 预览
 *
 * Re-render 优化：
 * - 使用 React.memo 避免父组件 re-render 时的连带更新
 * - 用户消息是静态内容，理论上只渲染一次
 */
const VideoItem = React.memo(function VideoItem({
  block,
  onClick
}: {
  block: VideoBlock
  onClick: () => void
}) {
  // 格式化时长显示（秒 → MM:SS）
  const formatDuration = (seconds?: number): string => {
    if (!seconds) return ''
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  return (
    <div
      className="relative w-20 h-20 aspect-square rounded-lg overflow-hidden border border-border bg-muted cursor-pointer hover:opacity-80 transition-opacity"
      onClick={onClick}
    >
      {/* 视频缩略图或占位符 */}
      {block.thumbnail ? (
        <img
          src={block.thumbnail}
          alt={block.alt || 'User uploaded video'}
          className="w-20 h-20 object-cover"
          loading="lazy"
        />
      ) : (
        <div className="w-full h-full flex items-center justify-center bg-muted">
          <FileVideo className="w-8 h-8 text-muted-foreground" />
        </div>
      )}

      {/* 播放图标覆盖层 */}
      <div className="absolute inset-0 flex items-center justify-center">
        <div className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center">
          <Play className="w-5 h-5 text-white fill-white ml-0.5" />
        </div>
      </div>

      {/* 时长显示（右下角） */}
      {block.duration && (
        <div className="absolute bottom-1 right-1 px-1.5 py-0.5 bg-black/70 rounded text-white text-xs font-medium">
          {formatDuration(block.duration)}
        </div>
      )}
    </div>
  )
})

export default UserBubble
