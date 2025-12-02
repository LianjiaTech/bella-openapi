"use client"

import { useState, useMemo, useEffect } from "react"
import { useSearchParams } from "next/navigation"
import { TopBar } from "@/components/top-bar"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  MessageSquare,
  ImageIcon,
  Mic,
  Search,
  ExternalLink,
  Plus,
  Building,
  Layers,
  Waves,
  Loader
} from "lucide-react"
import Link from "next/link"
import { useLanguage } from "@/components/language-provider"
import { useSidebar } from "@/lib/context/sidebar-context"
import { CategoryTree, EndpointDetails } from "@/lib/types/openapi"
import { getInitialEndpoint } from "@/lib/utils/endpoint-selection"
import { getEndpointDetails } from "@/lib/api/meta"

type CapabilityType = "chat" | "image" | "audio" | "embedding" | "realtime" | "tts" | "ocr"

// 扁平化 CategoryTree 结构，按 endpoint 组织数据
interface EndpointWithCategory {
  categoryCode: string
  categoryName: string
  endpoint: string
  endpointCode: string
  endpointName: string
  ctime: string
  cuName: string
  mtime: string
  muName: string
  status: string
}

function flattenCategoryTrees(trees: CategoryTree[]): EndpointWithCategory[] {
  const result: EndpointWithCategory[] = []

  function traverse(tree: CategoryTree) {
    // 处理当前节点的 endpoints
    if (tree.endpoints && tree.endpoints.length > 0) {
      tree.endpoints.forEach((endpoint) => {
        result.push({
          categoryCode: tree.categoryCode,
          categoryName: tree.categoryName,
          endpoint: endpoint.endpoint,
          endpointCode: endpoint.endpointCode,
          endpointName: endpoint.endpointName,
          ctime: endpoint.ctime,
          cuName: endpoint.cuName,
          mtime: endpoint.mtime,
          muName: endpoint.muName,
          status: endpoint.status,
        })
      })
    }

    // 递归处理子节点
    if (tree.children && tree.children.length > 0) {
      tree.children.forEach((child) => traverse(child))
    }
  }

  trees.forEach((tree) => traverse(tree))
  return result
}


