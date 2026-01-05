'use client'
import { TopBar } from "@/components/layout"
import { useLanguage } from "@/components/providers/language-provider"
import { useSidebar } from "@/components/providers"
import { useMemo } from "react"
import { ModelFilterPanel } from "@/components/ui/models"
import { useSearchParams } from "next/navigation"
import { useState, useEffect, useCallback } from "react"
import { useEndpointData } from "./hooks/useEndpointData"
import { getInitialEndpoint } from "@/lib/utils"
import { Model } from "@/lib/types/openapi"
import { Loader } from "lucide-react"
import { ModelCard } from "@/components/ui/modelCard"

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
  const { t } = useLanguage()
  const { categoryTrees } = useSidebar()
  const [selectedCapability, setSelectedCapability] = useState<string>("")
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  // 使用自定义 Hook 获取端点数据
  const { features, models, initialLoading, modelsLoading } = useEndpointData(selectedCapability, selectedTags)
  /**
   * 根据搜索关键词筛选模型列表
   */
  const filteredModels = useMemo(() => {
    if (!models) return []
    if (!searchQuery.trim()) return models

    const query = searchQuery.toLowerCase().trim()
    return models.filter((model) => {
      // 搜索模型名称
      if (model.modelName?.toLowerCase().includes(query)) return true
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
   * 处理能力分类变化
   */
  const handleCapabilityChange = useCallback((endpoint: string) => {
    setSelectedCapability(endpoint)
    setSelectedTags([])
  }, [])

  /**
   * 处理标签变化
   */
  const handleTagsChange = useCallback((tags: string[]) => {
    setSelectedTags(tags)
  }, [])

  /**
   * 处理搜索变化
   */
  const handleSearchChange = useCallback((query: string) => {
    setSearchQuery(query)
  }, [])

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
            {/* 模型筛选面板 */}
            <ModelFilterPanel
              categoryTrees={categoryTrees}
              features={features}
              initialEndpoint={selectedCapability}
              initialTags={selectedTags}
              isLoadingFeatures={initialLoading}
              onCapabilityChange={handleCapabilityChange}
              onTagsChange={handleTagsChange}
              onSearchChange={handleSearchChange}
            />

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

