"use client"

import { useState, useMemo, useEffect } from "react"
import { useSearchParams } from "next/navigation"
import { TopBar } from "@/components/top-bar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Search,
  Layers,
  Loader
} from "lucide-react"
import { useLanguage } from "@/components/language-provider"
import { useSidebar } from "@/lib/context/sidebar-context"
import { CategoryTree, EndpointDetails, Model } from "@/lib/types/openapi"
import { getInitialEndpoint } from "@/lib/utils/endpoint-selection"
import { getEndpointDetails } from "@/lib/api/meta"
import { ModelCard } from "./components/model-card"

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

const tagColors = [
  "bg-green-500/10 text-green-500 border-green-500/20",
  "bg-blue-500/10 text-blue-500 border-blue-500/20",
  "bg-purple-500/10 text-purple-500 border-purple-500/20",
  "bg-orange-500/10 text-orange-500 border-orange-500/20",
  "bg-pink-500/10 text-pink-500 border-pink-500/20",
  "bg-indigo-500/10 text-indigo-500 border-indigo-500/20",
  "bg-cyan-500/10 text-cyan-500 border-cyan-500/20",
  "bg-red-500/10 text-red-500 border-red-500/20",
]

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
      // const data = DATA_MOCK 

      // 检查并处理数据
      if (data && data.models) {
        // 对每个模型进行处理
        data.models = data.models.map(model => {
          const processedModel = { ...model }

          // 如果存在 features 字段且为字符串，进行 JSON.parse
          if (processedModel.features && typeof processedModel.features === 'string') {
            try {
              const parsedFeatures = JSON.parse(processedModel.features)
              // 提取值为 true 的键组成字符串数组
              processedModel.features = Object.keys(parsedFeatures).filter(
                key => parsedFeatures[key] === true
              ).join(',');
            } catch (e) {
              console.error('Failed to parse features:', e)
            }
          }

          // 如果存在 properties 字段且为字符串，进行 JSON.parse
          if (processedModel.properties && typeof processedModel.properties === 'string') {
            try {
              const parsedProps = JSON.parse(processedModel.properties)
              // 对 max_input_context 和 max_output_context 应用千分位分隔
              if (parsedProps.max_input_context !== undefined && typeof parsedProps.max_input_context === "number") {
                parsedProps.max_input_context = parsedProps.max_input_context.toLocaleString()
              }
              if (parsedProps.max_output_context !== undefined && typeof parsedProps.max_output_context === "number") {
                parsedProps.max_output_context = parsedProps.max_output_context.toLocaleString()
              }
              processedModel.properties = parsedProps
            } catch (e) {
              console.error('Failed to parse properties:', e)
            }
          }

          return processedModel
        })
      }

      console.log('data1', data)
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
                      className={`flex items-center gap-2 whitespace-nowrap ${isSelected ? "bg-primary text-primary-foreground" : ""
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
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder={t("searchModels")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 text-sm"
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
                      className={`cursor-pointer transition-colors`}
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
                {t("foundModels")} {endpointData?.models.length} {t("modelsCount")}
              </h2>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {endpointData?.models.map((model) => (
                <ModelCard
                  key={model.modelName}
                  model={model}
                  tagColors={tagColors}
                  onAddChannel={(model: Model) => {
                    console.log("添加私有渠道:", model.modelName)
                  }}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