const allModels = [
  {
    id: "gpt-5-pro",
    name: "GPT-5 Pro",
    provider: "OpenAI",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "超长上下文", "深度思考", "流式"],
    inputTokens: 128000,
    outputTokens: 16000,
    features: ["函数调用", "JSON模式", "视觉理解"],
    pricing: { input: "$0.03/1K", output: "$0.06/1K" },
    description: "最先进的推理和代码生成能力",
  },
  {
    id: "gpt-5-mini",
    name: "GPT-5 Mini",
    provider: "OpenAI",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "流式"],
    inputTokens: 16000,
    outputTokens: 4000,
    features: ["快速响应", "低成本"],
    pricing: { input: "$0.001/1K", output: "$0.002/1K" },
    description: "快速且经济实惠的模型",
  },
  {
    id: "claude-sonnet-4",
    name: "Claude Sonnet 4",
    provider: "Anthropic",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "超长上下文", "流式"],
    inputTokens: 200000,
    outputTokens: 8000,
    features: ["长文本理解", "多语言", "代码生成"],
    pricing: { input: "$0.015/1K", output: "$0.03/1K" },
    description: "平衡性能与成本的Claude模型",
  },
  {
    id: "qwen-plus",
    name: "通义千问 Plus",
    provider: "阿里云",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国内", "超长上下文", "流式"],
    inputTokens: 32000,
    outputTokens: 8000,
    features: ["中文优化", "多模态"],
    pricing: { input: "¥0.004/1K", output: "$0.012/1K" },
    description: "阿里云自研大语言模型",
  },
  {
    id: "my-custom-model",
    name: "Custom Model",
    provider: "内部",
    type: "chat" as CapabilityType,
    icon: Building,
    tags: ["内部", "私有渠道", "流式"],
    inputTokens: 8000,
    outputTokens: 4000,
    features: ["定制化", "私有部署"],
    pricing: { input: "自定义", output: "自定义" },
    description: "企业内部定制模型",
  },
  {
    id: "dall-e-3",
    name: "DALL-E 3",
    provider: "OpenAI",
    type: "image" as CapabilityType,
    icon: ImageIcon,
    tags: ["国外", "文生图"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["高质量图像", "1024x1024"],
    pricing: { input: "$0.04/image", output: "-" },
    description: "最先进的文本生成图像模型",
  },
  {
    id: "stable-diffusion-xl",
    name: "Stable Diffusion XL",
    provider: "Stability AI",
    type: "image" as CapabilityType,
    icon: ImageIcon,
    tags: ["国外", "文生图", "图生图"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["开源", "高分辨率"],
    pricing: { input: "$0.02/image", output: "-" },
    description: "开源的图像生成模型",
  },
  {
    id: "whisper-large",
    name: "Whisper Large",
    provider: "OpenAI",
    type: "audio" as CapabilityType,
    icon: Mic,
    tags: ["国外", "语音识别"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["多语言", "高准确率"],
    pricing: { input: "$0.006/min", output: "-" },
    description: "高精度语音识别模型",
  },
  {
    id: "text-embedding-3-large",
    name: "Text Embedding 3 Large",
    provider: "OpenAI",
    type: "embedding" as CapabilityType,
    icon: Layers,
    tags: ["国外", "高维度"],
    inputTokens: 8192,
    outputTokens: 0,
    features: ["3072维", "语义搜索"],
    pricing: { input: "$0.00013/1K", output: "-" },
    description: "高性能文本嵌入模型",
  },
  {
    id: "gpt-4o-realtime",
    name: "GPT-4o Realtime",
    provider: "OpenAI",
    type: "realtime" as CapabilityType,
    icon: Waves,
    tags: ["国外", "实时对话", "流式"],
    inputTokens: 128000,
    outputTokens: 16000,
    features: ["低延迟", "语音输入输出"],
    pricing: { input: "$0.06/1K", output: "$0.12/1K" },
    description: "实时语音对话模型",
  },
]

const tagColors: Record<string, string> = {
  国内: "bg-green-500/10 text-green-500 border-green-500/20",
  国外: "bg-blue-500/10 text-blue-500 border-blue-500/20",
  内部: "bg-purple-500/10 text-purple-500 border-purple-500/20",
  超长上下文: "bg-orange-500/10 text-orange-500 border-orange-500/20",
  超长输出: "bg-pink-500/10 text-pink-500 border-pink-500/20",
  深度思考: "bg-indigo-500/10 text-indigo-500 border-indigo-500/20",
  流式: "bg-cyan-500/10 text-cyan-500 border-cyan-500/20",
  私有渠道: "bg-red-500/10 text-red-500 border-red-500/20",
}

export default function ModelsPage() {
  const searchParams = useSearchParams()
  const [selectedCapability, setSelectedCapability] = useState<string>("")
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const [endpointData, setEndpointData] = useState<EndpointDetails | null>(null)
  const [loading, setLoading] = useState(false)
  const { t } = useLanguage()
  const { categoryTrees } = useSidebar()

  // 使用 useMemo 缓存扁平化后的数据
  const flattenedEndpoints = useMemo(() => {
    return flattenCategoryTrees(categoryTrees)
  }, [categoryTrees])

  // 获取 endpoint 详情数据
  async function fetchEndpointDetails(endpoint: string) {
    if (!endpoint) return

    try {
      setLoading(true)
      const data = await getEndpointDetails(endpoint, "", [])
      setEndpointData(data)
    } catch (error) {
      console.error('Error fetching endpoint details:', error)
      setEndpointData(null)
    } finally {
      setLoading(false)
    }
  }

  // 初始化选中的 endpoint
  useEffect(() => {
    const endpoint = getInitialEndpoint(searchParams.get("endpoint"))
    setSelectedCapability(endpoint)
  }, [searchParams])

  // 当 selectedCapability 变化时，获取数据
  useEffect(() => {
    if (selectedCapability) {
      fetchEndpointDetails(selectedCapability)
    }
  }, [selectedCapability])

  const toggleTag = (tag: string) => {
    setSelectedTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]))
  }

  const filteredModels = allModels.filter((model) => {
    const matchesCapability = model.type === selectedCapability

    const matchesSearch =
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.description.toLowerCase().includes(searchQuery.toLowerCase())

    const matchesTags = selectedTags.length === 0 || selectedTags.some((tag) => model.tags.includes(tag))

    return matchesCapability && matchesSearch && matchesTags
  })

  return (
    <>
      <TopBar title={t("modelCatalog")} description={t("modelCatalogDesc")} />

      <div className="flex h-[calc(100vh-4rem)] flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto">
          <div className="container px-6 py-8">
            <div className="mb-6">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("capabilityCategory")}</span>
              </div>
              <div className="flex gap-2 overflow-x-auto pb-2">
                {flattenedEndpoints.map((capability: EndpointWithCategory) => {
                  const isSelected = selectedCapability === capability.endpoint
                  return (
                    <Button
                      key={capability.endpoint}
                      variant={isSelected ? "default" : "outline"}
                      size="sm"
                      onClick={() => setSelectedCapability(capability.endpoint)}
                      className={`flex items-center gap-2 whitespace-nowrap ${
                        isSelected ? "bg-primary text-primary-foreground" : ""
                      }`}
                    >
                      {capability.endpointName}
                    </Button>
                  )
                })}
              </div>
            </div>

            {/* Search */}
            <div className="mb-6">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder={t("searchModels")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 text-base"
                />
              </div>
            </div>

            {/* Tags */}
            <div className="mb-8">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("quickFilter")}</span>
              </div>
              {loading ? (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Loader className="h-4 w-4 animate-spin" />
                  <span className="text-sm">加载中...</span>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {endpointData?.features.map((feature) => (
                    <Badge
                      key={feature.code}
                      variant={selectedTags.includes(feature.code) ? "default" : "outline"}
                      className={`cursor-pointer transition-colors ${selectedTags.includes(feature.code) ? tagColors[feature.name] || "" : ""}`}
                      onClick={() => toggleTag(feature.code)}
                    >
                      {feature.name}
                    </Badge>
                  ))}
                  {(!endpointData?.features || endpointData.features.length === 0) && (
                    <span className="text-sm text-muted-foreground">暂无可用筛选标签</span>
                  )}
                </div>
              )}
            </div>

            {/* Model Grid */}
            <div className="mb-4">
              <h2 className="text-sm font-medium text-muted-foreground">
                {t("foundModels")} {filteredModels.length} {t("modelsCount")}
              </h2>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {filteredModels.map((model) => {
                const Icon = model.icon
                return (
                  <Card
                    key={model.id}
                    className="border-border/50 transition-all hover:border-primary/50 hover:shadow-md"
                  >
                    <CardContent className="p-5">
                      <div className="mb-4 flex items-start justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                            <Icon className="h-5 w-5 text-primary" />
                          </div>
                          <div>
                            <h3 className="font-semibold">{model.name}</h3>
                            <p className="text-xs text-muted-foreground">{model.provider}</p>
                          </div>
                        </div>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-8 w-8 p-0 hover:bg-primary/10"
                          title="添加私有渠道"
                        >
                          <Plus className="h-4 w-4" />
                        </Button>
                      </div>

                      <p className="mb-3 text-sm text-muted-foreground">{model.description}</p>

                      {/* Tags */}
                      <div className="mb-3 flex flex-wrap gap-1">
                        {model.tags.slice(0, 3).map((tag) => (
                          <Badge key={tag} variant="outline" className={`text-xs ${tagColors[tag]}`}>
                            {tag}
                          </Badge>
                        ))}
                      </div>

                      <div className="mb-3 min-h-[40px] space-y-1 text-xs">
                        {model.inputTokens > 0 && (
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">输入/输出长度:</span>
                            <span className="font-medium">
                              {model.inputTokens.toLocaleString()} / {model.outputTokens.toLocaleString()}
                            </span>
                          </div>
                        )}
                        <div className="flex justify-between">
                          <span className="text-muted-foreground">定价:</span>
                          <span className="font-medium">
                            {model.pricing.input} / {model.pricing.output}
                          </span>
                        </div>
                      </div>

                      {/* Features */}
                      <div className="mb-4 flex flex-wrap gap-1">
                        {model.features.map((feature) => (
                          <Badge key={feature} variant="secondary" className="text-xs">
                            {feature}
                          </Badge>
                        ))}
                      </div>

                      {/* Actions */}
                      <div className="flex gap-2">
                        <Button size="sm" className="flex-1" asChild>
                          <Link href={`/playground/chat?model=${model.id}`}>试用</Link>
                        </Button>
                        <Button size="sm" variant="outline" asChild>
                          <Link href={`/docs/api/${model.id}`}>
                            <ExternalLink className="h-4 w-4" />
                          </Link>
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
