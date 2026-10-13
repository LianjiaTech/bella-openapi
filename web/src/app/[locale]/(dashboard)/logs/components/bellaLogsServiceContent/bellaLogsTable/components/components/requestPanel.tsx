"use client"

import { useState } from "react"
import { Badge } from "@/components/common/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/common/collapsible"
import { ScrollArea } from "@/components/common/scroll-area"
import { ChevronDown, FileText, User, Cpu, Binary } from "lucide-react"
import { safeParseJSON } from "../../utils"
import { LogRequestData } from "@/lib/types/logs"


function InputChunk({
  text,
  index,
  total,
}: {
  text: string
  index: number
  total: number
}) {
  const [isOpen, setIsOpen] = useState(index === 0)

  // Extract the document part name from the text
  const partMatch = text.match(/第([一二三四五六七八九十]+)部分/)
  const partLabel = partMatch ? `${partMatch[0]}` : `分块 ${index + 1}`

  const summaryMatch = text.match(/[，,](.{0,30})/)
  const summary = summaryMatch
    ? summaryMatch[1].slice(0, 15) + "..."
    : text.slice(0, 10) + "..."

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <button className="w-full flex items-center gap-3 p-3 rounded-lg bg-secondary/50 hover:bg-secondary transition-colors text-left group cursor-pointer">
          <div className="flex items-center justify-center h-6 w-6 rounded bg-primary/10 text-primary text-xs font-mono shrink-0">
            {index + 1}
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-foreground whitespace-nowrap">
                {partLabel}
              </span>
              <span className="text-xs text-muted-foreground truncate">
                {summary}
              </span>
            </div>
          </div>
          <Badge variant="outline" className="text-xs shrink-0 border-border text-muted-foreground">
            {text.length} 字符
          </Badge>
          <ChevronDown
            className={`h-4 w-4 text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`}
          />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-1 ml-9 mr-2 p-3 rounded-lg bg-background border border-border">
          <p className="text-sm text-foreground/90 leading-relaxed whitespace-pre-wrap">
            {text}
          </p>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

export function RequestPanel({ requestData }: { requestData: string }) {
  const request = safeParseJSON<LogRequestData>(requestData)
  console.log(request, '----')

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
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <User className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">User</p>
              <p className="text-sm text-foreground font-mono truncate">
                {request?.user}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <Cpu className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">Model</p>
              <p className="text-sm text-foreground font-mono truncate">
                {request?.model}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <Binary className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">编码格式</p>
              <p className="text-sm text-foreground font-mono">
                {request?.encoding_format}
              </p>
            </div>
          </div>
        </div>

        {/* Input chunks */}
        <div className="flex items-center gap-2 mb-3">
          <FileText className="h-4 w-4 text-primary" />
          <h3 className="text-sm font-medium text-foreground">
            输入文本
          </h3>
          <Badge variant="secondary" className="text-xs bg-secondary text-secondary-foreground">
            {request?.input?.length || 0} 个分块
          </Badge>
        </div>
        {
          !request?.input?.length && <div className="flex-1 min-h-[120px] rounded-lg border border-dashed border-border/80 bg-muted/30 flex items-center justify-center">
          <div className="text-center">
            <div className="mx-auto mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-primary/5">
              <FileText className="h-5 w-5 text-primary/40" />
            </div>
            <p className="text-sm text-muted-foreground">暂无输入文本</p>
            <p className="text-xs text-muted-foreground/60 mt-0.5">请输入需要嵌入的文本内容</p>
          </div>
        </div>
        }
        <ScrollArea className="pr-3">
          <div className="flex flex-col gap-2">
            {request?.input?.map((text, index) => (
              <InputChunk
                key={index}
                text={text}
                index={index}
                total={request.input?.length ?? 0}
              />
            ))}
          </div>
        </ScrollArea>
      </CardContent>
    </Card>
  )
}
