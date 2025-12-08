"use client"

import { Message } from "../types"
import { MessageItem } from "./MessageItem"

interface MessageListProps {
  messages: Message[]
}

/**
 * 消息列表组件
 * 负责渲染所有消息
 */
export function MessageList({ messages }: MessageListProps) {
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {messages.map((message, messageIndex) => (
        <MessageItem key={messageIndex} message={message} messageIndex={messageIndex} />
      ))}
    </div>
  )
}

