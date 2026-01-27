'use client'
import { TopBar } from "@/components/layout"
import { useLanguage } from "@/components/providers/language-provider"
import { useSidebar } from "@/components/providers/sidebar-provider"
import { useMemo, useDeferredValue } from "react"
import { ModelFilterPanel } from "@/components/ui/modelFilterPanel/index"
import { useSearchParams } from "next/navigation"
import { useState, useEffect, useCallback } from "react"
import { useEndpointData } from "./hooks/useEndpointData"
import { getInitialEndpoint } from "@/lib/utils"
import { Model } from "@/lib/types/openapi"
import { Loader, AlertCircle } from "lucide-react"
import { ModelCard } from "./components/modelCard"
import { Button } from "@/components/common/button"
import { VirtualGrid } from "@/components/ui/virtualGrid/index"
import { SearchBar } from "@/components/ui/modelFilterPanel/components/SearchBar"


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
  const { features, models, initialLoading, modelsLoading, error, refetch } = useEndpointData(selectedCapability, selectedTags)

  // 使用 useDeferredValue 实现搜索防抖，优化大数据量场景下的性能
  const deferredSearchQuery = useDeferredValue(searchQuery)

  /**
   * 根据搜索关键词筛选模型列表
   * 使用 deferredSearchQuery 而不是 searchQuery，避免用户快速输入时频繁计算
   */
  const filteredModels = useMemo(() => {
    if (!models) return []
    if (!deferredSearchQuery.trim()) return models

    const query = deferredSearchQuery.toLowerCase().trim()
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
  }, [models, deferredSearchQuery])

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
    // TODO: 添加渠道操作
  }


  return (
    <>
      <TopBar title={t("modelCatalog")} description={t("modelCatalogDesc")} />
      <div className=" flex h-[calc(100vh-4rem)] flex-col overflow-hidden">
        {/* 筛选面板区域（固定不滚动） */}
        <div className="flex-shrink-0 border-b bg-background">
          <div className="px-6 py-4">
            {/* 模型筛选面板 */}
            <ModelFilterPanel
              categoryTrees={categoryTrees}
              features={features}
              initialEndpoint={selectedCapability}
              initialTags={selectedTags}
              isLoadingFeatures={initialLoading}
              onCapabilityChange={handleCapabilityChange}
              onTagsChange={handleTagsChange}
            />

            {/* 错误提示 */}
            {error && (
              <div className="mt-4 rounded-lg border border-red-500/20 bg-red-500/10 p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
                    <p className="text-sm text-red-500">{error.message}</p>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={refetch}
                    className="ml-4 flex-shrink-0"
                  >
                    {t("retry")}
                  </Button>
                </div>
              </div>
            )}

            {/* 搜索框与模型列表统计 */}
            <div className="mt-4 flex items-center justify-between  gap-4">
              <div className="flex-shrink-0">
                <h2 className="text-sm font-medium text-muted-foreground whitespace-nowrap">
                  {t("foundModels")} {filteredModels.length} {t("modelsCount")}
                </h2>
              </div>
              <div>
                <SearchBar
                  value={searchQuery}
                  onChange={handleSearchChange}
                  onClear={() => setSearchQuery("")}
                  placeholder={t("searchModels")}
                  className="mb-0"
                />
              </div>
            </div>
          </div>
        </div>

        {/* 虚拟滚动容器（可滚动区域）estimateItemSize={estimateModelCardHeight} */}
        <div className="flex-1 overflow-hidden">
          {modelsLoading ? (
            <div className="flex items-center justify-center h-full text-muted-foreground">
              <Loader className="h-6 w-6 animate-spin mr-2" />
              <span className="text-sm">{t("loadingModels")}</span>
            </div>
          ) : (
            <VirtualGrid
              items={filteredModels}
              overscan={5}
              getItemKey={(model) => model.modelName}
              renderItem={(model) => (
                <ModelCard model={model} onAddChannel={handleAddChannel} />
              )}
              emptyElement={
                <div className="flex items-center justify-center h-full text-muted-foreground">
                  <p className="text-sm">{t("noModelsFound")}</p>
                </div>
              }
              className="px-6 py-6"
            />
          )}
        </div>
      </div>
    </>
  )
}

export default ModelsPage

