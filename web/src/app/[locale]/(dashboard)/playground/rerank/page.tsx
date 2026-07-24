"use client"

import { TopBar } from "@/components/layout"
import { Badge } from "@/components/common/badge"
import { Button } from "@/components/common/button"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Textarea } from "@/components/common/textarea"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useAuth } from "@/components/providers/auth-provider"
import { rerank, RerankResponse } from "@/lib/api/rerank"
import { parsePriceRows } from "@/lib/utils/image"
import { BarChart3, Check, ChevronDown, ChevronUp, Copy, ListFilter } from "lucide-react"
import { useSearchParams } from "next/navigation"
import { useEffect, useMemo, useState } from "react"
import { useModelList } from "../hooks/useModelList"

const DEFAULT_MAX_DOCUMENTS = 100

function getMaxDocuments(properties?: string): number {
  if (!properties) {
    return DEFAULT_MAX_DOCUMENTS
  }

  try {
    const parsed = JSON.parse(properties)
    const maxDocuments = parsed?.max_documents
    return typeof maxDocuments === "number" && maxDocuments > 0
      ? maxDocuments
      : DEFAULT_MAX_DOCUMENTS
  } catch {
    return DEFAULT_MAX_DOCUMENTS
  }
}

function getResultDocumentText(document: unknown): string | undefined {
  if (typeof document === "string") {
    return document
  }
  if (document && typeof document === "object" && "text" in document) {
    const text = (document as { text?: unknown }).text
    return typeof text === "string" ? text : undefined
  }
  return undefined
}

function getFiniteNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined
}

function getResultIndex(index: unknown): number | undefined {
  const value = getFiniteNumber(index)
  return value !== undefined && value >= 0 ? Math.floor(value) : undefined
}

function formatResultScore(score: unknown): string {
  const value = getFiniteNumber(score)
  if (value !== undefined) {
    return value.toFixed(6)
  }
  return score == null || score === "" ? "暂无评分" : String(score)
}

