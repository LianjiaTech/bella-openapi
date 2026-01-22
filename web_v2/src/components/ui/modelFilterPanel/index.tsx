"use client"

import { useState, useMemo, useCallback, useEffect } from "react"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Search, Layers, Loader, X, LucideIcon } from "lucide-react"
import { MetadataFeature } from "@/lib/types/openapi"
import { CategoryTree } from "@/lib/types/models"
import { flattenCategoryTrees } from "./utils"
import { useLanguage } from "@/components/providers/language-provider"
import { FeatureTagList } from "./components/FeatureTagList"
import { Badge } from "@/components/common/badge"

/**
 * 自定义筛选项配置
 */
export interface CustomFilterOption {
  value: string           // 选项值
  label: string          // 选项显示文本
  description?: string   // 选项描述(可选)
  color?: string        // 自定义颜色类(可选)
}

export interface CustomFilter {
  id: string                    // 筛选器唯一标识
  label: string                 // 筛选器标题
  icon?: LucideIcon            // 筛选器图标(可选)
  type: 'single' | 'multiple'  // 单选或多选
  options: CustomFilterOption[] // 筛选选项列表
  defaultValue?: string | string[] // 默认值
  className?: string           // 自定义样式类(可选)
  layout?: 'horizontal' | 'grid' // 布局方式(可选)
}

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

  // 自定义筛选器配置(新增)
  customFilters?: CustomFilter[]
  onCustomFilterChange?: (filterId: string, value: string | string[]) => void

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
  customFilters = [],
  onCustomFilterChange,
  className = "",
  searchPlaceholder,
}: ModelFilterPanelProps) {

  const { t } = useLanguage()

  // 内部状态
  const [searchQuery, setSearchQuery] = useState("")
  const [localSelectedCapability, setLocalSelectedCapability] = useState(initialEndpoint)
  const [localSelectedTags, setLocalSelectedTags] = useState<string[]>(initialTags)

  // 自定义筛选器状态:使用 Map 存储每个筛选器的选中值
  const [customFilterValues, setCustomFilterValues] = useState<Map<string, string | string[]>>(() => {
    const initialValues = new Map<string, string | string[]>()
    customFilters.forEach(filter => {
      if (filter.defaultValue !== undefined) {
        initialValues.set(filter.id, filter.defaultValue)
      } else {
        // 根据类型初始化默认值
        initialValues.set(filter.id, filter.type === 'multiple' ? [] : '')
      }
    })
    return initialValues
  })

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

  // 处理自定义筛选器变化
  const handleCustomFilterChange = useCallback((filterId: string, value: string | string[]) => {
    setCustomFilterValues(prev => {
      const newMap = new Map(prev)
      newMap.set(filterId, value)
      return newMap
    })
    onCustomFilterChange?.(filterId, value)
  }, [onCustomFilterChange])

  // 处理单选筛选器的选项点击
  const handleSingleOptionClick = useCallback((filterId: string, optionValue: string) => {
    const currentValue = customFilterValues.get(filterId)
    // 如果点击已选中的选项,则取消选中(设为空字符串)
    const newValue = currentValue === optionValue ? '' : optionValue
    handleCustomFilterChange(filterId, newValue)
  }, [customFilterValues, handleCustomFilterChange])

  // 处理多选筛选器的选项点击
  const handleMultipleOptionClick = useCallback((filterId: string, optionValue: string) => {
    const currentValue = customFilterValues.get(filterId) as string[] || []
    const newValue = currentValue.includes(optionValue)
      ? currentValue.filter(v => v !== optionValue)
      : [...currentValue, optionValue]
    handleCustomFilterChange(filterId, newValue)
  }, [customFilterValues, handleCustomFilterChange])

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
          />
        )}
      </div>

      {/* 自定义筛选器 */}
      {customFilters.map((filter) => {
        const FilterIcon = filter.icon || Layers
        const currentValue = customFilterValues.get(filter.id)
        const layoutClass = filter.layout === 'grid'
          ? 'grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2'
          : 'flex flex-wrap gap-2'

        return (
          <div key={filter.id} className={`mb-8 ${filter.className || ''}`}>
            {/* 筛选器标题 */}
            <div className="mb-3 flex items-center gap-2">
              <FilterIcon className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium text-muted-foreground">
                {filter.label}
              </span>
            </div>

            {/* 筛选器选项 */}
            <div className={layoutClass}>
              {filter.options.map((option) => {
                // 判断是否选中
                const isSelected = filter.type === 'single'
                  ? currentValue === option.value
                  : Array.isArray(currentValue) && currentValue.includes(option.value)

                return (
                  <div key={option.value} className="relative">
                    {filter.type === 'single' ? (
                      // 单选按钮样式
                      <Badge
                        variant={isSelected ? "default" : "outline"}
                        className={`cursor-pointer transition-colors font-normal rounded-md w-full justify-start ${
                          isSelected ? option.color || "bg-primary text-primary-foreground" : ""
                        }`}
                        onClick={() => handleSingleOptionClick(filter.id, option.value)}
                        title={option.description}
                        style={{ display: "flex", alignItems: "center" }}
                      >
                        {option.label}
                      </Badge>
                    ) : (
                      // 多选徽章样式
                      <Badge
                        variant={isSelected ? "default" : "outline"}
                        className={`cursor-pointer transition-colors font-normal rounded-md ${
                          isSelected ? option.color || "" : ""
                        }`}
                        onClick={() => handleMultipleOptionClick(filter.id, option.value)}
                        title={option.description}
                      >
                        {option.label}
                      </Badge>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}