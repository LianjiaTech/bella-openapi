"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, AlertCircle } from "lucide-react";
import { Skeleton } from "@/components/common/skeleton";
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/common/collapsible";
import { OCRResultProps } from "../types";

/**
 * 递归渲染单个值（string / number / array / null）
 */
function RenderValue({ value }: { value: unknown }) {
  if (value === null || value === undefined) {
    return <span className="text-muted-foreground italic">无</span>;
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return <span className="text-muted-foreground italic">空</span>;
    }
    if (value.length === 1) {
      return <span>{String(value[0])}</span>;
    }
    return (
      <div className="space-y-0.5">
        {value.map((item, i) => (
          <div key={i} className="text-foreground">{String(item)}</div>
        ))}
      </div>
    );
  }

  return <span className="text-foreground">{String(value)}</span>;
}

/**
 * 加载骨架屏
 */
function LoadingSkeleton() {
  return (
    <div className="space-y-3 p-4">
      <Skeleton className="h-4 w-24" />
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex gap-4">
          <Skeleton className="h-4 w-24 flex-shrink-0" />
          <Skeleton className="h-4 flex-1" />
        </div>
      ))}
    </div>
  );
}

/**
 * OCR 识别结果组件
 */
export function OCRResult({ response, isLoading, error }: OCRResultProps) {
  const [jsonOpen, setJsonOpen] = useState(false);

  // 加载中
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-card flex flex-col flex-1">
        <div className="px-4 py-3 border-b border-border">
          <p className="text-sm font-medium text-muted-foreground">正在识别...</p>
        </div>
        <LoadingSkeleton />
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="rounded-lg border border-border bg-card flex flex-col flex-1">
        <div className="px-4 py-3 border-b border-border">
          <p className="text-sm font-medium text-muted-foreground">识别结果</p>
        </div>
        <div className="flex flex-col items-center justify-center flex-1 gap-3 p-8 text-center">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-destructive/10">
            <AlertCircle className="h-5 w-5 text-destructive" />
          </div>
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">识别失败</p>
            <p className="text-xs text-muted-foreground max-w-xs">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  // 空状态
  if (!response) {
    return (
      <div className="rounded-lg border border-border bg-card flex items-center justify-center flex-1">
        <p className="text-sm text-muted-foreground">上传图片并点击「开始识别」查看结果</p>
      </div>
    );
  }

  const structuredData = response.data as Record<string, unknown> | undefined;

  return (
    <div className="rounded-lg border border-border bg-card">
      {/* 标题栏 */}
      <div className="px-4 py-3 border-b border-border">
        <p className="text-sm font-medium">识别结果</p>
      </div>

      <div className="p-4 space-y-4">
        {/* 结构化数据 */}
        {structuredData && Object.keys(structuredData).length > 0 ? (
          <div className="rounded-md border border-border overflow-hidden">
            <table className="w-full text-sm">
              <tbody>
                {Object.entries(structuredData).map(([key, value], i) => (
                  <tr
                    key={key}
                    className={i % 2 === 0 ? "bg-muted/30" : "bg-background"}
                  >
                    <td className="px-3 py-2 font-medium text-muted-foreground w-36 align-top whitespace-nowrap">
                      {key}
                    </td>
                    <td className="px-3 py-2 align-top break-all">
                      <RenderValue value={value} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">暂无结构化数据</p>
        )}

        {/* 原始 JSON 折叠区 */}
        <Collapsible open={jsonOpen} onOpenChange={setJsonOpen}>
          <CollapsibleTrigger className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors select-none">
            {jsonOpen ? (
              <ChevronDown className="h-3 w-3" />
            ) : (
              <ChevronRight className="h-3 w-3" />
            )}
            原始响应 JSON
          </CollapsibleTrigger>
          <CollapsibleContent>
            <pre className="mt-2 rounded-md bg-muted p-3 text-xs font-mono overflow-auto max-h-72 text-foreground">
              {JSON.stringify(response, null, 2)}
            </pre>
          </CollapsibleContent>
        </Collapsible>
      </div>
    </div>
  );
}
