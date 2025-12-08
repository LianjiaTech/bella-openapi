"use client"

import { useState } from "react"
import { MessageContent as MessageContentType } from "../types"
import { MarkdownRenderer } from "./MarkdownRenderer"
import { Button } from "@/components/ui/button"
import {
  Copy,
  Check,
  Brain,
  ChevronDown,
  ChevronUp,
  ImageIcon,
  Volume2,
  Play,
} from "lucide-react"

interface MessageContentProps {
  content: MessageContentType
  messageIndex: number
  contentIndex: number
}

/**
 * 消息内容渲染组件
 * 根据内容类型选择合适的渲染方式
 */
export function MessageContent({ content, messageIndex, contentIndex }: MessageContentProps) {
  const [copiedIndex, setCopiedIndex] = useState<string | null>(null)
  const [isExpanded, setIsExpanded] = useState(true)

  const key = `${messageIndex}-${contentIndex}`

  const copyToClipboard = (text: string, index: string) => {
    navigator.clipboard.writeText(text)
    setCopiedIndex(index)
    setTimeout(() => setCopiedIndex(null), 2000)
  }

  switch (content.type) {
    case "reasoning_content":
      return (
        <div className="my-3 rounded-lg border border-primary/20 bg-primary/5 dark:border-primary/30 dark:bg-primary/10 overflow-hidden">
          <button
            className="w-full flex items-center justify-between p-4 hover:bg-primary/10 dark:hover:bg-primary/20 transition-colors"
            onClick={() => setIsExpanded(!isExpanded)}
          >
            <div className="flex items-center gap-2 text-sm font-medium text-primary dark:text-primary">
              <Brain className="h-4 w-4" />
              思考过程
            </div>
            {isExpanded ? (
              <ChevronUp className="h-4 w-4 text-primary dark:text-primary" />
            ) : (
              <ChevronDown className="h-4 w-4 text-primary dark:text-primary" />
            )}
          </button>
          {isExpanded && (
            <div className="px-4 pb-4">
              <MarkdownRenderer content={content.content} />
            </div>
          )}
        </div>
      )

    case "code":
      return (
        <div className="my-3 rounded-lg border bg-muted/50 overflow-hidden">
          <div className="flex items-center justify-between border-b bg-muted px-4 py-2">
            <span className="text-xs font-medium text-muted-foreground">
              {content.language || "code"}
            </span>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 gap-1.5 px-2"
              onClick={() => copyToClipboard(content.content, key)}
            >
              {copiedIndex === key ? (
                <>
                  <Check className="h-3 w-3" />
                  <span className="text-xs">已复制</span>
                </>
              ) : (
                <>
                  <Copy className="h-3 w-3" />
                  <span className="text-xs">复制</span>
                </>
              )}
            </Button>
          </div>
          <pre className="overflow-x-auto p-4">
            <code className="text-xs leading-relaxed">{content.content}</code>
          </pre>
        </div>
      )

    case "image":
      return (
        <div className="my-3">
          <div className="rounded-lg border bg-muted/30 overflow-hidden">
            <img
              src={content.content || "/placeholder.svg"}
              alt={content.caption || "Generated image"}
              className="w-full h-auto"
            />
            {content.caption && (
              <div className="flex items-center gap-2 border-t bg-muted/50 px-4 py-2">
                <ImageIcon className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-xs text-muted-foreground">{content.caption}</span>
              </div>
            )}
          </div>
        </div>
      )

    case "audio":
      return (
        <div className="my-3">
          <div className="rounded-lg border bg-muted/30 overflow-hidden">
            <div className="flex items-center gap-3 p-4">
              <Volume2 className="h-10 w-10 text-primary" />
              <div className="flex-1">
                <p className="text-sm font-medium">生成的音频内容</p>
                <p className="text-xs text-muted-foreground">
                  {content.caption || "点击播放音频"}
                </p>
              </div>
              <Button size="sm" variant="outline">
                <Play className="h-4 w-4 mr-2" />
                播放
              </Button>
            </div>
          </div>
        </div>
      )

    case "text":
    default:
      return (
        <div className="text-sm leading-relaxed">
          <MarkdownRenderer content={content.content} />
        </div>
      )
  }
}

