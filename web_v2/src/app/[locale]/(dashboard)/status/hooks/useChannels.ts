"use client"

import * as React from "react"
import type {
  ChannelInfo,
  MetricsAtTime,
  MetricsData,
  UseChannelsParams,
  UseChannelsResult,
} from "@/lib/types/status"
import axios from "axios"

/**
 * 获取渠道列表的自定义 Hook
 * @param params 查询参数
 * @returns 渠道列表、加载状态和错误信息
 */
export function useChannels({
  model,
  endpoint,
  channelCode,
  start,
  end,
}: UseChannelsParams): UseChannelsResult {
  const [metricsData, setMetricsData] = React.useState<MetricsAtTime[]>([])
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  React.useEffect(() => {
    // 只有当所有必需参数都存在时才发起请求
    if (!model || !endpoint || !start || !end) {
      setMetricsData([])
      return
    }

    const fetchChannels = async (isBackgroundRefresh = false) => {
      // 只在非后台刷新时显示 loading
      if (!isBackgroundRefresh) {
        setLoading(true)
      }
      setError(null)
      try {
        // const data = await getChannelsForSelection(model, endpoint, start, end)
        const data = await axios.get(`/api/metrics?model=${model}&endpoint=${endpoint}&start=${start}&end=${end}`)
        if(data.status === 200){    
        setMetricsData(data.data)
        } else {
          setError("获取渠道列表失败")
          setMetricsData([])
        }
      } catch (err) {
        console.error("Failed to fetch channels:", err)
        setError("获取渠道列表失败")
        setMetricsData([])
      } finally {
        // 只在非后台刷新时关闭 loading
        if (!isBackgroundRefresh) {
          setLoading(false)
        }
      }
    }

    // 首次加载
    fetchChannels(false)

    // 添加定时刷新，每15秒自动更新数据（后台静默刷新）
    const intervalId = setInterval(() => {
      fetchChannels(true)
    }, 15000) // 15秒 = 15000毫秒

    return () => {
      clearInterval(intervalId) // 清理定时器
      setMetricsData([])
    }
  }, [model, endpoint, start, end])

  // 从 MetricsAtTime[] 中提取唯一的渠道列表
  const channels = React.useMemo(() => {
    const channelMap = new Map<string, ChannelInfo>()

    metricsData.forEach((timePoint) => {
      // 遍历每个时间点的渠道数据（数字索引）
      Object.keys(timePoint).forEach((key) => {
        if (!isNaN(Number(key))) {
          const channelData = timePoint[Number(key)]
          if (!channelMap.has(channelData.channel_code)) {
            channelMap.set(channelData.channel_code, {
              channelCode: channelData.channel_code,
              channel_code: channelData.channel_code,
              endpoint: channelData.endpoint,
              entity_code: channelData.entity_code,
            })
          }
        }
      })
    })

    return Array.from(channelMap.values())
  }, [metricsData])

  // 计算汇总数据
  const summary = React.useMemo(() => {
    if (metricsData.length === 0) {
      return {
        totalRequests: 0,
        totalRequestTooMany: 0,
        totalErrors: 0,
        avgTtft: 0,
      }
    }

    let totalCompleted = 0
    let totalErrors = 0
    let totalRequestTooMany = 0
    const ttftValues: number[] = []

    metricsData.forEach((timePoint) => {
      if (!channelCode || channelCode === "") {
        // 全部渠道：使用汇总数据
        totalCompleted += timePoint.metrics.completed || 0
        totalErrors += timePoint.metrics.errors || 0
        totalRequestTooMany += timePoint.metrics.request_too_many || 0
        if (timePoint.metrics.ttft) ttftValues.push(timePoint.metrics.ttft)
        if (timePoint.metrics.ttlt) ttftValues.push(timePoint.metrics.ttlt)
      } else {
        if(timePoint?.channel_code === channelCode){
          totalCompleted += timePoint.metrics.completed || 0
          totalErrors += timePoint.metrics.errors || 0
          totalRequestTooMany += timePoint.metrics.request_too_many || 0
          if (timePoint.metrics.ttft) ttftValues.push(timePoint.metrics.ttft)
          if (timePoint.metrics.ttlt) ttftValues.push(timePoint.metrics.ttlt)
        }
      }
    })
    const avgTtft =
      ttftValues.length > 0
        ? Math.round(ttftValues.reduce((sum, v) => sum + v, 0) / ttftValues.length)
        : 0

    return {
      totalRequests: totalCompleted,
      totalRequestTooMany,
      totalErrors,
      avgTtft,
    }
  }, [metricsData, channelCode])

  // 转换为图表数据
  const chartData = React.useMemo(() => {
    if (metricsData.length === 0) return undefined

    return metricsData.map((timePoint) => {
      // 格式化时间：将 "202602021759" 转换为 "17:59"
      const formatTime = (timeStr: string) => {
        if (timeStr.length !== 12) return timeStr
        const hour = timeStr.slice(8, 10)
        const minute = timeStr.slice(10, 12)
        return `${hour}:${minute}`
      }

      if (!channelCode || channelCode === "") {
        // 全部渠道：使用汇总数据
        return {
          time: formatTime(timePoint.time),
          completed: timePoint.metrics.completed || 0,
          request_too_many: timePoint.metrics.request_too_many || 0,
          errors: timePoint.metrics.errors || 0,
          input_token: timePoint.metrics.input_token || 0,
          output_token: timePoint.metrics.output_token || 0,
          token: timePoint.metrics.token || 0,
          ttft: timePoint.metrics.ttft || 0,
          ttlt: timePoint.metrics.ttlt || 0,
        }
      } else {
        // 单个渠道：查找指定渠道数据
        let channelMetrics: MetricsData | undefined = undefined
        if(timePoint?.channel_code === channelCode){
          channelMetrics = timePoint.metrics
        }

        return {
          time: formatTime(timePoint.time),
          completed: channelMetrics?.completed || 0,
          request_too_many: channelMetrics?.request_too_many || 0,
          errors: channelMetrics?.errors || 0,
          input_token: channelMetrics?.input_token || 0,
          output_token: channelMetrics?.output_token || 0,
          token: channelMetrics?.token || 0,
          ttft: channelMetrics?.ttft || 0,
          ttlt: channelMetrics?.ttlt || 0,
        }
      }
    })
  }, [metricsData, channelCode])
  return { channels, loading, error, summary, chartData,metricsData }
}
