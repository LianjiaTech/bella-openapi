"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"

interface MetricsTokenChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    input_token: number
    output_token: number
    token: number
    total_token?: number
  }>
}

export function MetricsTokenChart({
  loading = false,
  data,
}: MetricsTokenChartProps) {
  const formattedData = React.useMemo(() => data || [], [data])

  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-base font-medium">Token 使用量</CardTitle>
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
        <CardTitle className="text-base font-medium">Token 使用量</CardTitle>
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
            <Line
              type="monotone"
              dataKey="total_token"
              stroke="#10b981"
              name="总 Token"
              dot={false}
            />
          </LineChart>
        </div>
      </CardContent>
    </Card>
  )
}
