"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"
import { TimeIntervalSelect } from "./TimeIntervalSelect"
import type { TimeInterval } from "@/lib/types/status"
import { calculateTickCount, aggregateDataByInterval, calculateXAxisTicks } from "./utils"

interface MetricsTokenChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    input_token: number
    output_token: number
    token: number
  }>
  /** 日期范围（分钟数） */
  dateRangeInMinutes?: number
}

export function MetricsTokenChart({
  loading = false,
  data,
  dateRangeInMinutes,
}: MetricsTokenChartProps) {
  // 每个图表独立管理自己的时间间隔
  const [timeInterval, setTimeInterval] = React.useState<TimeInterval>('5')

  // 根据 dateRangeInMinutes 的变化动态计算 count，最少为1
  const count = React.useMemo(
    () => calculateTickCount(dateRangeInMinutes, timeInterval),
    [dateRangeInMinutes, timeInterval]
  )

  // 按时间间隔聚合数据（时间桶分组）
  const formattedData = React.useMemo(
    () => aggregateDataByInterval(
      data,
      Number(timeInterval),
      ['input_token', 'output_token', 'token']
    ),
    [data, timeInterval]
  )

  // 计算需要显示的 X 轴刻度点（按 count 控制间隔，从起始点开始，包含结尾）
  const xAxisTicks = React.useMemo(
    () => calculateXAxisTicks(formattedData, count),
    [formattedData, count]
  )

  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-medium">Token 使用量</CardTitle>
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
          <CardTitle className="text-base font-medium">Token 使用量</CardTitle>
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
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
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
              dataKey="input_token"
              stroke="#8b5cf6"
              name="输入 Token"
              dot={false}
            />
            <Line
              type="monotone"
              dataKey="output_token"
              stroke="#06b6d4"
              name="输出 Token"
              dot={false}
            />
          </LineChart>
        </div>
      </CardContent>
    </Card>
  )
}
