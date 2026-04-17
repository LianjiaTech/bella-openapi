"use client"

import * as React from "react"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useEndpoint } from "@/app/[locale]/(dashboard)/status/components/statusFilter/hooks/useEndpoint"

/**
 * 能力点选择器组件属性
 */
interface EndpointSelectorProps {
  /** 当前选中的能力点代码 */
  value?: string
  /** 能力点变化回调 */
  onValueChange?: (value: string) => void
  /** 占位符文本 */
  placeholder?: string
  /** 自定义样式类名 */
  className?: string
}

/**
 * 能力点选择器组件
 *
 * 使用 Combobox 组件渲染扁平化后的能力点列表
 * 数据来源于 flattenCategoryTrees 函数处理后的能力点数据
 */
export function EndpointSelector({
  value,
  onValueChange,
  placeholder = "选择能力点",
  className,
}: EndpointSelectorProps) {
  // 获取扁平化的能力点数据
  const { flattenedEndpoints, isLoading } = useEndpoint()

  console.log("flattenedEndpoints", flattenedEndpoints)

  // 将 EndpointWithCategory[] 转换为 ComboboxOption[] 格式
  const options: ComboboxOption[] = React.useMemo(() => {
    return flattenedEndpoints.map((endpoint) => ({
      value: endpoint.endpoint,
      label: `${endpoint.endpoint} (${endpoint.endpointName})`,
    }))
  }, [flattenedEndpoints])

  return (
    <Combobox
      options={options}
      value={value}
      onValueChange={onValueChange}
      placeholder={placeholder}
      searchPlaceholder="搜索能力点..."
      emptyText="未找到匹配的能力点"
      disabled={isLoading}
      className={className}
    />
  )
}
