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
  Loader,
  X
} from "lucide-react"
import { useLanguage } from "@/components/language-provider"
import { useSidebar } from "@/lib/context/sidebar-context"
import { Model } from "@/lib/types/openapi"
import { getInitialEndpoint } from "@/lib/utils/endpoint-selection"
import { ModelCard } from "./components/model-card"
import { flattenCategoryTrees } from "./utils/category-tree"
import { useEndpointData } from "./hooks/useEndpointData"

// 标签颜色配置
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

/**
 * 模型目录页面组件
 */
const ModelsPage = () => {
  const searchParams = useSearchParams()
  const [selectedCapability, setSelectedCapability] = useState<string>("")
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const { t } = useLanguage()
  const { categoryTrees } = useSidebar()

  // 使用自定义 Hook 获取端点数据
  const { endpoint, features, models, initialLoading, modelsLoading } = useEndpointData(selectedCapability, selectedTags)

  // 使用 useMemo 缓存扁平化后的数据
  const flattenedEndpoints = useMemo(() => {
    return flattenCategoryTrees(categoryTrees)
  }, [categoryTrees])

  /**
   * 根据搜索关键词筛选模型列表
   */
  const filteredModels = useMemo(() => {
    if (!models) return []
    if (!searchQuery.trim()) return models

    const query = searchQuery.toLowerCase().trim()
    return models.filter((model) => {
      // 搜索模型名称
      if (model.modelName.toLowerCase().includes(query)) return true
      // 搜索拥有者名称
      if (model.ownerName?.toLowerCase().includes(query)) return true
      // 搜索端点
      if (model.endpoints?.some(ep => ep.toLowerCase().includes(query))) return true
      // 搜索特性标签
      const modelFeatures = typeof model.features === 'string'
        ? model.features.split(',').map(f => f.trim())
        : model.features || []
      if (modelFeatures.some(f => f.toLowerCase().includes(query))) return true
      return false
    })
  }, [models, searchQuery])

  /**
   * 初始化选中的能力分类选项 endpoint
   */
  useEffect(() => {
    const endpoint = getInitialEndpoint(searchParams.get("endpoint"))
    setSelectedCapability(endpoint)
  }, [searchParams])

  /**
   * 切换标签选择状态
   */
  const toggleTag = (tag: string) => {
    setSelectedTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]))
  }

  /**
   * 处理添加渠道操作
   */
  const handleAddChannel = (model: Model) => {
    console.log("添加私有渠道:", model.modelName)
  }

  return (
    <>
      <TopBar title={t("modelCatalog")} description={t("modelCatalogDesc")} />

      <div className="flex h-[calc(100vh-4rem)] flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto">
          <div className="container px-6 py-8">
            {/* 能力分类选择 */}
            <div className="mb-6">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("capabilityCategory")}</span>
              </div>
              <div className="flex gap-2 overflow-x-auto pb-2">
                {flattenedEndpoints.map((capability) => {
                  const isSelected = selectedCapability === capability.endpoint
                  return (
                    <Button
                      key={capability.endpoint}
                      variant={isSelected ? "default" : "outline"}
                      size="sm"
                      onClick={() => {
                        setSelectedCapability(capability.endpoint)
                        setSelectedTags([])
                      }}
                      className={`flex items-center gap-2 whitespace-nowrap ${isSelected ? "bg-primary text-primary-foreground" : ""
                        }`}
                    >
                      {capability.endpointName}
                    </Button>
                  )
                })}
              </div>
            </div>

            {/* 搜索框 */}
            <div className="mb-6">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder={t("searchModels")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 pr-10 text-sm"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    <X className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>

            {/* 快速筛选标签 */}
            <div className="mb-8">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("quickFilter")}</span>
              </div>
              {initialLoading ? (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Loader className="h-4 w-4 animate-spin" />
                  <span className="text-sm">加载中...</span>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {features.map((feature) => (
                    <Badge
                      key={feature.code}
                      variant={selectedTags.includes(feature.code) ? "default" : "outline"}
                      className={`cursor-pointer transition-colors`}
                      onClick={() => toggleTag(feature.code)}
                    >
                      {feature.name}
                    </Badge>
                  ))}
                  {(!features || features.length === 0) && (
                    <span className="text-sm text-muted-foreground">暂无可用筛选标签</span>
                  )}
                </div>
              )}
            </div>

            {/* 模型列表 */}
            <div className="mb-4">
              <h2 className="text-sm font-medium text-muted-foreground">
                {t("foundModels")} {filteredModels.length} {t("modelsCount")}
              </h2>
            </div>

            {modelsLoading ? (
              <div className="flex items-center justify-center py-12 text-muted-foreground">
                <Loader className="h-6 w-6 animate-spin mr-2" />
                <span className="text-sm">正在加载模型...</span>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {filteredModels.map((model) => (
                  <ModelCard
                    key={model.modelName}
                    model={model}
                    tagColors={tagColors}
                    onAddChannel={handleAddChannel}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

export default ModelsPage
