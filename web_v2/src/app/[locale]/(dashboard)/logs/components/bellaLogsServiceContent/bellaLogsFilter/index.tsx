"use client"

import * as React from "react"
import { DateRange } from "react-day-picker"
import { DateRangePicker } from "@/components/ui/date-range-picker"
import { QueryTypeSelector, QueryType } from "../../common/QueryTypeSelector"
import { Button } from "@/components/common/button"
import { useLanguage } from "@/components/providers/language-provider"
import { getLogDatePresets } from "../../common/constants/datePresets"

interface LogsFilterProps {
  dateRange?: DateRange
  onDateRangeChange?: (range: DateRange | undefined) => void
  queryType: QueryType
  queryValue: string
  queryValueError?: string
  onQueryTypeChange: (type: QueryType) => void
  onQueryValueChange: (value: string) => void
  endpointCode?: string
  onEndpointCodeChange?: (code: string) => void
  modelName?: string
  onModelNameChange?: (name: string) => void
  httpCode?: string
  onHttpCodeChange?: (status: string) => void
  limit?: number
  onLimitChange?: (limit: number) => void
  onSearch?: () => void
  onReset?: () => void
}


/**
 * 日志筛选器组件
 *
 * 提供日志查询的筛选条件UI，包括查询类型选择和时间范围选择
 */
export function LogsFilter({
  dateRange,
  onDateRangeChange,
  queryType,
  queryValue,
  queryValueError,
  onQueryTypeChange,
  onQueryValueChange,
  onSearch,
  onReset,
}: LogsFilterProps) {
  const { t } = useLanguage()
  const customPresets = React.useMemo(() => getLogDatePresets(t), [t])

  return (
    <div className="filter-card">
      <div className="flex flex-col gap-4">
        {/* 标题 */}
        <div className="flex items-center justify-between">
          <h3 className="text-base font-medium">{t("logs.filter.title")}</h3>
        </div>

        {/* 筛选字段容器 */}
        <div className="flex flex-col gap-4">

          {/* 时间范围 + 能力点 + 模型 - 同一行 */}
          <div className="grid grid-cols-1 gap-x-6 gap-y-5 md:grid-cols-2 lg:grid-cols-3">
            {/* 查询方式 - 占据半行宽度 */}
            <div className="lg:col-span-2">
              <label className="text-sm text-foreground whitespace-nowrap">
                {t("logs.filter.queryMethod")}
              </label>
              <QueryTypeSelector
                queryType={queryType}
                queryValue={queryValue}
                error={queryValueError}
                availableTypes={["AK Code", "Bella TraceID"]}
                onQueryTypeChange={onQueryTypeChange}
                onQueryValueChange={onQueryValueChange}
              />
            </div>
            {/* 时间范围 */}
            <div>
              <label className="text-sm text-foreground whitespace-nowrap">
                {t("logs.filter.timeRange")}
              </label>
              <DateRangePicker
                value={dateRange}
                onChange={onDateRangeChange}
                placeholder={t("logs.filter.placeholders.selectTimeRange")}
                presets={customPresets}
              />
            </div>
          </div>

          {/* 操作按钮区域 */}
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={onReset}
            >
              {t("logs.filter.resetButton")}
            </Button>
            <Button
              variant="default"
              onClick={onSearch}
            >
              {t("logs.filter.searchButton")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

export { LogsFilter as LogsFilterBella }
