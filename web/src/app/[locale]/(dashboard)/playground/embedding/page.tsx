"use client"

import { TopBar } from "@/components/layout"
import { Brain } from "lucide-react"
import { Button } from "@/components/common/button"
import { Textarea } from "@/components/common/textarea"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select"
import { Input } from "@/components/common/input"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useEffect, useMemo } from "react"
import { useSearchParams } from "next/navigation"
import { useModelList } from "../hooks/useModelList"
import { createEmbedding, EmbeddingResponse } from "@/lib/api/embedding"
import { Copy, ChevronDown, ChevronUp, Check } from "lucide-react"
import { useAuth } from "@/components/providers/auth-provider"
import { parsePriceRows } from "@/lib/utils/image"

// 最大输入行数限制
const MAX_INPUT_LINES = 100

export default function EmbeddingPlaygroundPage() {
    const { models, loading, error } = useModelList("/v1/embeddings")
    const { user } = useAuth()
    const searchParams = useSearchParams()
    const [inputText, setInputText] = useState("")
    const [selectedModel, setSelectedModel] = useState<string>("")
    const [dimensions, setDimensions] = useState<string>("")
    const [encodingFormat, setEncodingFormat] = useState<"float" | "base64">("float")
    const [generating, setGenerating] = useState(false)
    const [result, setResult] = useState<EmbeddingResponse | null>(null)
    const [showRawJson, setShowRawJson] = useState(false)
    const [expandedVectors, setExpandedVectors] = useState<Set<number>>(new Set())
    const [copiedIndex, setCopiedIndex] = useState<number | null>(null)
    const [generateError, setGenerateError] = useState<string | null>(null)

    // 初始化选中模型：URL 参数优先，否则使用第一个模型
    // 职责：统一处理模型初始化逻辑，避免多个 effect 相互触发造成 re-render
    // 优先级：URL 参数 > 默认第一个模型
    useEffect(() => {
        if (models.length === 0) return

        const modelFromUrl = searchParams.get("model")

        // 如果 URL 有指定模型，使用 URL 的值
        if (modelFromUrl) {
            setSelectedModel(modelFromUrl)
            return
        }

        // 如果没有选中模型，使用第一个模型作为默认值
        if (!selectedModel) {
            setSelectedModel(models[0].modelName)
        }
    }, [models, searchParams, selectedModel])

    // 将模型数据转换为 Combobox 需要的格式
    const modelOptions: ComboboxOption[] = useMemo(() => {
        return models.map((model) => ({
            value: model.modelName,
            label: model.modelName,
        }))
    }, [models])

    // 缓存当前选中的模型对象
    const selectedModelData = useMemo(() => {
        return models.find(m => m.modelName === selectedModel)
    }, [models, selectedModel])

    // 获取当前选中模型的 features
    const features = useMemo(() => {
        if (!selectedModelData || !selectedModelData.features) return null

        try {
            return JSON.parse(selectedModelData.features)
        } catch {
            return null
        }
    }, [selectedModelData])

    // 计算当前输入的行数
    const currentLineCount = inputText.split("\n").filter((line) => line.trim()).length

    const handleGenerate = async () => {
        if (!selectedModel || !inputText.trim()) return

        setGenerating(true)
        setGenerateError(null)
        try {
            const lines = inputText.split("\n").filter((line) => line.trim())

            // 验证输入行数
            if (lines.length > MAX_INPUT_LINES) {
                setGenerateError(`输入行数不能超过 ${MAX_INPUT_LINES} 行，当前 ${lines.length} 行`)
                setGenerating(false)
                return
            }

            const response = await createEmbedding({
                model: selectedModel,
                input: lines,
                ...(features?.encodingFormat && { encoding_format: encodingFormat }),
                ...(dimensions && features?.dimensions && { dimensions: parseInt(dimensions) }),
                ...(user?.userId && { user: user.userId }),
            })

            setResult(response)
            setExpandedVectors(new Set())
            setShowRawJson(false)
        } catch (err: any) {
            const errorData = err.response?.data
            const message = errorData?.error?.message || errorData?.message || err.message || "未知错误"
            setGenerateError(`生成向量失败：${message}`)
            setResult(null)
        } finally {
            setGenerating(false)
        }
    }

    const toggleVectorExpand = (index: number) => {
        setExpandedVectors(prev => {
            const newSet = new Set(prev)
            if (newSet.has(index)) {
                newSet.delete(index)
            } else {
                newSet.add(index)
            }
            return newSet
        })
    }

    const copyToClipboard = (text: string, index: number) => {
        navigator.clipboard.writeText(text)
            .then(() => {
                setCopiedIndex(index)
                setTimeout(() => setCopiedIndex(null), 2000) // 2秒后恢复图标
            })
            .catch(() => {
                setGenerateError('复制失败，请手动复制或检查浏览器权限')
            })
    }

    return (
        <div className="flex h-screen flex-col">
            <TopBar title="向量化 Playground" description="生成文本向量" />
            <main className="flex flex-1 overflow-hidden">
                {/* 左侧主要内容区域 */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="mx-auto max-w-6xl space-y-6">
                        <Card className="p-6">
                            <div className="mb-4 flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <Brain className="h-5 w-5 text-primary" />
                                    <h3 className="font-semibold">输入文本（每行一条）</h3>
                                </div>
                                <span className={`text-sm ${currentLineCount > MAX_INPUT_LINES ? 'text-destructive' : 'text-muted-foreground'}`}>
                                    {currentLineCount} / {MAX_INPUT_LINES} 行
                                </span>
                            </div>
                            <Textarea
                                placeholder="输入多行文本，每行将生成独立的 embedding 向量...&#10;例如：&#10;什么是人工智能？&#10;AI 的应用场景有哪些？&#10;机器学习的基本原理"
                                className="min-h-[200px] font-mono"
                                value={inputText}
                                onChange={(e) => setInputText(e.target.value)}
                            />
                            <Button
                                onClick={handleGenerate}
                                className="mt-4 w-full"
                                size="lg"
                                disabled={!inputText.trim() || !selectedModel || generating || currentLineCount > MAX_INPUT_LINES}
                            >
                                {generating ? "生成中..." : "生成 Embedding 向量"}
                            </Button>

                            {/* 错误提示 */}
                            {generateError && (
                                <div className="mt-4 rounded-lg border border-destructive bg-destructive/10 p-4">
                                    <p className="text-sm text-destructive">{generateError}</p>
                                </div>
                            )}
                        </Card>

                        {result && result.data && result.data.length > 0 && (
                            <Card className="p-6">
                                <h3 className="mb-4 font-semibold">结果</h3>
                                <div className="space-y-4">
                                    {result.data.map((item, idx) => {
                                        const inputLines = inputText.split("\n").filter((line) => line.trim())
                                        const inputLine = inputLines[item.index] || `输入 ${item.index + 1}`
                                        const isExpanded = expandedVectors.has(idx)

                                        // 处理 embedding 可能是 base64 字符串或数组
                                        const isArray = Array.isArray(item.embedding)
                                        const embeddingArray = isArray ? item.embedding as number[] : null
                                        const embeddingDisplay = embeddingArray
                                            ? (isExpanded
                                                ? `[${embeddingArray.join(", ")}]`
                                                : `[${embeddingArray.slice(0, 3).map(v => v.toFixed(6)).join(", ")}, ... (点击展开查看全部)]`)
                                            : item.embedding as string // base64 字符串直接显示

                                        return (
                                            <div key={idx} className="space-y-2">
                                                <div className="text-sm">
                                                    <span className="font-medium">输入 {item.index + 1}:</span>{" "}
                                                    <span>{inputLine}</span>
                                                </div>
                                                <div className="flex items-start gap-2">
                                                    <span className="text-sm">向量:</span>
                                                    <div className="flex-1">
                                                        {isArray ? (
                                                            // 向量展示区：折叠时点击文本展开，展开时底部显示"收起"按钮
                                                            <div className="flex flex-col gap-1">
                                                                <button
                                                                    onClick={() => toggleVectorExpand(idx)}
                                                                    className="text-sm text-primary hover:underline font-mono break-all text-left"
                                                                >
                                                                    {embeddingDisplay}
                                                                </button>
                                                                {isExpanded && (
                                                                    <button
                                                                        onClick={() => toggleVectorExpand(idx)}
                                                                        className="flex items-center gap-1 text-xs text-muted-foreground hover:text-primary w-fit"
                                                                    >
                                                                        <ChevronUp className="h-3 w-3" />
                                                                        收起
                                                                    </button>
                                                                )}
                                                            </div>
                                                        ) : (
                                                            <span className="text-sm font-mono break-all">
                                                                {embeddingDisplay}
                                                            </span>
                                                        )}
                                                    </div>
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        className="h-6 w-6 p-0"
                                                        onClick={() => copyToClipboard(JSON.stringify(item.embedding), idx)}
                                                    >
                                                        {copiedIndex === idx ? (
                                                            <Check className="h-3 w-3 text-green-600" />
                                                        ) : (
                                                            <Copy className="h-3 w-3" />
                                                        )}
                                                    </Button>
                                                </div>
                                                {idx < result.data.length - 1 && <div className="border-t" />}
                                            </div>
                                        )
                                    })}
                                </div>

                                {/* 原始 JSON 响应 */}
                                <div className="mt-6 border-t pt-4">
                                    <button
                                        onClick={() => setShowRawJson(!showRawJson)}
                                        className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
                                    >
                                        {showRawJson ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                                        <span>原始JSON响应</span>
                                    </button>
                                    {showRawJson && (
                                        <div className="mt-3 relative">
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="absolute right-2 top-2 h-6 w-6 p-0"
                                                onClick={() => copyToClipboard(JSON.stringify(result, null, 2), -1)}
                                            >
                                                {copiedIndex === -1 ? (
                                                    <Check className="h-3 w-3 text-green-600" />
                                                ) : (
                                                    <Copy className="h-3 w-3" />
                                                )}
                                            </Button>
                                            <pre className="rounded-lg bg-muted p-4 text-xs overflow-auto max-h-96">
                                                {JSON.stringify(result, null, 2)}
                                            </pre>
                                        </div>
                                    )}
                                </div>
                            </Card>
                        )}
                    </div>
                </div>

                {/* 右侧配置面板 */}
                <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
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

                                    {/* 状态提示 */}
                                    {loading && (
                                        <p className="mt-2 text-xs text-muted-foreground">正在获取模型列表...</p>
                                    )}
                                    {error && (
                                        <p className="mt-2 text-xs text-destructive">{error}</p>
                                    )}
                                    {!loading && !error && models.length === 0 && (
                                        <p className="mt-2 text-xs text-muted-foreground">当前没有可用的 embedding 模型</p>
                                    )}
                                </div>

                                {features?.encodingFormat === true && (
                                    <div>
                                        <Label className="mb-2 block">编码格式</Label>
                                        <Select value={encodingFormat} onValueChange={(value) => setEncodingFormat(value as "float" | "base64")}>
                                            <SelectTrigger>
                                                <SelectValue />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="float">Float</SelectItem>
                                                <SelectItem value="base64">Base64</SelectItem>
                                            </SelectContent>
                                        </Select>
                                    </div>
                                )}

                                {features?.dimensions === true && (
                                    <div>
                                        <Label className="mb-2 block">维度</Label>
                                        <Input
                                            type="number"
                                            placeholder="例如: 1536"
                                            value={dimensions}
                                            onChange={(e) => setDimensions(e.target.value)}
                                            min="1"
                                        />
                                        <p className="mt-2 text-xs text-muted-foreground">降低维度可以减少存储成本，不填写则使用模型默认维度</p>
                                    </div>
                                )}
                            </div>
                        </div>

                        {selectedModelData && (
                            <div className="border-t pt-6">
                                <h4 className="mb-3 text-sm font-medium">价格信息</h4>
                                <div className="space-y-2 text-xs text-muted-foreground">
                                    {selectedModelData.priceDetails?.displayPrice && (
                                        <div className="flex justify-between">
                                            <div className="font-medium text-right">
                                                {parsePriceRows(
                                                    selectedModelData.priceDetails.displayPrice,
                                                    selectedModelData.priceDetails.unit
                                                ).map(({ label, lines }) => (
                                                    <div key={label}>{label}: {lines.join(' ')}</div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    )
}
