"use client"

import * as React from "react"
import type {
  ChannelMetricsAtTime,
  ChannelInfo,
  ChannelSummaryData,
  MetricsAtTime,
  MetricsData,
  SummaryData,
  UseChannelsParams,
  UseChannelsResult,
} from "@/lib/types/status"
import axios from "axios"

const RATE_LIMIT_UNSTABLE_THRESHOLD = 0.05
const ERROR_UNSTABLE_THRESHOLD = 0.02
const TTFT_UNSTABLE_THRESHOLD_MS = 3000
const TTLT_UNSTABLE_THRESHOLD_SECONDS = 60
const TTLT_UNSTABLE_THRESHOLD_MS = 60000

const formatRate = (value: number): string => `${(value * 100).toFixed(1)}%`

const numberMetric = (metrics: MetricsData | undefined, key: keyof MetricsData): number => {
  const value = metrics?.[key]
  return typeof value === "number" && Number.isFinite(value) ? value : 0
}

const emptySummary = (ttltUnit: "ms" | "s" = "s"): SummaryData => ({
  totalRequests: 0,
  totalRequestTooMany: 0,
  totalErrors: 0,
  avgTtft: 0,
  avgTtlt: 0,
  successEstimated: 0,
  successRateEstimated: 0,
  rateLimitRate: 0,
  errorRate: 0,
  totalToken: 0,
  availableChannels: 0,
  unavailableChannels: 0,
  totalChannels: 0,
  healthStatus: "no_data",
  healthLabel: "无数据",
  healthDescription: "当前时间范围内没有请求样本，暂无法判断模型可用性。",
  ttftUnit: "ms",
  ttltUnit,
})

const resolveTtltUnit = (endpoint?: string): "ms" | "s" => {
  const value = (endpoint || "").toLowerCase()
  return value.includes("stream") || value.includes("realtime") ? "ms" : "s"
}

const normalizeRecords = (metricsData: MetricsAtTime[]): ChannelMetricsAtTime[] => {
  const records: ChannelMetricsAtTime[] = []

  metricsData.forEach((timePoint) => {
    const hasNestedRecords = Object.keys(timePoint).some((key) => !Number.isNaN(Number(key)))

    if (!hasNestedRecords && timePoint.metrics && timePoint.channel_code) {
      records.push({
        time: timePoint.time,
        endpoint: timePoint.endpoint,
        entity_code: timePoint.entity_code || "",
        channel_code: timePoint.channel_code,
        channelCode: timePoint.channel_code,
        metrics: timePoint.metrics,
      })
    }

    Object.keys(timePoint).forEach((key) => {
      if (!Number.isNaN(Number(key))) {
        const record = timePoint[Number(key)]
        if (record?.metrics && record.channel_code) {
          records.push(record)
        }
      }
    })
  })

  return records
}

