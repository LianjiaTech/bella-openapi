"use client"

import * as React from "react"
import { MetricsRequestChart } from "./MetricsRequestChart"
import { MetricsTokenChart } from "./MetricsTokenChart"
import { MetricsPerformanceChart } from "./MetricsPerformanceChart"
import { useStatusFilter } from "../../context/StatusFilterContext"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/common/table"
import { Badge } from "@/components/common/badge"

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
    success_estimated: Math.max(d.completed - d.request_too_many - d.errors, 0),
    request_too_many: d.request_too_many,
    errors: d.errors,
  }))

  const tokenData = data.chartData?.map(d => ({
    time: d.time,
    input_token: d.input_token,
    output_token: d.output_token,
    token: d.token,
    total_token: d.total_token,
  }))

  const performanceData = data.chartData?.map(d => ({
    time: d.time,
    ttft: d.ttft,
    ttlt: d.ttlt,
  }))

  const formatPercent = (value: number) => `${(value * 100).toFixed(1)}%`
  const formatNumber = (value: number) => value.toLocaleString()

  return (
    <div className="space-y-6">
      {/* 请求指标图表 */}
      <MetricsRequestChart
        loading={loading}
        data={requestData}
      />

      {/* Token 使用量图表 */}
      <MetricsTokenChart
        loading={loading}
        data={tokenData}
      />

      {/* 性能指标图表 */}
      <MetricsPerformanceChart
        loading={loading}
        data={performanceData}
        ttftUnit={data.summary.ttftUnit}
        ttltUnit={data.summary.ttltUnit}
      />

      <div className="rounded-md border bg-card">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <h3 className="text-base font-medium">渠道明细</h3>
            <p className="text-sm text-muted-foreground">按当前筛选时间范围汇总</p>
          </div>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>渠道</TableHead>
              <TableHead>状态</TableHead>
              <TableHead className="text-right">请求数</TableHead>
              <TableHead className="text-right">成功率估算</TableHead>
              <TableHead className="text-right">429</TableHead>
              <TableHead className="text-right">5xx</TableHead>
              <TableHead className="text-right">平均 TTFT</TableHead>
              <TableHead className="text-right">平均 TTLT</TableHead>
              <TableHead className="text-right">Token</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={9} className="text-center text-muted-foreground">
                  加载中...
                </TableCell>
              </TableRow>
            ) : data.channelSummaries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="text-center text-muted-foreground">
                  当前时间范围内暂无渠道数据
                </TableCell>
              </TableRow>
            ) : (
              data.channelSummaries.map((channel) => (
                <TableRow key={channel.channelCode}>
                  <TableCell className="font-mono text-xs">{channel.channelCode}</TableCell>
                  <TableCell>
                    <Badge
                      variant={channel.status === 1 ? "secondary" : "destructive"}
                      className={channel.status === 1 ? "bg-green-50 text-green-700" : ""}
                    >
                      {channel.status === 1 ? "可用" : "不可用"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">{formatNumber(channel.completed)}</TableCell>
                  <TableCell className="text-right">{formatPercent(channel.successRateEstimated)}</TableCell>
                  <TableCell className="text-right">{formatNumber(channel.requestTooMany)}</TableCell>
                  <TableCell className="text-right">{formatNumber(channel.errors)}</TableCell>
                  <TableCell className="text-right">{channel.avgTtft}{data.summary.ttftUnit}</TableCell>
                  <TableCell className="text-right">{channel.avgTtlt}{data.summary.ttltUnit}</TableCell>
                  <TableCell className="text-right">{formatNumber(channel.totalToken)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
