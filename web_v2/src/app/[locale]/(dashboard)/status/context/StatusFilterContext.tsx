"use client"

import * as React from "react"
import { DateRange } from "react-day-picker"
import type {
  MetricsAtTime,
  ChannelInfo,
  SummaryData,
  ChartDataPoint,
} from "@/lib/types/status"

/**
 * 状态筛选器 Context 值类型
 */
export interface StatusFilterContextValue {
  // === 筛选条件 ===
  filters: {
    /** 能力分类 (endpoint) */
    endpoint: string
    /** 模型名称 */
    modelName?: string
    /** 渠道代码 */
    channelCode?: string
    /** 日期范围 */
    dateRange?: DateRange
  }

  // === 数据 ===
  data: {
    /** 原始指标数据（按时间点） */
    metricsData: MetricsAtTime[]
    /** 渠道列表 */
    channels: ChannelInfo[]
    /** 汇总统计数据 */
    summary: SummaryData
    /** 图表数据 */
    chartData?: ChartDataPoint[]
    /** 日期范围对应的分钟数 */
    dateRangeInMinutes?: number
  }

  // === 加载状态 ===
  /** 数据加载状态 */
  loading: boolean

  // === 操作方法 ===
  actions: {
    /** 更新能力分类 (会级联重置 modelName 和 channelCode) */
    updateEndpoint: (endpoint: string) => void
    /** 更新模型名称 */
    updateModel: (modelName: string) => void
    /** 更新渠道代码 */
    updateChannel: (channelCode: string) => void
    /** 更新日期范围 */
    updateDateRange: (dateRange: DateRange | undefined) => void
  }
}

/**
 * 状态筛选器 Context
 * 用于在状态监控页面的各个组件之间共享筛选条件、数据和操作方法
 */
const StatusFilterContext = React.createContext<StatusFilterContextValue | undefined>(undefined)

/**
 * 使用状态筛选器 Context 的 Hook
 *
 * @throws 如果在 StatusFilterProvider 外部使用则抛出错误
 * @returns StatusFilterContextValue - 包含筛选条件、数据和操作方法
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { filters, data, loading, actions } = useStatusFilter()
 *
 *   return (
 *     <div>
 *       <button onClick={() => actions.updateEndpoint('/v1/chat/completions')}>
 *         切换到聊天补全
 *       </button>
 *       <p>当前模型: {filters.modelName}</p>
 *       <p>总请求数: {data.summary.totalRequests}</p>
 *     </div>
 *   )
 * }
 * ```
 */
export function useStatusFilter(): StatusFilterContextValue {
  const context = React.useContext(StatusFilterContext)
  if (!context) {
    throw new Error(
      "useStatusFilter must be used within StatusFilterProvider. " +
      "请确保组件被 StatusFilterProvider 包裹。"
    )
  }
  return context
}

/**
 * 状态筛选器 Provider
 * 应该在 StatusPage 中使用，包裹所有需要访问筛选状态的子组件
 *
 * @example
 * ```tsx
 * <StatusFilterProvider value={contextValue}>
 *   <StatusFilter />
 *   <MetricsSummary />
 *   <MetricsDetailSection />
 * </StatusFilterProvider>
 * ```
 */
export const StatusFilterProvider = StatusFilterContext.Provider
