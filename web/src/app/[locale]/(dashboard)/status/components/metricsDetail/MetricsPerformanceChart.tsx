"use client"

import * as React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Line, LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend } from "recharts"
import { Skeleton } from "@/components/common/skeleton"

interface MetricsPerformanceChartProps {
  /** 加载状态 */
  loading?: boolean
  /** 图表数据 */
  data?: Array<{
    time: string
    ttft: number
    ttlt: number
  }>
  /** TTFT 单位 */
  ttftUnit?: "ms"
  /** TTLT 单位 */
  ttltUnit?: "ms" | "s"
}

export function MetricsPerformanceChart({
  loading = false,
  data,
  ttftUnit = "ms",
  ttltUnit = "s",
}: MetricsPerformanceChartProps) {
  const formattedData = React.useMemo(() => data || [], [data])

  if (loading) {
    return (
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-base font-medium">平均性能指标</CardTitle>
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
        <CardTitle className="text-base font-medium">平均性能指标</CardTitle>
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
              yAxisId="left"
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}${ttftUnit}`}
            />
            <YAxis
              yAxisId="right"
              orientation="right"
              stroke="#6b7280"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}${ttltUnit}`}
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
                if (name === "平均 TTFT（首包响应时间）") return `${value}${ttftUnit}`
                if (name === "平均 TTLT（总响应时间）") return `${value}${ttltUnit}`
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
              name="平均 TTFT（首包响应时间）"
              dot={false}
            />
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="ttlt"
              stroke="#ec4899"
              name="平均 TTLT（总响应时间）"
              dot={false}
            />
          </LineChart>
        </div>
      </CardContent>
    </Card>
  )
}
