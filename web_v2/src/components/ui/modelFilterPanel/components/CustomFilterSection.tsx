import { memo, useCallback } from "react"
import { Layers } from "lucide-react"
import { CustomFilter, CustomFilterValues } from "../types"
import { FilterOptionBadge } from "./FilterOptionBadge"

interface CustomFilterSectionProps {
  filters: CustomFilter[]
  values: CustomFilterValues
  onChange: (filterId: string, value: string | string[]) => void
}

/**
 * 自定义筛选器区域组件
 * 支持渲染多个自定义筛选器，每个筛选器可以是单选或多选模式
 */
export const CustomFilterSection = memo(({
  filters,
  values,
  onChange,
}: CustomFilterSectionProps) => {
  // 处理单选选项点击
  const handleSingleOptionClick = useCallback((filterId: string, optionValue: string) => {
    const currentValue = values[filterId]
    // 如果点击已选中的选项,则取消选中(设为空字符串)
    const newValue = currentValue === optionValue ? '' : optionValue
    onChange(filterId, newValue)
  }, [values, onChange])

  // 处理多选选项点击
  const handleMultipleOptionClick = useCallback((filterId: string, optionValue: string) => {
    const currentValue = (values[filterId] as string[]) || []
    const newValue = currentValue.includes(optionValue)
      ? currentValue.filter(v => v !== optionValue)
      : [...currentValue, optionValue]
    onChange(filterId, newValue)
  }, [values, onChange])

  if (filters.length === 0) {
    return null
  }

  return (
    <>
      {filters.map((filter) => {
        const FilterIcon = filter.icon || Layers
        const currentValue = values[filter.id]
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
                  <FilterOptionBadge
                    key={option.value}
                    option={option}
                    isSelected={isSelected}
                    onClick={() => {
                      if (filter.type === 'single') {
                        handleSingleOptionClick(filter.id, option.value)
                      } else {
                        handleMultipleOptionClick(filter.id, option.value)
                      }
                    }}
                    isSingleSelect={filter.type === 'single'}
                  />
                )
              })}
            </div>
          </div>
        )
      })}
    </>
  )
})

CustomFilterSection.displayName = "CustomFilterSection"
