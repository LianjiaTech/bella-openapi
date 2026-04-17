"use client"

import * as React from "react"
import { useLanguage } from "@/components/providers/language-provider"
import { PrefixedInput, type PrefixOption } from "@/components/common/prefixed-input"
import { QueryTypeSelectorProps, QueryType } from "./types"

export type { QueryType } from "./types"
export { QUERY_TYPE_TO_FIELD_MAP } from "./constants"

/**
 * 通用查询类型选择器组件
 *
 * 支持动态配置可用的查询类型：
 * - OpenAPI 日志: AK Code / Request ID / Bella TraceID
 * - Bella 链路: AK Code / Bella TraceID
 */
export function QueryTypeSelector({
  queryType,
  queryValue,
  error,
  onQueryTypeChange,
  onQueryValueChange,
  className,
  availableTypes = [], // 默认全部
}: QueryTypeSelectorProps) {
  const { t } = useLanguage()

  // 根据 availableTypes 动态生成前缀选项
  const prefixOptions: PrefixOption[] = React.useMemo(() => {
    return availableTypes.map(type => ({
      value: type,
      label: type,
    }))
  }, [availableTypes])

  const handlePrefixChange = (selectedType: string) => {
    if (availableTypes.includes(selectedType as QueryType)) {
      onQueryTypeChange(selectedType as QueryType)
    }
  }

  return (
    <PrefixedInput
      prefixOptions={prefixOptions}
      defaultPrefix={queryType}
      onPrefixChange={handlePrefixChange}
      value={queryValue}
      onValueChange={onQueryValueChange}
      placeholder={t("logs.queryPlaceholder")}
      containerClassName={className}
      error={error}
      required
    />
  )
}
