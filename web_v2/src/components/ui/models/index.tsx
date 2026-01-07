"use client"

import { useState, useMemo, useCallback, useEffect } from "react"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Search, Layers, Loader, X } from "lucide-react"
import { CategoryTree, MetadataFeature } from "@/lib/types/openapi"
import { flattenCategoryTrees } from "./utils"
import { useLanguage } from "@/components/providers/language-provider"
import { FeatureTagList } from "./FeatureTagList"
import { TAG_COLORS } from "@/lib/constants/theme"

export interface ModelFilterPanelProps {
  // 数据输入
  categoryTrees: CategoryTree[]
  features: MetadataFeature[]
  initialEndpoint?: string
  initialTags?: string[]

  // 加载状态
  isLoadingFeatures?: boolean

  // 事件回调
  onCapabilityChange: (endpoint: string) => void
  onTagsChange: (tags: string[]) => void
  onSearchChange?: (query: string) => void

  // 可选配置
  className?: string
  searchPlaceholder?: string
}
export function ModelFilterPanel({
  categoryTrees,
  features,
  initialEndpoint = "",
  initialTags = [],
  isLoadingFeatures = false,
  onCapabilityChange,
  onTagsChange,
  onSearchChange,
  className = "",
  searchPlaceholder,
}: ModelFilterPanelProps) {

  const { t } = useLanguage()

  // 内部状态
  const [searchQuery, setSearchQuery] = useState("")
  const [localSelectedCapability, setLocalSelectedCapability] = useState(initialEndpoint)
  const [localSelectedTags, setLocalSelectedTags] = useState<string[]>(initialTags)

  // 扁平化端点数据
  const flattenedEndpoints = useMemo(
    () => flattenCategoryTrees(categoryTrees),
    [categoryTrees]
  )

  // 同步外部初始值变化（受控组件模式）
  // 只在 props 初始化时同步，避免在渲染期间更新状态
  useEffect(() => {
    setLocalSelectedCapability(initialEndpoint)
  }, [initialEndpoint])

  useEffect(() => {
    setLocalSelectedTags(initialTags)
  }, [initialTags])

  // 处理能力分类变化
  const handleCapabilityChange = useCallback((endpoint: string) => {
    setLocalSelectedCapability(endpoint)
    setLocalSelectedTags([])  // 清空标签选择
    onCapabilityChange(endpoint)
    onTagsChange([])  // 同步清空父组件的标签状态
  }, [onCapabilityChange, onTagsChange])

  // 处理标签切换
  const handleTagToggle = useCallback((tag: string) => {
    const newTags = localSelectedTags.includes(tag)
      ? localSelectedTags.filter(t => t !== tag)
      : [...localSelectedTags, tag]
    
    setLocalSelectedTags(newTags)
    onTagsChange(newTags)
  }, [localSelectedTags, onTagsChange])

  // 处理搜索变化
  const handleSearchChange = useCallback((value: string) => {
    setSearchQuery(value)
    onSearchChange?.(value)
  }, [onSearchChange])

  // 清空搜索
  const handleClearSearch = useCallback(() => {
    setSearchQuery("")
    onSearchChange?.("")
  }, [onSearchChange])

  return (
    <div className={className}>
      {/* 能力分类选择 */}
      <div className="mb-6">
        <div className="mb-3 flex items-center gap-2">
          <Layers className="h-4 w-4 text-muted-foreground" />
          <span className="text-sm font-medium text-muted-foreground">
            {t("capabilityCategory")}
          </span>
        </div>
        <div className="flex gap-2 overflow-x-auto pb-2">
          {flattenedEndpoints.map((capability) => {
            const isSelected = localSelectedCapability === capability.endpoint
            return (
              <Button
                key={capability.endpoint}
                variant={isSelected ? "default" : "outline"}
                size="sm"
                onClick={() => handleCapabilityChange(capability.endpoint)}
                className={`flex items-center gap-2 whitespace-nowrap font-normal cursor-pointer ${isSelected ? "bg-primary text-primary-foreground" : ""
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
            placeholder={searchPlaceholder || t("searchModels")}
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="pl-10 pr-10 text-sm"
          />
          {searchQuery && (
            <button
              onClick={handleClearSearch}
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
          <span className="text-sm font-medium text-muted-foreground">
            {t("quickFilter")}
          </span>
        </div>
        {isLoadingFeatures ? (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader className="h-4 w-4 animate-spin" />
            <span className="text-sm">{t("loading")}</span>
          </div>
        ) : features.length === 0 ? (
          <span className="text-sm text-muted-foreground">{t("noFilterTags")}</span>
        ) : (
          <FeatureTagList
            features={features}
            selectedTags={localSelectedTags}
            onTagToggle={handleTagToggle}
            tagColors={TAG_COLORS}
          />
        )}
      </div>
    </div>
  )
}
