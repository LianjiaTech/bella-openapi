"use client"

import { useState, useMemo, useCallback, useEffect } from "react"
import { Layers, Loader } from "lucide-react"
import type { MetadataFeature } from "@/lib/types/openapi"
import type { CategoryTree } from "@/lib/types/models"
import { flattenCategoryTrees } from "./utils"
import { useLanguage } from "@/components/providers/language-provider"
import { FeatureTagList } from "./components/FeatureTagList"
import { CapabilitySelector } from "./components/CapabilitySelector"
import { CustomFilterSection } from "./components/CustomFilterSection"
import { useCustomFilters } from "./hooks/useCustomFilters"
import { CustomFilter } from "./types"

// 重新导出类型定义,保持向后兼容
export type { CustomFilter, CustomFilterOption } from "./types"

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

  // 自定义筛选器配置
  customFilters?: CustomFilter[]
  onCustomFilterChange?: (filterId: string, value: string | string[]) => void

  // 可选配置
  className?: string
}

/**
 * 模型筛选面板组件
 * 提供能力分类、搜索、特性标签和自定义筛选器功能
 */
export function ModelFilterPanel({
  categoryTrees,
  features,
  initialEndpoint = "",
  initialTags = [],
  isLoadingFeatures = false,
  onCapabilityChange,
  onTagsChange,
  customFilters = [],
  onCustomFilterChange,
  className = "",
}: ModelFilterPanelProps) {
  const { t } = useLanguage()

  // 状态管理 - 使用非受控模式,仅在初始化时读取 props
  const [selectedCapability, setSelectedCapability] = useState(initialEndpoint)
  const [selectedTags, setSelectedTags] = useState<string[]>(initialTags)

  // 自定义筛选器状态管理 - 使用 useReducer
  const { values: customFilterValues, setValue: setCustomFilterValue } = useCustomFilters(customFilters)

  // 扁平化端点数据
  const flattenedEndpoints = useMemo(() => flattenCategoryTrees(categoryTrees),[categoryTrees])

  // 同步 initialEndpoint prop 的变化到内部状态
  useEffect(() => {
    if (initialEndpoint && initialEndpoint !== selectedCapability) {
      setSelectedCapability(initialEndpoint)
    }
  }, [initialEndpoint])

  // 同步 initialTags prop 的变化到内部状态
  useEffect(() => {
    if (initialTags.length > 0 && JSON.stringify(initialTags) !== JSON.stringify(selectedTags)) {
      setSelectedTags(initialTags)
    }
  }, [initialTags])

  // 处理能力分类变化
  const handleCapabilityChange = useCallback((endpoint: string) => {
    setSelectedCapability(endpoint)
    setSelectedTags([])  // 清空标签选择
    onCapabilityChange(endpoint)
    onTagsChange([])  // 同步清空父组件的标签状态
  }, [onCapabilityChange, onTagsChange])

  // 处理标签切换
  const handleTagToggle = useCallback((tag: string) => {
    const newTags = selectedTags.includes(tag)
      ? selectedTags.filter(t => t !== tag)
      : [...selectedTags, tag]

    setSelectedTags(newTags)
    onTagsChange(newTags)
  }, [selectedTags, onTagsChange])

  // 处理自定义筛选器变化
  const handleCustomFilterChange = useCallback((filterId: string, value: string | string[]) => {
    setCustomFilterValue(filterId, value)
    onCustomFilterChange?.(filterId, value)
  }, [setCustomFilterValue, onCustomFilterChange])

  return (
    <div className={className}>
      {/* 能力分类选择 */}
      <CapabilitySelector
        endpoints={flattenedEndpoints}
        selectedCapability={selectedCapability}
        onCapabilityChange={handleCapabilityChange}
        label={t("capabilityCategory")}
      />

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
            selectedTags={selectedTags}
            onTagToggle={handleTagToggle}
          />
        )}
      </div>

      {/* 自定义筛选器 */}
      <CustomFilterSection
        filters={customFilters}
        values={customFilterValues}
        onChange={handleCustomFilterChange}
      />
    </div>
  )
}
