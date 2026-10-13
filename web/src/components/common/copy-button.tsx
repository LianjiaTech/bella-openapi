"use client"

import { useState } from "react"
import { Copy, Check } from "lucide-react"
import { copyToClipboard } from "@/lib/utils/clipboard"

interface CopyButtonProps {
  value: string
  className?: string
}

export function CopyButton({ value, className }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      title="复制"
      className={className ?? "h-7 w-7 inline-flex items-center justify-center rounded-md hover:bg-muted cursor-pointer shrink-0"}
      onClick={async (event) => {
        event.preventDefault()
        event.stopPropagation()
        const success = await copyToClipboard(value)
        if (success) {
          setCopied(true)
          setTimeout(() => setCopied(false), 2000)
        }
      }}
    >
      {copied ? <Check className="h-3.5 w-3.5 text-green-500" /> : <Copy className="h-3.5 w-3.5 text-muted-foreground" />}
    </button>
  )
}
