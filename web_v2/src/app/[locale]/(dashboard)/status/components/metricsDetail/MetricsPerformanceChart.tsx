"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"
import { TimeIntervalSelect } from "./TimeIntervalSelect"
import type { TimeInterval } from "@/lib/types/status"
import { calculateTickCount, aggregateDataByInterval, calculateXAxisTicks } from "./utils"

interface MetricsPerformanceChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    ttft: number
    ttlt: number
  }>
  /** 日期范围（分钟数） */
  dateRange?: number
}

export function MetricsPerformanceChart({
  loading = false,
  data,
  dateRange,
}: MetricsPerformanceChartProps) {
  // 每个图表独立管理自己的时间间隔
  const [timeInterval, setTimeInterval] = React.useState<TimeInterval>('5')

  // 根据 dateRange 的变化动态计算 count，最少为1
  const count = React.useMemo(
    () => calculateTickCount(dateRange, timeInterval),
    [dateRange, timeInterval]
  )

  // 按时间间隔聚合数据（时间桶分组），性能指标使用平均值
  const formattedData = React.useMemo(
    () => aggregateDataByInterval(
      data,
      Number(timeInterval),
      [],
      ['ttft', 'ttlt']  // 性能指标需要计算平均值而非求和
    ),
    [data, timeInterval]
  )

  // 计算需要显示的 X 轴刻度点
  const xAxisTicks = React.useMemo(
    () => calculateXAxisTicks(formattedData, count),
    [formattedData, count]
  )

  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-medium">性能指标</CardTitle>
            <TimeIntervalSelect
              value={timeInterval}
              onValueChange={setTimeInterval}
            />
          </div>
        </CardHeader>
        <CardContent>
          <Skeleton className="h-[320px] w-full" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-medium">性能指标</CardTitle>
          <TimeIntervalSelect
            value={timeInterval}
            onValueChange={setTimeInterval}
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className="h-[320px] w-full">
          <LineChart
            data={formattedData}
            width={"100%"}
            height={320}
            margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="time"
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              ticks={xAxisTicks}
            />
            <YAxis
              yAxisId="left"
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}ms`}
            />
            <YAxis
              yAxisId="right"
              orientation="right"
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}s`}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: "white",
                border: "1px solid #e5e7eb",
                borderRadius: "6px",
                fontSize: "12px",
              }}
              formatter={(value, name) => {
                if (value === undefined) return ''
                if (name === "TTFT（首包响应时间）") return `${value}ms`
                if (name === "TTLT（总响应时间）") return `${value}s`
                return value
              }}
            />
            <Legend
              wrapperStyle={{ fontSize: "12px" }}
              iconType="circle"
            />
            <Line
              yAxisId="left"
              type="monotone"
              dataKey="ttft"
              stroke="#10b981"
              name="TTFT（首包响应时间）"
              dot={false}
            />
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="ttlt"
              stroke="#ec4899"
              name="TTLT（总响应时间）"
              dot={false}
            />
          </LineChart>
        </div>
      </CardContent>
    </Card>
  )
}
