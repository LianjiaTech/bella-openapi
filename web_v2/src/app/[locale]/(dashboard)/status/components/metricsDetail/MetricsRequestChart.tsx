"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"
import { TimeIntervalSelect } from "./TimeIntervalSelect"
import type { TimeInterval } from "@/lib/types/status"
import { calculateTickCount, aggregateDataByInterval, calculateXAxisTicks } from "./utils"

interface MetricsRequestChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    completed: number
    request_too_many: number
    errors: number
  }>
  /** 日期范围 */
  dateRangeInMinutes?: number
}

export function MetricsRequestChart({
  loading = false,
  data,
  dateRangeInMinutes,
}: MetricsRequestChartProps) {
  // 每个图表独立管理自己的时间间隔
  const [timeInterval, setTimeInterval] = React.useState<TimeInterval>('5')

  // 根据 dateRangeInMinutes 的变化动态计算 count，最少为1
  const count = React.useMemo(
    () => calculateTickCount(dateRangeInMinutes, timeInterval),
    [dateRangeInMinutes, timeInterval]
  )

  // 按时间间隔聚合数据（时间桶分组）
  const displayData = React.useMemo(
    () => aggregateDataByInterval(
      data,
      Number(timeInterval),
      ['completed', 'request_too_many', 'errors']
    ),
    [data, timeInterval]
  )

  // 计算需要显示的 X 轴刻度点（按 count 控制间隔，从起始点开始，包含结尾）
  const xAxisTicks = React.useMemo(
    () => calculateXAxisTicks(displayData, count),
    [displayData, count]
  )
  
  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-medium">请求指标</CardTitle>
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
          <CardTitle className="text-base font-medium">请求指标</CardTitle>
          <TimeIntervalSelect
            value={timeInterval}
            onValueChange={setTimeInterval}
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className="h-[320px] w-full">
          <LineChart
            data={displayData}
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
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}`}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: "white",
                border: "1px solid #e5e7eb",
                borderRadius: "6px",
                fontSize: "12px",
              }}
            />
            <Legend
              wrapperStyle={{ fontSize: "12px" }}
              iconType="circle"
            />
            <Line
              type="monotone"
              dataKey="completed"
              stroke="#3b82f6"
              name="实时请求数"
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="request_too_many"
              stroke="#f59e0b"
              name="429限流数"
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="errors"
              stroke="#ef4444"
              name="500错误数"
              dot={false}
            />
          </LineChart>
        </div>
      </CardContent>
    </Card>
  )
}
