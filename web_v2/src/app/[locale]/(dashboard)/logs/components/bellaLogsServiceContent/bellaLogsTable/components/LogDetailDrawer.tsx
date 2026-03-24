'use client'

import * as React from "react"
import * as DialogPrimitive from "@radix-ui/react-dialog"
import { Activity, X } from "lucide-react"
import { cn } from "@/lib/utils"
import { Card, CardContent } from "@/components/common/card"
import { LogEntry, KeRagInfo } from "@/lib/types/logs"

interface LogDetailDrawerProps {
  /** 是否打开抽屉 */
  open: boolean
  /** 关闭抽屉的回调 */
  onClose: () => void
  /** 日志详情数据 */
  data: LogEntry | KeRagInfo | null
}
function DataField({ label, value, mono = false }: { label: string; value: string | number; mono?: boolean }) {
  const stringValue = String(value)
  const isLongValue = stringValue.length > 50

  // 检测是否为有效的 JSON 字符串
  const isValidJSON = (str: string): boolean => {
    if (typeof str !== 'string' || str.trim().length === 0) return false
    // JSON 字符串通常以 { 或 [ 开头
    if (!str.trim().startsWith('{') && !str.trim().startsWith('[')) return false
    try {
      JSON.parse(str)
      return true
    } catch {
      return false
    }
  }

  const isJSON = isValidJSON(stringValue)

  // 如果是 JSON,格式化展示
  let formattedValue = stringValue
  if (isJSON) {
    try {
      const parsed = JSON.parse(stringValue)
      formattedValue = JSON.stringify(parsed, null, 2)
    } catch {
      // 解析失败,使用原始值
    }
  }

  // JSON 格式使用上下布局,普通文本使用左右布局
  if (isJSON) {
    return (
      <div className="group py-2.5 px-3 rounded-lg hover:bg-secondary/50 transition-colors">
        <div className="flex flex-col gap-2">
          <span className="text-sm text-muted-foreground font-medium">{label}</span>
          <pre className={cn(
            "text-xs text-foreground text-left font-mono",
            "bg-secondary/30 rounded-md p-3",
            "max-h-[300px] overflow-auto",
            "border border-border/50",
            "w-full"
          )}>
            {formattedValue}
          </pre>
        </div>
      </div>
    )
  }

  return (
    <div className="group flex items-start justify-between gap-4 py-2.5 px-3 rounded-lg hover:bg-secondary/50 transition-colors">
      <span className="text-sm text-muted-foreground shrink-0 min-w-[140px]">{label}</span>
      <div className="flex items-center gap-2 flex-1 min-w-0 justify-end">
        <span className={cn(
          "text-sm text-foreground text-right",
          mono && "font-mono text-xs",
          isLongValue && "truncate"
        )}>
          {stringValue || "—"}
        </span>
        {/* <CopyButton value={stringValue} /> */}
      </div>
    </div>
  )
}


/**
 * 日志详情抽屉组件
 * 从右侧滑出展示日志的详细信息
 */
export function LogDetailDrawer({ open, onClose, data }: LogDetailDrawerProps) {
  if (!data) return null

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onClose}>
      <DialogPrimitive.Portal>
        {/* 背景遮罩 */}
        <DialogPrimitive.Overlay
          className={cn(
            "fixed inset-0 z-50 bg-black/50",
            "data-[state=open]:animate-in data-[state=closed]:animate-out",
            "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
          )}
        />

        {/* 抽屉内容 */}
        <DialogPrimitive.Content
          className={cn(
            "fixed right-0 top-0 z-50",
            "h-full overflow-y-auto sm:w-[70vw]",
            "bg-background shadow-xl",
            "flex flex-col",
            "data-[state=open]:animate-in data-[state=closed]:animate-out",
            "data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right",
            "duration-300"
          )}
        >
          {/* 头部 */}
          <div className="flex items-center justify-between border-b px-6 py-4">
            <DialogPrimitive.Title className="text-lg font-semibold text-foreground">
              <div className="flex items-center gap-3">
                <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10">
                  <Activity className="h-5 w-5 text-primary" />
                </div>
                <div>
                  <h1 className="text-lg font-semibold text-foreground">
                    链路查询详情
                  </h1>
                </div>
              </div>
            </DialogPrimitive.Title>
            <DialogPrimitive.Close className="rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
              <X className="h-5 w-5" />
              <span className="sr-only">关闭</span>
            </DialogPrimitive.Close>
          </div>
          <div className="flex-1 overflow-y-auto">
            <Card className="border-0 shadow-none">
              <CardContent className="pt-0 pb-4">
                <div className="space-y-1 divide-y divide-border/50">
                  {/* 追踪信息 */}
                  <div className="pb-3">
                    {
                      Object.entries(data).map(([key, value]) => (
                        <DataField
                          key={key}
                          label={key}
                          value={value}
                          mono
                        />
                      ))
                    }
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

