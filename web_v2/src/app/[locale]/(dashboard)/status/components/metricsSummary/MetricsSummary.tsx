"use client"

import * as React from "react"
import { MetricsSummaryCard } from "./MetricsSummaryCard"
import { useStatusFilter } from "../../context/StatusFilterContext"

/**
 * 指标汇总组件
 *
 * 从 StatusFilterContext 读取汇总数据和加载状态
 * 展示请求总数、限流次数、错误次数和平均 TTFT
 */
export function MetricsSummary() {
  // 从 Context 获取汇总数据和加载状态
  const { data, loading } = useStatusFilter()

  // 格式化显示值
  const formatValue = (value: number, suffix?: string): string => {
    if (loading) return "--"
    if (!value) return "0"
    return suffix ? `${value}${suffix}` : value.toString()
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <MetricsSummaryCard
        title="请求总数"
        value={formatValue(data.summary.totalRequests)}
        type="success"
        loading={loading}
      />
      <MetricsSummaryCard
        title="总限流次数"
        value={formatValue(data.summary.totalRequestTooMany)}
        type="warning"
        loading={loading}
      />
      <MetricsSummaryCard
        title="总错误次数"
        value={formatValue(data.summary.totalErrors)}
        type="error"
        loading={loading}
      />
      <MetricsSummaryCard
        title="平均 TTFT"
        value={formatValue(data.summary.avgTtft, "ms")}
        type="info"
        loading={loading}
      />
    </div>
  )
}
