"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Badge } from "@/components/common/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/common/collapsible"
import { ScrollArea } from "@/components/common/scroll-area"
import { Progress } from "@/components/common/progress"
import {
  ChevronDown,
  Database,
  Cpu,
  Coins,
  Copy,
  Check,
} from "lucide-react"
import { LogResponseData } from "@/lib/types/logs"
import { safeParseJSON } from "../../utils"

interface EmbeddingData {
  object: string
  embedding: string
  index: number
}


function EmbeddingItem({
  item,
  total,
}: {
  item: EmbeddingData
  total: number
}) {
  const t = useTranslations("logs.responsePanel")
  const [isOpen, setIsOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const truncatedEmbedding = item.embedding.slice(0, 30) + "..."

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation()
    await navigator.clipboard.writeText(item.embedding)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <button className="w-full flex items-center gap-3 p-3 rounded-lg bg-secondary/50 hover:bg-secondary transition-colors text-left group cursor-pointer">
          <div className="flex items-center justify-center h-6 w-6 rounded bg-primary/10 text-primary text-xs font-mono shrink-0">
            {item.index}
          </div>
          <div className="flex-1 min-w-0 truncate break-all">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-foreground">
                {'Embedding #'}{item.index}
              </span>
              <span className="text-xs text-muted-foreground">
                {item.object}
              </span>
            </div>
            {!isOpen && (
              <p className="w-full truncate breal-all text-xs text-muted-foreground font-mono mt-0.5">
                {truncatedEmbedding}
              </p>
            )}
          </div>
          <Badge variant="outline" className="text-xs shrink-0 border-border text-muted-foreground">
            {item.embedding.length} {t("characters")}
          </Badge>
          <ChevronDown
            className={`h-4 w-4 text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`}
          />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-1 ml-9 mr-2 p-3 rounded-lg bg-background border border-border relative group/code">
          <button
            onClick={handleCopy}
            className="absolute top-2 right-2 p-1.5 rounded-md bg-secondary hover:bg-secondary/80 transition-colors cursor-pointer"
            title={t("copyEmbedding")}
          >
            {copied ? (
              <Check className="h-3.5 w-3.5 text-primary" />
            ) : (
              <Copy className="h-3.5 w-3.5 text-muted-foreground" />
            )}
          </button>
          <pre className="text-xs text-foreground/80 font-mono whitespace-pre-wrap break-all leading-relaxed pr-8 break-words">
            {item.embedding}
          </pre>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

export function ResponsePanel({ responseData }: { responseData: string }) {
  const t = useTranslations("logs.responsePanel")
  const response = safeParseJSON<LogResponseData>(responseData)
  const usagePercent =
    (response?.usage?.total_tokens && response?.usage?.total_tokens > 0 && response?.usage?.prompt_tokens != null)
      ? (response.usage.prompt_tokens / response.usage.total_tokens) * 100
      : 0

  return (
    <Card className="border-border bg-card shadow-[0_1px_4px_0_rgb(0_0_0/0.06)]">
      <CardHeader className="pb-4">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-medium text-foreground">
            {t("response")}
          </CardTitle>
          <Badge
            variant="outline"
            className="border-primary/30 bg-primary/10 text-primary"
          >
            {response?.object}
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        {/* Response metadata */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <Cpu className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">{t("model")}</p>
              <p className="text-sm text-foreground font-mono truncate">
                {response?.model}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <Coins className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">Prompt Tokens</p>
              <p className="text-sm text-foreground font-mono">
                {response?.usage?.prompt_tokens?.toLocaleString()}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 rounded-lg bg-secondary/50 px-3 py-2.5">
            <Coins className="h-4 w-4 text-primary shrink-0" />
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">Total Tokens</p>
              <p className="text-sm text-foreground font-mono">
                {response?.usage?.total_tokens?.toLocaleString()}
              </p>
            </div>
          </div>
        </div>

        <div className="mb-5 p-3 rounded-lg bg-secondary/50">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-muted-foreground">
              {t("tokenUsageDistribution")}
            </span>
            <span className="text-xs text-muted-foreground font-mono">
              {response?.usage?.prompt_tokens} / {response?.usage?.total_tokens}
            </span>
          </div>
          <Progress value={usagePercent} className="h-2" />
          <div className="flex items-center justify-between mt-1.5">
            <span className="text-xs text-muted-foreground">Prompt Tokens</span>
            <span className="text-xs text-primary font-mono">
              {usagePercent.toFixed(1)}%
            </span>
          </div>
        </div>

        {/* Embedding data */}
        <div className="flex items-center gap-2 mb-3">
          <Database className="h-4 w-4 text-primary" />
          <h3 className="text-sm font-medium text-foreground">
            {t("embeddingData")}
          </h3>
          <Badge variant="secondary" className="text-xs bg-secondary text-secondary-foreground">
            {response?.data?.length} {t("embeddingsCount")}
          </Badge>
        </div>
        {
          !response?.data?.length &&  <div className="flex-1 min-h-[60px] rounded-lg border border-dashed border-border/80 bg-muted/30 flex items-center justify-center">
          <div className="text-center py-3">
            <p className="text-sm text-muted-foreground">暂无向量数据</p>
            <p className="text-xs text-muted-foreground/60 mt-0.5">发送请求后将显示向量结果</p>
          </div>
        </div>
        }
        <ScrollArea className="pr-3">
          <div className="flex flex-col gap-2">
            {response?.data?.map((item) => (
              <EmbeddingItem
                key={item.index}
                item={item}
                total={response.data.length}
              />
            ))}
          </div>
        </ScrollArea>
      </CardContent>
    </Card>
  )
}
