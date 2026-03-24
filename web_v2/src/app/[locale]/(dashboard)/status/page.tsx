'use client'

import { useState, useCallback, useMemo } from "react"
import { TopBar } from "@/components/layout/top-bar"
import { useLanguage } from "@/components/providers/language-provider"
import { StatusFilter } from "./components/statusFilter"
import { MetricsSummary } from "./components/metricsSummary"
import { MetricsDetailSection } from "./components/metricsDetail"
import { useChannels } from "./hooks/useChannels"
import { formatDateToYYYYMMDDHHmm } from "@/lib/utils/date"
import { useDateRangeFilter } from "@/components/ui/date-range-picker"
import { StatusFilterProvider } from "./context/StatusFilterContext"
import { DEFAULT_ENDPOINT } from "@/lib/constants/constants"

export default function StatusPage() {
  const { t } = useLanguage()

  // ==================== 状态管理 ====================
  // 筛选条件状态
  const [endpoint, setEndpoint] = useState<string>(DEFAULT_ENDPOINT)
  const [modelName, setModelName] = useState<string | undefined>()
  const [channelCode, setChannelCode] = useState<string | undefined>()

  // 日期范围管理（使用公共 Hook）
  const { dateRange, dateRangeInMinutes, handleDateRangeChange } = useDateRangeFilter()

  // ==================== 数据获取 ====================
  const { summary, loading, chartData, metricsData, channels } = useChannels({
    model: modelName,
    endpoint,
    channelCode,
    start: dateRange?.from ? formatDateToYYYYMMDDHHmm(dateRange.from) : undefined,
    end: dateRange?.to ? formatDateToYYYYMMDDHHmm(dateRange.to) : undefined,
  })

  // ==================== 更新方法 ====================
  /**
   * 更新能力分类
   * 级联重置：清空 modelName 和 channelCode
   */
  const updateEndpoint = useCallback((newEndpoint: string) => {
    setEndpoint(newEndpoint)
    setModelName(undefined)
    setChannelCode(undefined)
  }, [])

  /**
   * 更新模型名称
   * 不清空 channelCode，因为渠道列表依赖后端数据
   */
  const updateModel = useCallback((newModel: string) => {
    setModelName(newModel)
  }, [])

  /**
   * 更新渠道代码
   */
  const updateChannel = useCallback((newChannel: string) => {
    setChannelCode(newChannel)
  }, [])

  // ==================== Context Value ====================
  /**
   * 构建 Context 值
   * 使用 useMemo 避免不必要的重渲染
   */
  const contextValue = useMemo(() => ({
    filters: {
      endpoint,
      modelName,
      channelCode,
      dateRange,
    },
    data: {
      metricsData,
      channels,
      summary,
      chartData,
      dateRangeInMinutes,
    },
    loading,
    actions: {
      updateEndpoint,
      updateModel,
      updateChannel,
      updateDateRange: handleDateRangeChange,
    },
  }), [
    // filters 依赖
    endpoint,
    modelName,
    channelCode,
    dateRange,
    // data 依赖
    metricsData,
    channels,
    summary,
    chartData,
    dateRangeInMinutes,
    // 其他依赖
    loading,
    updateEndpoint,
    updateModel,
    updateChannel,
    handleDateRangeChange,
  ])

  return (
    <StatusFilterProvider value={contextValue}>
      <div>
        <TopBar title={t("status.modelStatus")} description={t("status.modelStatusDesc")} />
        <div className="m-4 space-y-4">
          {/* 筛选条件模块 */}
          <StatusFilter />

          {/* 模型状态展示区域 */}
          <div className="space-y-4">
            {/* 指标汇总区域 */}
            <MetricsSummary />

            {/* 指标详情区域 */}
            <MetricsDetailSection />
          </div>
        </div>
      </div>
    </StatusFilterProvider>
  )
}
