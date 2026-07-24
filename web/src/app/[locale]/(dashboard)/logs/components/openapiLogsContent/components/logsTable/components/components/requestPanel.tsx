"use client"

import { useState, useMemo, useCallback } from "react"
import { Badge } from "@/components/common/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { CopyDownloadToolbar } from "@/components/common/copy-download-toolbar"
import { FileText, Loader2, Download } from "lucide-react"
import { safeParseJSON } from "../../utils"
import { fetchLogDetail } from "@/lib/api/logs"

const COLLAPSED_LINE_LIMIT = 10
const TRUNCATION_MARKER = "[REMOVED: Log size exceeded"
const getRequestFilename = (requestId?: string) => requestId ? `request-${requestId}.json` : "request-log.json"

interface RequestPanelProps {
  requestData: string
  requestId?: string
  shardPath?: string
}

export function RequestPanel({ requestData, requestId, shardPath }: RequestPanelProps) {
  const [expanded, setExpanded] = useState(false)
  const [fullContent, setFullContent] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isTruncated = useMemo(() => {
    return typeof requestData === 'string' && requestData.includes(TRUNCATION_MARKER)
  }, [requestData])

  const formatted = useMemo(() => {
    const source = fullContent ?? requestData
    const parsed = safeParseJSON<unknown>(source)
    return parsed !== null
      ? JSON.stringify(parsed, null, 2)
      : (source ?? "")
  }, [requestData, fullContent])

  const lines = formatted.split("\n")
  const needsFold = lines.length > COLLAPSED_LINE_LIMIT
  const displayText = needsFold && !expanded
    ? lines.slice(0, COLLAPSED_LINE_LIMIT).join("\n") + "\n..."
    : formatted

  const handleLoadFull = useCallback(async () => {
    if (!requestId || !shardPath) {
      setError("缺少完整日志定位信息")
      return
    }
    setLoading(true)
    setError(null)
    try {
      const detail = await fetchLogDetail(requestId, shardPath)
      if (detail?.request != null) {
        setFullContent(typeof detail.request === 'string' ? detail.request : JSON.stringify(detail.request))
      } else {
        setError("未找到完整内容")
      }
    } catch {
      setError("加载失败，请重试")
    } finally {
      setLoading(false)
    }
  }, [requestId, shardPath])

  if (!requestData) {
    return (
      <Card className="border-border bg-card shadow-[0_1px_4px_0_rgb(0_0_0/0.06)]">
        <CardHeader className="pb-4">
          <CardTitle className="text-base font-medium text-foreground">请求体 (Request)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex-1 min-h-[120px] rounded-lg border border-dashed border-border/80 bg-muted/30 flex items-center justify-center">
            <div className="text-center py-4">
              <div className="mx-auto mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-primary/5">
                <FileText className="h-5 w-5 text-primary/40" />
              </div>
              <p className="text-sm text-muted-foreground">暂无请求数据</p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-border bg-card shadow-[0_1px_4px_0_rgb(0_0_0/0.06)]">
      <CardHeader className="pb-4">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-medium text-foreground">
            请求体 (Request)
          </CardTitle>
          <div className="flex items-center gap-1">
            <CopyDownloadToolbar
              content={formatted}
              filename={getRequestFilename(requestId)}
            />
            <Badge
              variant="outline"
              className="border-primary/30 bg-primary/10 text-primary"
            >
              POST
            </Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <pre className="p-3 rounded-lg bg-muted/50 border border-border text-xs text-foreground font-mono whitespace-pre-wrap break-all leading-relaxed overflow-x-auto">
          {displayText}
        </pre>
        <div className="flex items-center gap-3 mt-2">
          {needsFold && (
            <button
              type="button"
              onClick={(event) => {
                event.preventDefault()
                event.stopPropagation()
                setExpanded(prev => !prev)
              }}
              className="text-xs text-primary hover:underline cursor-pointer"
            >
              {expanded ? "收起" : "展开更多"}
            </button>
          )}
          {isTruncated && !fullContent && (
            <button
              type="button"
              onClick={(event) => {
                event.preventDefault()
                event.stopPropagation()
                handleLoadFull()
              }}
              disabled={loading}
              className="flex items-center gap-1 text-xs text-primary hover:underline cursor-pointer disabled:opacity-50"
            >
              {loading ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <Download className="h-3 w-3" />
              )}
              {loading ? "加载中..." : "加载完整内容"}
            </button>
          )}
        </div>
        {error && (
          <p className="mt-1 text-xs text-destructive">{error}</p>
        )}
      </CardContent>
    </Card>
  )
}