const latestStatusByChannel = (records: ChannelMetricsAtTime[]): Map<string, 0 | 1> => {
  const latest = new Map<string, { time: string; status: 0 | 1 }>()

  records.forEach((record) => {
    const status = record.metrics.status === 0 ? 0 : 1
    const current = latest.get(record.channel_code)
    if (!current || record.time >= current.time) {
      latest.set(record.channel_code, { time: record.time, status })
    }
  })

  return new Map(Array.from(latest.entries()).map(([channel, value]) => [channel, value.status]))
}

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
  const requestVersionRef = React.useRef(0)

  React.useEffect(() => {
    // 只有当所有必需参数都存在时才发起请求
    if (!model || !endpoint || !start || !end) {
      setMetricsData([])
      setLoading(false)
      setError(null)
      return
    }

    requestVersionRef.current += 1
    const requestVersion = requestVersionRef.current
    let disposed = false
    setMetricsData([])
    setLoading(true)
    setError(null)

    const fetchChannels = async (isBackgroundRefresh = false) => {
      // 只在非后台刷新时显示 loading
      try {
        // const data = await getChannelsForSelection(model, endpoint, start, end)
        const data = await axios.get(`/api/metrics?model=${model}&endpoint=${endpoint}&start=${start}&end=${end}`)
        if (disposed || requestVersion !== requestVersionRef.current) {
          return
        }
        if(data.status === 200){
          setMetricsData(data.data)
        } else {
          setError("获取渠道列表失败")
          setMetricsData([])
        }
      } catch (err) {
        if (disposed || requestVersion !== requestVersionRef.current) {
          return
        }
        console.error("Failed to fetch channels:", err)
        setError("获取渠道列表失败")
        setMetricsData([])
      } finally {
        // 只在非后台刷新时关闭 loading
        if (!isBackgroundRefresh && !disposed && requestVersion === requestVersionRef.current) {
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
      disposed = true
      clearInterval(intervalId) // 清理定时器
    }
  }, [model, endpoint, start, end])

  const records = React.useMemo(() => normalizeRecords(metricsData), [metricsData])
  const filteredRecords = React.useMemo(
    () => channelCode ? records.filter((record) => record.channel_code === channelCode) : records,
    [records, channelCode]
  )
  const ttltUnit = React.useMemo(() => resolveTtltUnit(endpoint), [endpoint])

  // 从 MetricsAtTime[] 中提取唯一的渠道列表
  const channels = React.useMemo(() => {
    const channelMap = new Map<string, ChannelInfo>()

    records.forEach((record) => {
      if (!channelMap.has(record.channel_code)) {
        channelMap.set(record.channel_code, {
          channelCode: record.channel_code,
          channel_code: record.channel_code,
          endpoint: record.endpoint,
          entity_code: record.entity_code,
        })
      }
    })

    return Array.from(channelMap.values())
  }, [records])

  // 计算汇总数据
  const summary = React.useMemo<SummaryData>(() => {
    if (filteredRecords.length === 0) {
      return emptySummary(ttltUnit)
    }

    let totalCompleted = 0
    let totalErrors = 0
    let totalRequestTooMany = 0
    let totalTtft = 0
    let totalTtlt = 0
    let totalInputToken = 0
    let totalOutputToken = 0
    let totalEmbeddingToken = 0

    filteredRecords.forEach((record) => {
      totalCompleted += numberMetric(record.metrics, "completed")
      totalErrors += numberMetric(record.metrics, "errors")
      totalRequestTooMany += numberMetric(record.metrics, "request_too_many")
      totalTtft += numberMetric(record.metrics, "ttft")
      totalTtlt += numberMetric(record.metrics, "ttlt")
      totalInputToken += numberMetric(record.metrics, "input_token")
      totalOutputToken += numberMetric(record.metrics, "output_token")
      totalEmbeddingToken += numberMetric(record.metrics, "token")
    })

    const avgTtft = totalCompleted > 0
      ? Math.round(totalTtft / totalCompleted)
      : 0
    const avgTtlt = totalCompleted > 0
      ? Math.round((totalTtlt / totalCompleted) * 10) / 10
      : 0
    const successEstimated = Math.max(totalCompleted - totalErrors - totalRequestTooMany, 0)
    const successRateEstimated = totalCompleted > 0 ? successEstimated / totalCompleted : 0
    const rateLimitRate = totalCompleted > 0 ? totalRequestTooMany / totalCompleted : 0
    const errorRate = totalCompleted > 0 ? totalErrors / totalCompleted : 0
    const totalToken = totalInputToken + totalOutputToken + totalEmbeddingToken
    const statusMap = latestStatusByChannel(filteredRecords)
    const totalChannels = statusMap.size
    const availableChannels = Array.from(statusMap.values()).filter((status) => status === 1).length
    const unavailableChannels = Math.max(totalChannels - availableChannels, 0)

    const unstableByRate =
      rateLimitRate >= RATE_LIMIT_UNSTABLE_THRESHOLD ||
      errorRate >= ERROR_UNSTABLE_THRESHOLD
    const unstableByLatency =
      avgTtft >= TTFT_UNSTABLE_THRESHOLD_MS ||
      avgTtlt >= (ttltUnit === "ms" ? TTLT_UNSTABLE_THRESHOLD_MS : TTLT_UNSTABLE_THRESHOLD_SECONDS)
    const unstableReasons = []
    if (rateLimitRate >= RATE_LIMIT_UNSTABLE_THRESHOLD) {
      unstableReasons.push(`429 比例 ${formatRate(rateLimitRate)} >= ${formatRate(RATE_LIMIT_UNSTABLE_THRESHOLD)}`)
    }
    if (errorRate >= ERROR_UNSTABLE_THRESHOLD) {
      unstableReasons.push(`5xx 比例 ${formatRate(errorRate)} >= ${formatRate(ERROR_UNSTABLE_THRESHOLD)}`)
    }
    if (avgTtft >= TTFT_UNSTABLE_THRESHOLD_MS) {
      unstableReasons.push(`平均 TTFT ${avgTtft}ms >= ${TTFT_UNSTABLE_THRESHOLD_MS}ms`)
    }
    const ttltThreshold = ttltUnit === "ms" ? TTLT_UNSTABLE_THRESHOLD_MS : TTLT_UNSTABLE_THRESHOLD_SECONDS
    if (avgTtlt >= ttltThreshold) {
      unstableReasons.push(`平均 TTLT ${avgTtlt}${ttltUnit} >= ${ttltThreshold}${ttltUnit}`)
    }
    const healthStatus: SummaryData["healthStatus"] = totalCompleted === 0
      ? "no_data"
      : totalChannels > 0 && availableChannels === 0
      ? "unavailable"
      : unstableByRate || unstableByLatency
      ? "unstable"
      : "available"

    const healthText = {
      available: {
        label: "可用",
        description: `当前有样本渠道 ${availableChannels}/${totalChannels || 0} 可用，429、5xx 和平均延迟处于提示阈值内。`,
      },
      unstable: {
        label: "不稳定",
        description: unstableReasons.length > 0
          ? `当前仍有可用渠道，但${unstableReasons.join("、")}。`
          : "当前仍有可用渠道，但 429、5xx 或延迟指标已超过提示阈值。",
      },
      unavailable: {
        label: "不可用",
        description: "当前时间范围内出现请求，但最新观测到的渠道均处于不可用状态。",
      },
      no_data: {
        label: "无数据",
        description: "当前时间范围内没有请求样本，暂无法判断模型可用性。",
      },
    }[healthStatus]

    return {
      totalRequests: totalCompleted,
      totalRequestTooMany,
      totalErrors,
      avgTtft,
      avgTtlt,
      successEstimated,
      successRateEstimated,
      rateLimitRate,
      errorRate,
      totalToken,
      availableChannels,
      unavailableChannels,
      totalChannels,
      healthStatus,
      healthLabel: healthText.label,
      healthDescription: healthText.description,
      ttftUnit: "ms",
      ttltUnit,
    }
  }, [filteredRecords, ttltUnit])

  const channelSummaries = React.useMemo<ChannelSummaryData[]>(() => {
    const grouped = new Map<string, {
      completed: number
      errors: number
      requestTooMany: number
      ttft: number
      ttlt: number
      inputToken: number
      outputToken: number
      embeddingToken: number
      latestTime: string
      status: 0 | 1
    }>()

    filteredRecords.forEach((record) => {
      const current = grouped.get(record.channel_code) || {
        completed: 0,
        errors: 0,
        requestTooMany: 0,
        ttft: 0,
        ttlt: 0,
        inputToken: 0,
        outputToken: 0,
        embeddingToken: 0,
        latestTime: "",
        status: 1 as 0 | 1,
      }

      current.completed += numberMetric(record.metrics, "completed")
      current.errors += numberMetric(record.metrics, "errors")
      current.requestTooMany += numberMetric(record.metrics, "request_too_many")
      current.ttft += numberMetric(record.metrics, "ttft")
      current.ttlt += numberMetric(record.metrics, "ttlt")
      current.inputToken += numberMetric(record.metrics, "input_token")
      current.outputToken += numberMetric(record.metrics, "output_token")
      current.embeddingToken += numberMetric(record.metrics, "token")
      if (!current.latestTime || record.time >= current.latestTime) {
        current.latestTime = record.time
        current.status = record.metrics.status === 0 ? 0 : 1
      }

      grouped.set(record.channel_code, current)
    })

    return Array.from(grouped.entries()).map(([channel, value]) => ({
      channelCode: channel,
      status: value.status,
      completed: value.completed,
      errors: value.errors,
      requestTooMany: value.requestTooMany,
      successRateEstimated: value.completed > 0
        ? Math.max(value.completed - value.errors - value.requestTooMany, 0) / value.completed
        : 0,
      avgTtft: value.completed > 0 ? Math.round(value.ttft / value.completed) : 0,
      avgTtlt: value.completed > 0 ? Math.round((value.ttlt / value.completed) * 10) / 10 : 0,
      totalToken: value.inputToken + value.outputToken + value.embeddingToken,
    })).sort((a, b) => b.completed - a.completed)
  }, [filteredRecords])

  // 转换为图表数据
  const chartData = React.useMemo(() => {
    if (filteredRecords.length === 0) return undefined

    const formatTime = (timeStr: string) => {
      if (timeStr.length !== 12) return timeStr
      const hour = timeStr.slice(8, 10)
      const minute = timeStr.slice(10, 12)
      return `${hour}:${minute}`
    }

    const grouped = new Map<string, {
      completed: number
      requestTooMany: number
      errors: number
      inputToken: number
      outputToken: number
      embeddingToken: number
      ttft: number
      ttlt: number
    }>()

    filteredRecords.forEach((record) => {
      const current = grouped.get(record.time) || {
        completed: 0,
        requestTooMany: 0,
        errors: 0,
        inputToken: 0,
        outputToken: 0,
        embeddingToken: 0,
        ttft: 0,
        ttlt: 0,
      }

      current.completed += numberMetric(record.metrics, "completed")
      current.requestTooMany += numberMetric(record.metrics, "request_too_many")
      current.errors += numberMetric(record.metrics, "errors")
      current.inputToken += numberMetric(record.metrics, "input_token")
      current.outputToken += numberMetric(record.metrics, "output_token")
      current.embeddingToken += numberMetric(record.metrics, "token")
      current.ttft += numberMetric(record.metrics, "ttft")
      current.ttlt += numberMetric(record.metrics, "ttlt")
      grouped.set(record.time, current)
    })

    return Array.from(grouped.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([time, value]) => {
      const avgTtft = value.completed > 0 ? Math.round(value.ttft / value.completed) : 0
      const avgTtlt = value.completed > 0 ? Math.round((value.ttlt / value.completed) * 10) / 10 : 0
      const totalToken = value.inputToken + value.outputToken + value.embeddingToken

      return {
        time: formatTime(time),
        completed: value.completed,
        request_too_many: value.requestTooMany,
        errors: value.errors,
        input_token: value.inputToken,
        output_token: value.outputToken,
        token: value.embeddingToken,
        total_token: totalToken,
        ttft: avgTtft,
        ttlt: avgTtlt,
      }
    })
  }, [filteredRecords])
  return { channels, loading, error, summary, chartData, metricsData, channelSummaries }
}
