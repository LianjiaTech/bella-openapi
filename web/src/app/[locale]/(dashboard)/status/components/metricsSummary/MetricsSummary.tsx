"use client"

import * as React from "react"
import { MetricsSummaryCard } from "./MetricsSummaryCard"
import { useStatusFilter } from "../../context/StatusFilterContext"

/**
 * 指标汇总组件
 *
 * 从 StatusFilterContext 读取汇总数据和加载状态
 * 展示健康结论、请求分布、Token 和平均延迟
 */
export function MetricsSummary() {
  // 从 Context 获取汇总数据和加载状态
  const { data, loading } = useStatusFilter()
  const { summary } = data

  // 格式化显示值
  const formatValue = (value: number, suffix?: string): string => {
    if (loading) return "--"
    if (!Number.isFinite(value)) return "--"
    return suffix ? `${value}${suffix}` : value.toLocaleString()
  }

  const formatPercent = (value: number): string => {
    if (loading || !Number.isFinite(value)) return "--"
    return `${(value * 100).toFixed(1)}%`
  }

  const healthStyle = {
    available: "border-green-200 bg-green-50 text-green-700",
    unstable: "border-yellow-200 bg-yellow-50 text-yellow-700",
    unavailable: "border-red-200 bg-red-50 text-red-700",
    no_data: "border-slate-200 bg-slate-50 text-slate-600",
  }[summary.healthStatus]

  return (
    <div className="space-y-4">
      <div className={`rounded-md border px-4 py-3 ${healthStyle}`}>
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium">模型状态</span>
            <span className="rounded-full bg-white/80 px-2.5 py-1 text-sm font-semibold">
              {loading ? "--" : summary.healthLabel}
            </span>
          </div>
          <span className="text-sm">
            {loading ? "正在加载指标数据..." : summary.healthDescription}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <MetricsSummaryCard
          title="请求总数"
          value={formatValue(summary.totalRequests)}
          type="success"
          loading={loading}
        />
        <MetricsSummaryCard
          title="成功率估算"
          value={formatPercent(summary.successRateEstimated)}
          type="success"
          loading={loading}
        />
        <MetricsSummaryCard
          title="429 比例"
          value={formatPercent(summary.rateLimitRate)}
          type="warning"
          loading={loading}
        />
        <MetricsSummaryCard
          title="5xx 比例"
          value={formatPercent(summary.errorRate)}
          type="error"
          loading={loading}
        />
        <MetricsSummaryCard
          title="平均 TTFT"
          value={formatValue(summary.avgTtft, summary.ttftUnit)}
          type="info"
          loading={loading}
        />
        <MetricsSummaryCard
          title="平均 TTLT"
          value={formatValue(summary.avgTtlt, summary.ttltUnit)}
          type="info"
          loading={loading}
        />
        <MetricsSummaryCard
          title="总 Token"
          value={formatValue(summary.totalToken)}
          type="info"
          loading={loading}
        />
        <MetricsSummaryCard
          title="有样本渠道"
          value={loading ? "--" : `${summary.availableChannels}/${summary.totalChannels}`}
          type={summary.healthStatus === "unavailable" ? "error" : "success"}
          loading={loading}
        />
      </div>
    </div>
  )
}
