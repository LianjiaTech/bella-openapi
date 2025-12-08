/**
 * 消息内容类型定义
 */
export interface MessageContent {
  type: "text" | "code" | "reasoning_content" | "image" | "audio"
  content: string
  language?: string
  caption?: string
}

/**
 * 消息类型定义
 */
export interface Message {
  role: "user" | "assistant"
  contents: MessageContent[]
}

