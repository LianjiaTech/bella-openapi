"use client"

import { Message } from "../types"
import { MessageContent } from "./MessageContent"
import { Bot, User } from "lucide-react"

interface MessageItemProps {
  message: Message
  messageIndex: number
}

/**
 * 单条消息组件
 * 包含头像、昵称和内容列表
 */
export function MessageItem({ message, messageIndex }: MessageItemProps) {
  return (
    <div className="flex gap-3">
      <div
        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
          message.role === "assistant" ? "bg-primary/10" : "bg-muted"
        }`}
      >
        {message.role === "assistant" ? (
          <Bot className="h-4 w-4 text-primary" />
        ) : (
          <User className="h-4 w-4" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="mb-2 text-sm font-medium text-muted-foreground">
          {message.role === "assistant" ? "助手" : "你"}
        </p>
        <div className="space-y-2">
          {message.contents.map((content, contentIndex) => (
            <MessageContent
              key={contentIndex}
              content={content}
              messageIndex={messageIndex}
              contentIndex={contentIndex}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