export default function RerankPlaygroundPage() {
  const { models, loading, error } = useModelList("/v1/reranks")
  const { user } = useAuth()
  const searchParams = useSearchParams()
  const [selectedModel, setSelectedModel] = useState("")
  const [query, setQuery] = useState("")
  const [documentsText, setDocumentsText] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<RerankResponse | null>(null)
  const [showRawJson, setShowRawJson] = useState(false)
  const [copied, setCopied] = useState(false)
  const [requestError, setRequestError] = useState<string | null>(null)

  const documents = useMemo(
    () => documentsText.split("\n").map((line) => line.trim()).filter(Boolean),
    [documentsText]
  )

  useEffect(() => {
    if (models.length === 0) return
    const modelFromUrl = searchParams.get("model")
    if (modelFromUrl) {
      setSelectedModel(modelFromUrl)
      return
    }
    if (!selectedModel) {
      setSelectedModel(models[0].modelName)
    }
  }, [models, searchParams, selectedModel])

  const modelOptions: ComboboxOption[] = useMemo(
    () => models.map((model) => ({ value: model.modelName, label: model.modelName })),
    [models]
  )

  const selectedModelData = useMemo(
    () => models.find((model) => model.modelName === selectedModel),
    [models, selectedModel]
  )

  const maxDocuments = useMemo(
    () => getMaxDocuments(selectedModelData?.properties),
    [selectedModelData]
  )

  const sortedResults = useMemo(
    () => [...(result?.results || [])].sort((left, right) => {
      const leftScore = getFiniteNumber(left.relevance_score)
      const rightScore = getFiniteNumber(right.relevance_score)
      if (leftScore === undefined && rightScore === undefined) return 0
      if (leftScore === undefined) return 1
      if (rightScore === undefined) return -1
      return rightScore - leftScore
    }),
    [result]
  )

  const handleSubmit = async () => {
    if (!selectedModel || !query.trim() || documents.length === 0) return
    if (documents.length > maxDocuments) {
      setRequestError(`文档数量不能超过 ${maxDocuments} 条，当前 ${documents.length} 条`)
      return
    }

    setSubmitting(true)
    setRequestError(null)
    try {
      const response = await rerank({
        model: selectedModel,
        query: query.trim(),
        documents,
        ...(user?.userId ? { user: String(user.userId) } : {}),
      })
      setResult(response)
      setShowRawJson(false)
    } catch (err: any) {
      const errorData = err.response?.data
      const message = errorData?.error?.message || errorData?.message || err.message || "未知错误"
      setRequestError(`重排序失败：${message}`)
      setResult(null)
    } finally {
      setSubmitting(false)
    }
  }

  const copyJson = () => {
    if (!result) return
    navigator.clipboard.writeText(JSON.stringify(result, null, 2))
      .then(() => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      })
      .catch(() => setRequestError("复制失败，请手动复制或检查浏览器权限"))
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="重排序 Playground" description="对候选文档进行相关性重排序" />
      <main className="flex flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-6xl space-y-6">
            <Card className="p-6">
              <div className="mb-4 flex items-center justify-between gap-4">
                <div className="flex items-center gap-2">
                  <ListFilter className="h-5 w-5 text-primary" />
                  <h3 className="font-semibold">重排序输入</h3>
                </div>
                <span className={`text-sm ${documents.length > maxDocuments ? "text-destructive" : "text-muted-foreground"}`}>
                  {documents.length} / {maxDocuments} 条文档
                </span>
              </div>

              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block">查询文本</Label>
                  <Textarea
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="输入需要匹配的 query，例如：适合家庭入住的酒店"
                    className="min-h-[96px]"
                  />
                </div>
                <div>
                  <Label className="mb-2 block">候选文档（每行一条）</Label>
                  <Textarea
                    value={documentsText}
                    onChange={(event) => setDocumentsText(event.target.value)}
                    placeholder={"湖景亲子房，含早餐和儿童乐园\n商务大床房，靠近会议中心\n青年旅社床位，公共卫浴"}
                    className="min-h-[220px] font-mono"
                  />
                </div>
                <Button
                  onClick={handleSubmit}
                  className="w-full"
                  size="lg"
                  disabled={!selectedModel || !query.trim() || documents.length === 0 || documents.length > maxDocuments || submitting}
                >
                  {submitting ? "重排序中..." : "执行重排序"}
                </Button>
              </div>

              {requestError && (
                <div className="mt-4 rounded-lg border border-destructive bg-destructive/10 p-4">
                  <p className="text-sm text-destructive">{requestError}</p>
                </div>
              )}
            </Card>

            {result && (
              <Card className="p-6">
                <div className="mb-4 flex items-center justify-between gap-4">
                  <h3 className="font-semibold">结果</h3>
                  {result.usage?.total_tokens != null && (
                    <Badge variant="secondary">{result.usage.total_tokens} tokens</Badge>
                  )}
                </div>
                {sortedResults.length > 0 ? (
                  <div className="space-y-3">
                    {sortedResults.map((item, rank) => {
                      const documentIndex = getResultIndex(item.index)
                      const documentLabel = documentIndex === undefined ? "文档 -" : `文档 ${documentIndex + 1}`
                      const documentText = getResultDocumentText(item.document)
                        || (documentIndex === undefined ? undefined : documents[documentIndex])
                        || documentLabel
                      return (
                        <div key={`${documentIndex ?? "unknown"}-${rank}`} className="rounded-md border p-4">
                          <div className="mb-2 flex items-center justify-between gap-3">
                            <div className="flex items-center gap-2">
                              <Badge variant="outline">#{rank + 1}</Badge>
                              <span className="text-sm text-muted-foreground">{documentLabel}</span>
                            </div>
                            <div className="flex items-center gap-2 text-sm font-medium">
                              <BarChart3 className="h-4 w-4 text-primary" />
                              {formatResultScore(item.relevance_score)}
                            </div>
                          </div>
                          <p className="whitespace-pre-wrap text-sm">
                            {documentText}
                          </p>
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">响应中没有返回重排序结果。</p>
                )}

                <div className="mt-6 border-t pt-4">
                  <div className="flex items-center justify-between gap-3">
                    <button
                      onClick={() => setShowRawJson(!showRawJson)}
                      className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
                    >
                      {showRawJson ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                      <span>原始JSON响应</span>
                    </button>
                    <Button variant="ghost" size="sm" className="h-8 px-2" onClick={copyJson}>
                      {copied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
                    </Button>
                  </div>
                  {showRawJson && (
                    <pre className="mt-3 max-h-96 overflow-auto rounded-lg bg-muted p-4 text-xs">
                      {JSON.stringify(result, null, 2)}
                    </pre>
                  )}
                </div>
              </Card>
            )}
          </div>
        </div>

        <div className="w-80 overflow-auto border-l bg-muted/30 p-6">
          <div className="space-y-6">
            <div>
              <h3 className="mb-4 font-semibold">模型配置</h3>
              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block">模型选择</Label>
                  <Combobox
                    options={modelOptions}
                    value={selectedModel}
                    onValueChange={setSelectedModel}
                    placeholder={loading ? "加载中..." : error ? "加载失败" : models.length === 0 ? "暂无可用模型" : "选择模型"}
                    searchPlaceholder="搜索模型..."
                    emptyText="未找到匹配的模型"
                    disabled={loading || !!error || models.length === 0}
                    className="w-full"
                  />
                  {loading && <p className="mt-2 text-xs text-muted-foreground">正在获取模型列表...</p>}
                  {error && <p className="mt-2 text-xs text-destructive">{error}</p>}
                  {!loading && !error && models.length === 0 && (
                    <p className="mt-2 text-xs text-muted-foreground">当前没有可用的重排序模型</p>
                  )}
                </div>
              </div>
            </div>

            {selectedModelData?.priceDetails?.displayPrice && (
              <div className="border-t pt-6">
                <h4 className="mb-3 text-sm font-medium">价格信息</h4>
                <div className="space-y-2 text-xs text-muted-foreground">
                  {parsePriceRows(
                    selectedModelData.priceDetails.displayPrice,
                    selectedModelData.priceDetails.unit
                  ).map(({ label, lines }) => (
                    <div key={label} className="font-medium">
                      {label}: {lines.join(" ")}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}
