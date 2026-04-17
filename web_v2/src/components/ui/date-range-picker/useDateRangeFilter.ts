"use client"

import * as React from "react"
import { DateRange } from "react-day-picker"

/**
 * 日期范围筛选 Hook 配置选项
 */
export interface UseDateRangeFilterOptions {
  /** 默认时间范围（分钟），默认为 15 */
  defaultMinutes?: number
}

/**
 * 日期范围筛选 Hook 返回值
 */
export interface UseDateRangeFilterResult {
  /** 当前日期范围 */
  dateRange: DateRange | undefined
  /** 当前日期范围的分钟数 */
  dateRangeInMinutes: number | undefined
  /** 处理日期范围变化的函数 */
  handleDateRangeChange: (range: DateRange | undefined) => void
  /** 获取默认日期范围的函数 */
  getDefaultDateRange: (minutes?: number) => DateRange
}

/**
 * 获取指定时间范围的日期范围
 * @param minutes - 时间范围（分钟），默认为 15
 */
const getDefaultDateRange = (minutes: number = 15): DateRange => {
  const end = new Date()
  const start = new Date()
  start.setMinutes(start.getMinutes() - minutes)
  return { from: start, to: end }
}

/**
 * 将日期范围转换为分钟数
 * @param dateRange - 日期范围
 * @returns 分钟数，如果日期范围无效则返回 undefined
 */
const convertDateRangeToMinutes = (
  dateRange: DateRange | undefined
): number | undefined => {
  if (!dateRange?.from || !dateRange?.to) {
    return undefined
  }

  const diffMs = dateRange.to.getTime() - dateRange.from.getTime()
  return Math.ceil(diffMs / (1000 * 60))
}

/**
 * 日期范围筛选 Hook
 *
 * 用于管理日期范围筛选逻辑，提供日期范围状态管理和辅助函数
 *
 * @param options - 配置选项
 * @returns 日期范围状态和处理函数
 *
 * @example
 * ```tsx
 * // 使用默认15分钟
 * const { dateRange, handleDateRangeChange } = useDateRangeFilter()
 *
 * // 使用自定义默认时间范围（30分钟）
 * const { dateRange, handleDateRangeChange } = useDateRangeFilter({ defaultMinutes: 30 })
 * ```
 */
export function useDateRangeFilter(
  options?: UseDateRangeFilterOptions
): UseDateRangeFilterResult {
  const { defaultMinutes = 15 } = options || {}

  // 默认设置为 undefined，避免 SSR hydration 不匹配
  const [dateRange, setDateRange] = React.useState<DateRange | undefined>(undefined)

  // 在客户端挂载后设置默认日期范围
  React.useEffect(() => {
    setDateRange(getDefaultDateRange(defaultMinutes))
  }, [defaultMinutes])

  // 计算当前日期范围的分钟数
  const dateRangeInMinutes = React.useMemo(
    () => convertDateRangeToMinutes(dateRange),
    [dateRange]
  )

  // 处理日期范围变化
  const handleDateRangeChange = React.useCallback((range: DateRange | undefined) => {
    setDateRange(range)
  }, [])

  return {
    dateRange,
    dateRangeInMinutes,
    handleDateRangeChange,
    getDefaultDateRange,
  }
}
