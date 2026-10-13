"use client"

import { Download } from "lucide-react"
import { CopyButton } from "./copy-button"
import { downloadAsFile } from "@/lib/utils/clipboard"

interface CopyDownloadToolbarProps {
  content: string
  filename: string
  mimeType?: string
}

export function CopyDownloadToolbar({ content, filename, mimeType = "application/json" }: CopyDownloadToolbarProps) {
  return (
    <div className="flex items-center gap-1">
      <CopyButton value={content} />
      <button
        type="button"
        title="下载"
        className="h-7 w-7 inline-flex items-center justify-center rounded-md hover:bg-muted cursor-pointer"
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          downloadAsFile(content, filename, mimeType)
        }}
      >
        <Download className="h-3.5 w-3.5 text-muted-foreground" />
      </button>
    </div>
  )
}
