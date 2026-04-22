"use client"

import * as React from "react"
import { MetricsRequestChart } from "./MetricsRequestChart"
import { MetricsTokenChart } from "./MetricsTokenChart"
import { MetricsPerformanceChart } from "./MetricsPerformanceChart"
import { useStatusFilter } from "../../context/StatusFilterContext"

/**
 * 指标详情区域组件
 *
 * 从 StatusFilterContext 读取图表数据和加载状态
 * 展示请求指标、Token 使用量和性能指标三个图表
 * 每个图表独立管理自己的时间间隔选择
 */
export function MetricsDetailSection() {
  // 从 Context 获取所有需要的数据
  const { data, loading } = useStatusFilter()

  // 从统一的 data.chartData 中提取各图表所需的数据
  const requestData = data.chartData?.map(d => ({
    time: d.time,
    completed: d.completed,
    request_too_many: d.request_too_many,
    errors: d.errors,
  }))

  const tokenData = data.chartData?.map(d => ({
    time: d.time,
    input_token: d.input_token,
    output_token: d.output_token,
    token: d.token,
  }))

  const performanceData = data.chartData?.map(d => ({
    time: d.time,
    ttft: d.ttft,
    ttlt: d.ttlt,
  }))

  return (
    <div className="space-y-6">
      {/* 请求指标图表 */}
      <MetricsRequestChart
        loading={loading}
        data={requestData}
        dateRangeInMinutes={data.dateRangeInMinutes}
      />

      {/* Token 使用量图表 */}
      <MetricsTokenChart
        loading={loading}
        data={tokenData}
        dateRangeInMinutes={data.dateRangeInMinutes}
      />

      {/* 性能指标图表 */}
      <MetricsPerformanceChart
        loading={loading}
        data={performanceData}
        dateRange={data.dateRangeInMinutes}
      />
    </div>
  )
}
