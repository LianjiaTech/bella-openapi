"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"

interface MetricsRequestChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    completed: number
    success_estimated?: number
    request_too_many: number
    errors: number
  }>
}

export function MetricsRequestChart({
  loading = false,
  data,
}: MetricsRequestChartProps) {
  const displayData = React.useMemo(() => data || [], [data])

  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-base font-medium">请求指标</CardTitle>
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
        <CardTitle className="text-base font-medium">请求指标</CardTitle>
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
              dataKey="success_estimated"
              stroke="#10b981"
              name="成功数估算"
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
