'use client'

import { TopBar } from "@/components/layout"
import { useLanguage } from "@/components/providers/language-provider"
import { useSidebar } from "@/components/providers"
import { useMemo, useDeferredValue } from "react"
import { ModelFilterPanel } from "@/components/ui/modelFilterPanel"
import { useSearchParams } from "next/navigation"
import { useState, useEffect, useCallback } from "react"
import { useMetadataData } from "./hooks"
import { getInitialEndpoint } from "@/lib/utils"
import { Model } from "@/lib/types/openapi"
import { Loader, AlertCircle, Plus } from "lucide-react"
import { MetadataModelCard } from "./components/MetadataModelCard"
import { Button } from "@/components/common/button"
import { CreateModelDialog } from "./components/CreateModelDialog"
import { ConfigureModelPanel } from "./components/ConfigureModelPanel"
import { VirtualGrid } from "@/components/ui/virtualGrid"
import { SearchBar } from "@/components/ui/modelFilterPanel/components/SearchBar"
import { StatusSelector } from "@/components/ui/status-selector"

/**
 * 元数据管理页面组件
 */
const MetadataPage = () => {
  const searchParams = useSearchParams()
  const { t } = useLanguage()
  const { categoryTrees } = useSidebar()
  const [selectedCapability, setSelectedCapability] = useState<string>("")
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedStatus, setSelectedStatus] = useState<string|null>(null)
  const [selectedVisibility, setSelectedVisibility] = useState<string|null>(null)
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [selectedModel, setSelectedModel] = useState<Model | null>(null)

  // 使用自定义 Hook 获取元数据
  const { features, models, initialLoading, modelsLoading, error, refetch } = useMetadataData(selectedCapability, selectedTags, selectedStatus, selectedVisibility)

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
   * 处理新建模型操作
   */
  const handleCreateModel = () => {
    setIsCreateDialogOpen(true)
  }

  /**
   * 处理配置模型操作
   */
  const handleConfigureModel = (model: Model) => {
    setSelectedModel(model)
  }

  /**
   * 处理返回列表操作
   */
  const handleBackToList = () => {
    setSelectedModel(null)
  }

  return (
    <>
      {/* 条件渲染:列表视图或配置视图 */}
      {selectedModel ? (
        /* 配置视图 - 淡入动画 */
        <div className="animate-in fade-in duration-300">
          <ConfigureModelPanel
            model={selectedModel}
            onBack={handleBackToList}
          />
        </div>
      ) : (
        /* 列表视图 - 淡入动画 */
        <div className="animate-in fade-in duration-300">
          <TopBar
            title="元数据管理"
            description="管理和配置属于您有模型的元数据信息"
            action={
              <Button onClick={handleCreateModel} className="gap-2 cursor-pointer">
                <Plus className="h-4 w-4" />
                新模型
              </Button>
            }
          />
          <div className="flex h-[calc(100vh-4rem)] flex-col overflow-hidden">
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
                <div className="mt-4 flex items-center justify-between gap-4">
                  <div className="flex-shrink-0">
                    <h2 className="text-sm font-medium text-muted-foreground whitespace-nowrap">
                      找到 {filteredModels.length} 个模型
                    </h2>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusSelector
                      label="状态"
                      value={selectedStatus}
                      onChange={setSelectedStatus}
                      options={[
                        { value: 'active', label: '启用' },
                        { value: 'inactive', label: '停用' }
                      ]}
                    />
                    <StatusSelector
                      label="权限"
                      value={selectedVisibility}
                      onChange={setSelectedVisibility}
                      options={[
                        { value: 'public', label: '公开' },
                        { value: 'private', label: '私有' }
                      ]}
                    />
                    <SearchBar
                      value={searchQuery}
                      onChange={handleSearchChange}
                      onClear={() => setSearchQuery("")}
                      placeholder="搜索模型名称或描述..."
                      className="mb-0"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* 虚拟滚动容器（可滚动区域） */}
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
                    <MetadataModelCard
                      model={model}
                      onConfigure={handleConfigureModel}
                    />
                  )}
                  emptyElement={
                    <div className="flex items-center justify-center h-full text-muted-foreground">
                      <p className="text-sm">暂无模型</p>
                    </div>
                  }
                  className="px-6 py-6"
                />
              )}
            </div>
          </div>
        </div>
      )}

      {/* 新建模型弹窗 */}
      <CreateModelDialog
        mode="create"
        open={isCreateDialogOpen}
        onClose={() => setIsCreateDialogOpen(false)}
        onSuccess={() => {
          // 创建成功后刷新模型列表
          refetch()
        }}
      />
    </>
  )
}

export default MetadataPage
