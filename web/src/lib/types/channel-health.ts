export const PROMETHEUS_ENDPOINT_LABEL = "api_endpoint" as const
export const LLM_ENDPOINTS = [
  "/v1/chat/completions",
  "/v1/messages",
  "/v1/responses",
] as const

export type MetricAvailability = "available" | "unreleased" | "no_data" | "loading"
export type SampleStatus = "success" | "error_429" | "error_5xx" | "error_4xx" | "route_failure" | "no_traffic"

export type ChannelHealthTimePreset = {
  label: string
  minutes: number
  stepSeconds: number
  rateWindow: string
}

export type ChannelHealthGroupBy = "model" | "supplier" | "forward_host" | "channel"

export type ChannelHealthFilters = {
  groupBy: ChannelHealthGroupBy
  model: string
  supplier: string
  channelCode: string
  timePreset: ChannelHealthTimePreset
}

export type MetricValue<T> = {
  status: MetricAvailability
  value?: T
  reason?: string
}

export type SampleCell = {
  timestamp: number
  label: string
  status: SampleStatus
  total: number
  success: number
  error429: number
  error5xx: number
  error4xx: number
}

export type ChannelHealthRow = {
  id: string
  model: string
  supplier: string
  channelCode: string
  forwardHost: string
  deployName: string
  requestCount: number
  errorCount: number
  channelIssueCount: number
  clientErrorCount: number
  rpm: number
  failureRate: number
  clientErrorRate: number
  rate429Count: number
  error5xxCount: number
  outputTokenTpm: number
  ttftAvgMs: MetricValue<number>
  ttftP95Ms: MetricValue<number>
  tpsAvg: MetricValue<number>
  tpsP10: MetricValue<number>
  inputTokenBuckets: BucketBreakdownRow[]
  outputTokenBuckets: BucketBreakdownRow[]
  ttftDistribution: BucketDistributionRow[]
  tpsDistribution: BucketDistributionRow[]
  performanceTrend: PerformanceTrendPoint[]
  samples: SampleCell[]
}

export type ChannelHealthGroup = {
  id: string
  groupBy: ChannelHealthGroupBy
  label: string
  subtitle: string
  requestCount: number
  errorCount: number
  channelIssueCount: number
  clientErrorCount: number
  rpm: number
  failureRate: number
  clientErrorRate: number
  rate429Count: number
  error5xxCount: number
  outputTokenTpm: number
  ttftAvgMs: MetricValue<number>
  ttftP95Ms: MetricValue<number>
  tpsAvg: MetricValue<number>
  tpsP10: MetricValue<number>
  samples: SampleCell[]
  channels: ChannelHealthRow[]
}

export type BucketBreakdownRow = {
  bucket: string
  sampleCount: number
  coverage: number
  confidence: "none" | "low" | "medium" | "high"
  avg: MetricValue<number>
  tail: MetricValue<number>
  delta: MetricValue<number>
  ratio?: MetricValue<number>
}

export type PerformanceTrendPoint = {
  timestamp: number
  label: string
  ttftAvgMs: number | null
  ttftP95Ms: number | null
  tpsAvg: number | null
  tpsP10: number | null
}

export type BucketDistributionCell = {
  label: string
  count: number
  ratio: number
}

export type BucketDistributionRow = {
  bucket: string
  sampleCount: number
  coverage: number
  confidence: "none" | "low" | "medium" | "high"
  cells: BucketDistributionCell[]
}

export type ChannelHealthDetailData = {
  ttftAvg: Map<string, number>
  ttftP95: Map<string, number>
  tpsAvg: Map<string, number>
  tpsP10: Map<string, number>
  inputBucketAvg: Map<string, Map<string, number>>
  inputBucketP95: Map<string, Map<string, number>>
  inputBucketCount: Map<string, Map<string, number>>
  outputBucketAvg: Map<string, Map<string, number>>
  outputBucketP10: Map<string, Map<string, number>>
  outputBucketCount: Map<string, Map<string, number>>
  inputTtftDistribution: Map<string, BucketDistributionRow[]>
  outputTpsDistribution: Map<string, BucketDistributionRow[]>
  performanceTrend: PerformanceTrendPoint[]
}

export type RecentAnomaly = {
  time: string
  channelCode: string
  model: string
  supplier: string
  message: string
  severity: MetricValue<"P1" | "P2" | "P3">
}

export type ChannelHealthSummary = {
  requestCount: number
  rpm: number
  totalTokens: number
  tokenTpm: number
  outputTokens: number
  outputTokenTpm: number
}

export type ChannelHealthDashboardData = {
  generatedAt: string
  summary: ChannelHealthSummary
  groups: ChannelHealthGroup[]
  rows: ChannelHealthRow[]
  recentAnomalies: RecentAnomaly[]
}

export const CHANNEL_HEALTH_TIME_PRESETS: ChannelHealthTimePreset[] = [
  { label: "最近 5 分钟", minutes: 5, stepSeconds: 5, rateWindow: "1m" },
  { label: "最近 10 分钟", minutes: 10, stepSeconds: 10, rateWindow: "2m" },
  { label: "最近 15 分钟", minutes: 15, stepSeconds: 15, rateWindow: "5m" },
  { label: "最近 30 分钟", minutes: 30, stepSeconds: 30, rateWindow: "5m" },
  { label: "最近 1 小时", minutes: 60, stepSeconds: 60, rateWindow: "5m" },
  { label: "最近 3 小时", minutes: 180, stepSeconds: 180, rateWindow: "15m" },
  { label: "最近 6 小时", minutes: 360, stepSeconds: 360, rateWindow: "15m" },
  { label: "最近 24 小时", minutes: 1440, stepSeconds: 1440, rateWindow: "30m" },
]
