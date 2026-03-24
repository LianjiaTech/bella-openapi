/**
 * UserBubble 组件
 *
 * 职责：
 * - 渲染用户发送的消息气泡
 * - 展示右对齐的蓝色气泡样式
 * - 从 TextBlock 的 segments 数组中提取文本内容
 *
 * 代码设计：
 * - 使用 Store 的 TextBlock 类型，与数据结构对齐
 * - 文本提取：segments.join('') 获取已固化的完整文本
 * - 用户消息为 status='done'，不会再变化，无需考虑流式更新
 *
 * Re-render 优化：
 * - 本组件为纯展示组件，不订阅 store
 * - 由父组件 MessageItem 精确订阅 messageMap[id]
 * - 使用 React.memo 阻止父组件 re-render 时的连带 re-render
 * - 只有当 message props 引用真正变化时才会重新渲染
 * - 用户消息是静态内容(status='done'),添加 memo 后理论上只渲染一次
 */

import React from 'react'
import type { TextBlock } from '@/lib/chat/store/types'
import {User} from 'lucide-react'

interface UserBubbleProps {
  message: {
    id: string
    role: "user"
    blocks: TextBlock[]
  }
}

const UserBubble = React.memo(function UserBubble({ message }: UserBubbleProps) {
  // 提取所有 text block 的文本内容
  // TextBlock 使用三层缓冲架构，用户消息已固化到 segments 数组
  const textContent = message.blocks
    .filter((block) => block.type === "text")
    .map((block) => block.segments.join(''))
    .join("")

  return (
    <div className="flex justify-end items-start gap-2">
      <div className="max-w-[70%] px-4 py-2">
        <p className="whitespace-pre-wrap break-words text-sm ">{textContent}</p>
      </div>
      {/* 用户头像 */}
      <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
        <User className="w-5 h-5 text-white" />
      </div>
    </div>
  )
})

export default UserBubble
