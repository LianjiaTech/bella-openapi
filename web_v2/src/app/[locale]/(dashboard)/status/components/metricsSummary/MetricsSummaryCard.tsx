"use client"

import * as React from "react"

export type MetricType = "success" | "warning" | "error" | "info"

interface MetricsSummaryCardProps {
  /** 标题 */
  title: string
  /** 数值 */
  value: string | number
  /** 指标类型，决定图标和颜色 */
  type: MetricType
  /** 是否显示加载状态 */
  loading?: boolean
}

// 根据类型返回对应的图标和颜色
const getMetricStyle = (type: MetricType) => {
  switch (type) {
    case "success":
      return {
        icon: "↗",
        iconClass: "text-green-500",
        bgClass: "bg-green-50",
      }
    case "warning":
      return {
        icon: "⚠",
        iconClass: "text-yellow-500",
        bgClass: "bg-yellow-50",
      }
    case "error":
      return {
        icon: "✕",
        iconClass: "text-red-500",
        bgClass: "bg-red-50",
      }
    case "info":
      return {
        icon: "◷",
        iconClass: "text-blue-500",
        bgClass: "bg-blue-50",
      }
  }
}

export function MetricsSummaryCard({
  title,
  value,
  type,
  loading = false,
}: MetricsSummaryCardProps) {
  const style = getMetricStyle(type)

  return (
    <div className="metric-card flex flex-row items-center justify-between">
      {/* 图标和标题 */}
      <div className="flex flex-col items-center justify-between">

        <span className="text-sm text-muted-foreground">{title}</span>
        {/* 数值 */}
        <div>
          {loading ? (
            <div className="h-10 w-24 bg-muted animate-pulse rounded" />
          ) : (
            <div className="text-xl font-bold">{value}</div>
          )}
        </div>
      </div>
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center ${style.bgClass}`}
      >
        <span className={`text-lg ${style.iconClass}`}>{style.icon}</span>
      </div>

    </div>
  )
}
