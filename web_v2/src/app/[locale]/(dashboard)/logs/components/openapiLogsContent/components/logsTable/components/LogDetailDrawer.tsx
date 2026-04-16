'use client'

import * as React from "react"
import * as DialogPrimitive from "@radix-ui/react-dialog"
import { Activity, X } from "lucide-react"
import { cn } from "@/lib/utils"
import type { LogEntry } from "@/lib/types/logs"
import { MetadataPanel } from "./components/metadataPanel"
import { RequestPanel } from "./components/requestPanel"
import { ResponsePanel } from "./components/responsePanel"

interface LogDetailDrawerProps {
  /** 是否打开抽屉 */
  open: boolean
  /** 关闭抽屉的回调 */
  onClose: () => void
  /** 日志详情数据 */
  data: LogEntry | null
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
            "h-full overflow-y-auto sm:w-[80vw]",
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
                    日志请求详情
                  </h1>
                </div>
              </div>
            </DialogPrimitive.Title>
            <DialogPrimitive.Close className="rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
              <X className="h-5 w-5" />
              <span className="sr-only">关闭</span>
            </DialogPrimitive.Close>
          </div>

          {/* 内容区域 - 可滚动 */}
          <div className="flex-1 overflow-y-auto">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
              <div className="flex flex-col gap-6">
                <MetadataPanel data={data as any} />
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <RequestPanel requestData={data.data_info_msg_request} />
                  <ResponsePanel responseData={data.data_info_msg_response} />
                </div>
              </div>
            </div>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

