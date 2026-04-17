"use client"

import { useState, useMemo } from "react"
import { Badge } from "@/components/common/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { FileText } from "lucide-react"
import { safeParseJSON } from "../../utils"

/** 折叠时最多显示的行数 */
const COLLAPSED_LINE_LIMIT = 10

export function RequestPanel({ requestData }: { requestData: string }) {
  const [expanded, setExpanded] = useState(false)

  /** 格式化 JSON，parse 失败则原样显示 */
  const formatted = useMemo(() => {
    const parsed = safeParseJSON<unknown>(requestData)
    return parsed !== null
      ? JSON.stringify(parsed, null, 2)
      : (requestData ?? "")
  }, [requestData])

  const lines = formatted.split("\n")
  const needsFold = lines.length > COLLAPSED_LINE_LIMIT
  const displayText = needsFold && !expanded
    ? lines.slice(0, COLLAPSED_LINE_LIMIT).join("\n") + "\n..."
    : formatted

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
          <Badge
            variant="outline"
            className="border-primary/30 bg-primary/10 text-primary"
          >
            POST
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        <pre className="p-3 rounded-lg bg-muted/50 border border-border text-xs text-foreground font-mono whitespace-pre-wrap break-all leading-relaxed overflow-x-auto">
          {displayText}
        </pre>
        {needsFold && (
          <button
            onClick={() => setExpanded(prev => !prev)}
            className="mt-2 text-xs text-primary hover:underline cursor-pointer"
          >
            {expanded ? "收起" : "展开更多"}
          </button>
        )}
      </CardContent>
    </Card>
  )
}
