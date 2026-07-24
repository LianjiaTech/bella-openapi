import {
  CHANNEL_HEALTH_TIME_PRESETS,
  LLM_ENDPOINTS,
  PROMETHEUS_ENDPOINT_LABEL,
  type BucketDistributionRow,
  type BucketBreakdownRow,
  type ChannelHealthDashboardData,
  type ChannelHealthDetailData,
  type ChannelHealthFilters,
  type ChannelHealthGroup,
  type ChannelHealthGroupBy,
  type ChannelHealthRow,
  type MetricValue,
  type PerformanceTrendPoint,
  type RecentAnomaly,
  type SampleCell,
  type SampleStatus,
} from "@/lib/types/channel-health"
import {
  getDefaultPrometheusBaseUrl,
  queryRange,
  queryVector,
  sampleValue,
  sumVector,
  type PrometheusMatrixResult,
  type PrometheusVectorResult,
} from "@/lib/api/prometheus"

const UNRELEASED_REASON = "当前 Prometheus 指标未发布"

const INPUT_BUCKETS = ["0_1k", "1k_8k", "8k_32k", "32k_plus"]
const OUTPUT_BUCKETS = ["0_512", "512_2k", "2k_8k", "8k_plus"]
const TTFT_DISTRIBUTION_BOUNDARIES_MS = [500, 2000, 15000, 30000, 60000]
const TPS_DISTRIBUTION_BOUNDARIES = [5, 10, 20, 40, 80, 160, 320, 640, 1000]
const SYSTEM_TTFT_METRIC = "bella_system_ttft_milliseconds"
const SYSTEM_TTFT_BUCKET_METRIC = `${SYSTEM_TTFT_METRIC}_bucket`
const SYSTEM_TTFT_COUNT_METRIC = `${SYSTEM_TTFT_METRIC}_count`
const CHAT_COMPLETION_TPS_BUCKET_METRIC = "bella_chat_completion_tps_tokens_per_second_bucket"
const CHAT_COMPLETION_TPS_COUNT_METRIC = "bella_chat_completion_tps_tokens_per_second_count"
const GATEWAY_CHANNEL_CODE = "none"
const GATEWAY_SUPPLIER_LABEL = "AIT网关"
const GATEWAY_CHANNEL_LABEL = "AIT网关拦截"
const GATEWAY_FORWARD_HOST_LABEL = "网关侧"
const GATEWAY_DEPLOY_NAME_LABEL = "网关侧"
const CHANNEL_GROUP_LABELS = "model, supplier, channel_code, forward_host, deploy_name"

const unavailableMetric = <T>(reason = UNRELEASED_REASON): MetricValue<T> => ({
  status: "unreleased",
  reason,
})

const availableMetric = <T>(value: T): MetricValue<T> => ({
  status: "available",
  value,
})

const noDataMetric = <T>(reason = "当前时间范围无样本"): MetricValue<T> => ({
  status: "no_data",
  reason,
})

const loadingMetric = <T>(): MetricValue<T> => ({
  status: "loading",
})

const labelValue = (value: string) => value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")

const regexLabelValue = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/"/g, "\\\"")

const llmEndpointMatcher = () =>
  `${PROMETHEUS_ENDPOINT_LABEL}=~"${LLM_ENDPOINTS.map((endpoint) => regexLabelValue(endpoint)).join("|")}"`

const requestMatcher = (filters: ChannelHealthFilters, extraLabels: string[] = []) => {
  const labels = [llmEndpointMatcher(), ...extraLabels]
  if (filters.model.trim()) {
    labels.push(`model="${labelValue(filters.model.trim())}"`)
  }
  if (filters.supplier.trim()) {
    labels.push(`supplier="${labelValue(filters.supplier.trim())}"`)
  }
  if (filters.channelCode.trim()) {
    const channelCode = filters.channelCode.trim() === GATEWAY_CHANNEL_LABEL ? GATEWAY_CHANNEL_CODE : filters.channelCode.trim()
    labels.push(`channel_code="${labelValue(channelCode)}"`)
  }
  return `{${labels.join(",")}}`
}

const channelKey = (metric: Record<string, string>) =>
  `${metric.model || "-"}|${metric.supplier || "-"}|${metric.channel_code || "-"}|${metric.forward_host || "-"}|${metric.deploy_name || "-"}`

const resourceGroupLabel = (forwardHost: string, deployName: string) =>
  deployName && deployName !== "-" ? `${deployName} @ ${forwardHost || "-"}` : forwardHost || "-"

const formatSampleLabel = (timestamp: number) =>
  new Date(timestamp * 1000).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })

const emptySample = (timestamp: number, label = formatSampleLabel(timestamp)): SampleCell => ({
  timestamp,
  label,
  status: "no_traffic",
  total: 0,
  success: 0,
  error429: 0,
  error5xx: 0,
  error4xx: 0,
})

const resolveSampleStatus = (cell: SampleCell): SampleStatus => {
  if (cell.total <= 0) {
    return "no_traffic"
  }
  if (cell.error5xx > 0) {
    return "error_5xx"
  }
  if (cell.error429 > 0) {
    return "error_429"
  }
  if (cell.error4xx > 0) {
    return "error_4xx"
  }
  return "success"
}

function readQuantileByChannel(result: PrometheusVectorResult[] = []) {
  const values = new Map<string, number>()
  result.forEach((item) => values.set(channelKey(item.metric), sampleValue(item.value?.[1])))
  return values
}

function readBucketQuantilesByChannel(result: PrometheusVectorResult[] = [], bucketLabel: "input_token_bucket" | "output_token_bucket") {
  const values = new Map<string, Map<string, number>>()
  result.forEach((item) => {
    const key = channelKey(item.metric)
    const bucket = item.metric[bucketLabel] || "-"
    const current = values.get(key) || new Map<string, number>()
    current.set(bucket, sampleValue(item.value?.[1]))
    values.set(key, current)
  })
  return values
}

function filterVectorByKind(result: PrometheusVectorResult[], kind: string) {
  return result.filter((item) => item.metric.metric_kind === kind)
}

function filterMatrixByKind(result: PrometheusMatrixResult[], kind: string) {
  return result.filter((series) => series.metric.metric_kind === kind)
}

const queryWithKind = (query: string, kind: string) =>
  `label_replace((${query}), "metric_kind", "${kind}", "__name__", ".*")`

function readRangeValues(result: PrometheusMatrixResult[] = []) {
  const values = new Map<number, number>()
  result.forEach((series) => {
    series.values.forEach(([timestamp, rawValue]) => {
      const value = sampleValue(rawValue)
      if (Number.isFinite(value)) {
        values.set(timestamp, value)
      }
    })
  })
  return values
}

function buildPerformanceTrend(
  start: number,
  end: number,
  stepSeconds: number,
  ttftAvgResult: PrometheusMatrixResult[],
  ttftP95Result: PrometheusMatrixResult[],
  tpsAvgResult: PrometheusMatrixResult[],
  tpsP10Result: PrometheusMatrixResult[]
): PerformanceTrendPoint[] {
  const ttftAvg = readRangeValues(ttftAvgResult)
  const ttftP95 = readRangeValues(ttftP95Result)
  const tpsAvg = readRangeValues(tpsAvgResult)
  const tpsP10 = readRangeValues(tpsP10Result)
  const timestamps = new Set<number>()

  for (let timestamp = start; timestamp <= end; timestamp += stepSeconds) {
    timestamps.add(timestamp)
  }
  ;[ttftAvg, ttftP95, tpsAvg, tpsP10].forEach((series) => {
    series.forEach((_, timestamp) => timestamps.add(timestamp))
  })

  return Array.from(timestamps)
    .sort((left, right) => left - right)
    .map((timestamp) => ({
      timestamp,
      label: new Date(timestamp * 1000).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }),
      ttftAvgMs: ttftAvg.has(timestamp) ? ttftAvg.get(timestamp)! : null,
      ttftP95Ms: ttftP95.has(timestamp) ? ttftP95.get(timestamp)! : null,
      tpsAvg: tpsAvg.has(timestamp) ? tpsAvg.get(timestamp)! : null,
      tpsP10: tpsP10.has(timestamp) ? tpsP10.get(timestamp)! : null,
    }))
}

function parseHistogramLe(value?: string) {
  if (!value) {
    return undefined
  }
  if (value === "+Inf") {
    return Number.POSITIVE_INFINITY
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function formatTtftDistributionBoundary(value: number) {
  if (value === 60000) {
    return "1min"
  }
  if (value >= 1000) {
    return `${Math.round(value / 1000)}s`
  }
  return `${Math.round(value)}ms`
}

function formatTpsDistributionBoundary(value: number) {
  return `${Math.round(value)}/s`
}

function buildDistributionBins(
  preferredBoundaries: number[],
  availableBoundaries: Set<number>,
  formatBoundary: (value: number) => string
) {
  const finiteBoundaries = preferredBoundaries.filter((boundary) => availableBoundaries.has(boundary))
  const fallbackBoundaries = Array.from(availableBoundaries)
    .filter((boundary) => Number.isFinite(boundary))
    .sort((left, right) => left - right)
  const boundaries = finiteBoundaries.length > 0 ? finiteBoundaries : fallbackBoundaries

  if (boundaries.length === 0) {
    return [{ label: "全部", lowerMs: 0, upperMs: Number.POSITIVE_INFINITY }]
  }

  return boundaries.map((upperMs, index) => {
    const lowerMs = index === 0 ? 0 : boundaries[index - 1]
    return {
      label: lowerMs === 0
        ? `<=${formatBoundary(upperMs)}`
        : `${formatBoundary(lowerMs)}-${formatBoundary(upperMs)}`,
      lowerMs,
      upperMs,
    }
  }).concat({
    label: `>${formatBoundary(boundaries[boundaries.length - 1])}`,
    lowerMs: boundaries[boundaries.length - 1],
    upperMs: Number.POSITIVE_INFINITY,
  })
}

function emptyDistributionRows(
  buckets: string[],
  preferredBoundaries: number[],
  defaultBoundaries: number[],
  formatBoundary: (value: number) => string
): BucketDistributionRow[] {
  const bins = buildDistributionBins(preferredBoundaries, new Set(defaultBoundaries), formatBoundary)
  return buckets.map((bucket) => ({
    bucket,
    sampleCount: 0,
    coverage: 0,
    confidence: "none" as const,
    cells: bins.map((bin) => ({
      label: bin.label,
      count: 0,
      ratio: 0,
    })),
  }))
}

const emptyTtftDistributionRows = () =>
  emptyDistributionRows(INPUT_BUCKETS, TTFT_DISTRIBUTION_BOUNDARIES_MS, [500, 2000, 15000, 30000], formatTtftDistributionBoundary)

const emptyTpsDistributionRows = () =>
  emptyDistributionRows(OUTPUT_BUCKETS, TPS_DISTRIBUTION_BOUNDARIES, [5, 10, 20, 40, 80], formatTpsDistributionBoundary)

function buildDistributionRows(
  buckets: string[],
  preferredBoundaries: number[],
  formatBoundary: (value: number) => string,
  bucketCumulativeCounts: Map<string, Map<number, number>>
): BucketDistributionRow[] {
  const availableBoundaries = new Set<number>()
  bucketCumulativeCounts.forEach((cumulativeCounts) => {
    cumulativeCounts.forEach((_, boundary) => {
      if (Number.isFinite(boundary)) {
        availableBoundaries.add(boundary)
      }
    })
  })
  const bins = buildDistributionBins(preferredBoundaries, availableBoundaries, formatBoundary)
  const bucketTotals = new Map<string, number>()
  buckets.forEach((bucket) => {
    const cumulativeCounts = bucketCumulativeCounts.get(bucket) || new Map<number, number>()
    const fallbackTotal = Math.max(0, ...Array.from(cumulativeCounts.values()))
    bucketTotals.set(bucket, cumulativeCounts.get(Number.POSITIVE_INFINITY) || fallbackTotal || 0)
  })
  const totalSamples = Array.from(bucketTotals.values()).reduce((sum, value) => sum + value, 0)

  return buckets.map((bucket) => {
    const cumulativeCounts = bucketCumulativeCounts.get(bucket) || new Map<number, number>()
    const sampleCount = bucketTotals.get(bucket) || 0
    const cells = bins.map((bin) => {
      const upperCount = bin.upperMs === Number.POSITIVE_INFINITY
        ? sampleCount
        : cumulativeCounts.get(bin.upperMs) || 0
      const lowerCount = bin.lowerMs > 0 ? cumulativeCounts.get(bin.lowerMs) || 0 : 0
      const count = Math.max(upperCount - lowerCount, 0)
      return {
        label: bin.label,
        count,
        ratio: sampleCount > 0 ? count / sampleCount : 0,
      }
    })

    return {
      bucket,
      sampleCount,
      coverage: bucketCoverage(sampleCount, totalSamples),
      confidence: bucketConfidence(sampleCount),
      cells,
    }
  })
}

function readDistributionByChannel(
  result: PrometheusVectorResult[] = [],
  bucketLabel: "input_token_bucket" | "output_token_bucket",
  buckets: string[],
  preferredBoundaries: number[],
  formatBoundary: (value: number) => string
) {
  const cumulativeByChannel = new Map<string, Map<string, Map<number, number>>>()

  result.forEach((item) => {
    const key = channelKey(item.metric)
    const bucket = item.metric[bucketLabel] || "-"
    const le = parseHistogramLe(item.metric.le)
    if (typeof le !== "number") {
      return
    }

    const buckets = cumulativeByChannel.get(key) || new Map<string, Map<number, number>>()
    const cumulativeCounts = buckets.get(bucket) || new Map<number, number>()
    cumulativeCounts.set(le, sampleValue(item.value?.[1]))
    buckets.set(bucket, cumulativeCounts)
    cumulativeByChannel.set(key, buckets)
  })

  const values = new Map<string, BucketDistributionRow[]>()
  cumulativeByChannel.forEach((bucketCounts, key) => {
    values.set(key, buildDistributionRows(buckets, preferredBoundaries, formatBoundary, bucketCounts))
  })
  return values
}

const readTtftDistributionByChannel = (result: PrometheusVectorResult[] = []) =>
  readDistributionByChannel(result, "input_token_bucket", INPUT_BUCKETS, TTFT_DISTRIBUTION_BOUNDARIES_MS, formatTtftDistributionBoundary)

const readTpsDistributionByChannel = (result: PrometheusVectorResult[] = []) =>
  readDistributionByChannel(result, "output_token_bucket", OUTPUT_BUCKETS, TPS_DISTRIBUTION_BOUNDARIES, formatTpsDistributionBoundary)

function latestTrendValue(
  trend: PerformanceTrendPoint[],
  key: keyof Pick<PerformanceTrendPoint, "ttftAvgMs" | "ttftP95Ms" | "tpsAvg" | "tpsP10">
) {
  for (let index = trend.length - 1; index >= 0; index -= 1) {
    const value = trend[index][key]
    if (typeof value === "number" && Number.isFinite(value)) {
      return value
    }
  }
  return undefined
}

function singleChannelMetric(rowId: string | undefined, value: number | undefined) {
  return rowId && typeof value === "number" && Number.isFinite(value) ? new Map([[rowId, value]]) : new Map<string, number>()
}

function buildTimeline(rows: ChannelHealthRow[], result: PrometheusMatrixResult[], start: number, end: number, stepSeconds: number) {
  const cellsByChannel = new Map<string, SampleCell[]>()
  const slotCount = Math.max(1, Math.ceil((end - start) / stepSeconds))
  const slots = Array.from({ length: slotCount }, (_, index) => {
    const timestamp = start + index * stepSeconds
    return {
      timestamp,
      label: formatSampleLabel(timestamp),
    }
  })

  rows.forEach((row) => {
    cellsByChannel.set(row.id, slots.map((slot) => emptySample(slot.timestamp, slot.label)))
  })

  result.forEach((series) => {
    const key = channelKey(series.metric)
    const statusClass = series.metric.status_class || "unknown"
    const cells = cellsByChannel.get(key)
    if (!cells) {
      return
    }

    series.values.forEach(([timestamp, rawValue]) => {
      const slotIndex = Math.min(Math.max(Math.floor((timestamp - start) / stepSeconds), 0), slotCount - 1)
      const cell = cells[slotIndex]
      const value = sampleValue(rawValue)
      cell.total += value
      if (statusClass === "success") {
        cell.success += value
      } else if (statusClass === "error_429") {
        cell.error429 += value
      } else if (statusClass === "error_5xx") {
        cell.error5xx += value
      } else if (statusClass === "error_4xx") {
        cell.error4xx += value
      }
      cell.status = resolveSampleStatus(cell)
    })
  })

  rows.forEach((row) => {
    row.samples = cellsByChannel.get(row.id) || []
  })
}

function buildRecentAnomalies(rows: ChannelHealthRow[]) {
  const anomalies: RecentAnomaly[] = []

  rows.forEach((row) => {
    row.samples
      .filter((sample) => sample.status !== "success" && sample.status !== "no_traffic")
      .slice(-3)
      .forEach((sample) => {
        const parts = []
        if (sample.error5xx > 0) {
          parts.push(`5xx ${sample.error5xx.toFixed(0)}`)
        }
        if (sample.error429 > 0) {
          parts.push(`429 ${sample.error429.toFixed(0)}`)
        }
        if (sample.error4xx > 0) {
          parts.push(`4xx ${sample.error4xx.toFixed(0)}`)
        }
        anomalies.push({
          time: sample.label,
          channelCode: row.channelCode,
          model: row.model,
          supplier: row.supplier,
          message: parts.join(" / "),
          severity: unavailableMetric("异常等级规则未发布"),
        })
      })
  })

  return anomalies.sort((left, right) => right.time.localeCompare(left.time)).slice(0, 8)
}

function buildRows(
  requestDistribution: PrometheusVectorResult[],
  outputTokenTpm: Map<string, number>,
  ttftAvg: Map<string, number>,
  ttftP95: Map<string, number>,
  tpsAvg: Map<string, number>,
  tpsP10: Map<string, number>,
  inputBucketAvg: Map<string, Map<string, number>>,
  inputBucketP95: Map<string, Map<string, number>>,
  outputBucketAvg: Map<string, Map<string, number>>,
  outputBucketP10: Map<string, Map<string, number>>,
  rangeMinutes: number
) {
  const rows = new Map<string, ChannelHealthRow>()
  const breakdownByChannel = new Map<string, {
    requestCount: number
    errorCount: number
    channelIssueCount: number
    clientErrorCount: number
    rate429Count: number
    error5xxCount: number
    metric: Record<string, string>
  }>()

  requestDistribution.forEach((item) => {
    const key = channelKey(item.metric)
    const count = sampleValue(item.value?.[1])
    const statusClass = item.metric.status_class || "unknown"
    const breakdown = breakdownByChannel.get(key) || {
      requestCount: 0,
      errorCount: 0,
      channelIssueCount: 0,
      clientErrorCount: 0,
      rate429Count: 0,
      error5xxCount: 0,
      metric: item.metric,
    }

    breakdown.requestCount += count

    if (statusClass !== "success") {
      breakdown.errorCount += count
    }
    if (statusClass === "error_429") {
      breakdown.rate429Count += count
      breakdown.channelIssueCount += count
    } else if (statusClass === "error_5xx" || statusClass === "timeout" || statusClass === "network_error") {
      breakdown.error5xxCount += statusClass === "error_5xx" ? count : 0
      breakdown.channelIssueCount += count
    } else if (statusClass === "error_4xx") {
      breakdown.clientErrorCount += count
    }

    breakdownByChannel.set(key, breakdown)
  })

  breakdownByChannel.forEach((breakdown, key) => {
    const { metric } = breakdown
    const requestCount = breakdown.requestCount
    const gatewayIntercept = metric.channel_code === GATEWAY_CHANNEL_CODE
    rows.set(key, {
      id: key,
      model: metric.model || "-",
      supplier: gatewayIntercept ? GATEWAY_SUPPLIER_LABEL : metric.supplier || "-",
      channelCode: gatewayIntercept ? GATEWAY_CHANNEL_LABEL : metric.channel_code || "-",
      forwardHost: gatewayIntercept ? GATEWAY_FORWARD_HOST_LABEL : metric.forward_host || "-",
      deployName: gatewayIntercept ? GATEWAY_DEPLOY_NAME_LABEL : metric.deploy_name || "-",
      requestCount,
      errorCount: breakdown.errorCount,
      channelIssueCount: breakdown.channelIssueCount,
      clientErrorCount: breakdown.clientErrorCount,
      rpm: rangeMinutes > 0 ? requestCount / rangeMinutes : 0,
      failureRate: requestCount > 0 ? breakdown.channelIssueCount / requestCount : 0,
      clientErrorRate: requestCount > 0 ? breakdown.clientErrorCount / requestCount : 0,
      rate429Count: breakdown.rate429Count,
      error5xxCount: breakdown.error5xxCount,
      outputTokenTpm: outputTokenTpm.get(key) || 0,
      ttftAvgMs: Number.isFinite(ttftAvg.get(key)) ? availableMetric(ttftAvg.get(key) || 0) : noDataMetric<number>(),
      ttftP95Ms: Number.isFinite(ttftP95.get(key)) ? availableMetric(ttftP95.get(key) || 0) : noDataMetric<number>(),
      tpsAvg: Number.isFinite(tpsAvg.get(key)) ? availableMetric(tpsAvg.get(key) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      tpsP10: Number.isFinite(tpsP10.get(key)) ? availableMetric(tpsP10.get(key) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      inputTokenBuckets: buildLatencyBucketRows(INPUT_BUCKETS, inputBucketAvg.get(key) || new Map(), inputBucketP95.get(key) || new Map()),
      outputTokenBuckets: buildTpsBucketRows(OUTPUT_BUCKETS, outputBucketAvg.get(key) || new Map(), outputBucketP10.get(key) || new Map()),
      ttftDistribution: emptyTtftDistributionRows(),
      tpsDistribution: emptyTpsDistributionRows(),
      performanceTrend: [],
      samples: [],
    })
  })

  return Array.from(rows.values()).sort((left, right) => {
    if (right.failureRate !== left.failureRate) {
      return right.failureRate - left.failureRate
    }
    return right.requestCount - left.requestCount
  }).map((row) => ({
    ...row,
    failureRate: Math.max(row.failureRate, 0),
    rpm: row.rpm,
  }))
}

function aggregateSamples(channels: ChannelHealthRow[]) {
  const cells = new Map<number, SampleCell>()

  channels.forEach((channel) => {
    channel.samples.forEach((sample) => {
      const current = cells.get(sample.timestamp) || emptySample(sample.timestamp, sample.label)
      current.total += sample.total
      current.success += sample.success
      current.error429 += sample.error429
      current.error5xx += sample.error5xx
      current.error4xx += sample.error4xx
      current.status = resolveSampleStatus(current)
      cells.set(sample.timestamp, current)
    })
  })

  return Array.from(cells.values()).sort((left, right) => left.timestamp - right.timestamp)
}

function groupKey(row: ChannelHealthRow, groupBy: ChannelHealthGroupBy) {
  if (groupBy === "supplier") {
    return row.supplier
  }
  if (groupBy === "forward_host") {
    return row.forwardHost
  }
  if (groupBy === "channel") {
    return resourceGroupLabel(row.forwardHost, row.deployName)
  }
  return row.model
}

function groupSubtitle(channels: ChannelHealthRow[], groupBy: ChannelHealthGroupBy) {
  const gatewayCount = channels.filter((channel) => channel.channelCode === GATEWAY_CHANNEL_LABEL).length
  const channelCount = channels.length - gatewayCount
  const models = new Set(channels.map((channel) => channel.model).filter(Boolean))
  const suppliers = new Set(channels.map((channel) => channel.supplier).filter(Boolean))
  const channelCodes = new Set(channels.map((channel) => channel.channelCode).filter(Boolean))
  const gatewayText = gatewayCount > 0 ? ` / ${gatewayCount} 个网关侧` : ""

  if (groupBy === "supplier") {
    return `${models.size} 个模型 / ${channelCount} 个渠道${gatewayText}`
  }
  if (groupBy === "forward_host") {
    return `${suppliers.size} 个供应商 / ${models.size} 个模型 / ${channelCount} 个渠道${gatewayText}`
  }
  if (groupBy === "channel") {
    return `${suppliers.size} 个供应商 / ${models.size} 个模型 / ${channelCodes.size} 个 channel_code${gatewayText}`
  }
  return `${suppliers.size} 个供应商 / ${channelCount} 个渠道${gatewayText}`
}

function buildGroups(
  rows: ChannelHealthRow[],
  groupBy: ChannelHealthGroupBy,
  ttftAvg: Map<string, number>,
  ttftP95: Map<string, number>,
  tpsAvg: Map<string, number>,
  tpsP10: Map<string, number>,
): ChannelHealthGroup[] {
  const groupedRows = new Map<string, ChannelHealthRow[]>()

  rows.forEach((row) => {
    const key = groupKey(row, groupBy)
    const current = groupedRows.get(key) || []
    current.push(row)
    groupedRows.set(key, current)
  })

  return Array.from(groupedRows.entries()).map(([key, channels]) => {
    const requestCount = channels.reduce((total, channel) => total + channel.requestCount, 0)
    const errorCount = channels.reduce((total, channel) => total + channel.errorCount, 0)
    const channelIssueCount = channels.reduce((total, channel) => total + channel.channelIssueCount, 0)
    const clientErrorCount = channels.reduce((total, channel) => total + channel.clientErrorCount, 0)
    const rate429Count = channels.reduce((total, channel) => total + channel.rate429Count, 0)
    const error5xxCount = channels.reduce((total, channel) => total + channel.error5xxCount, 0)
    const outputTokenTpm = channels.reduce((total, channel) => total + channel.outputTokenTpm, 0)
    const rpm = channels.reduce((total, channel) => total + channel.rpm, 0)

    return {
      id: `${groupBy}:${key}`,
      groupBy,
      label: key,
      subtitle: groupSubtitle(channels, groupBy),
      requestCount,
      errorCount,
      channelIssueCount,
      clientErrorCount,
      rpm,
      failureRate: requestCount > 0 ? channelIssueCount / requestCount : 0,
      clientErrorRate: requestCount > 0 ? clientErrorCount / requestCount : 0,
      rate429Count,
      error5xxCount,
      outputTokenTpm,
      ttftAvgMs: Number.isFinite(ttftAvg.get(key)) ? availableMetric(ttftAvg.get(key) || 0) : noDataMetric<number>(),
      ttftP95Ms: Number.isFinite(ttftP95.get(key)) ? availableMetric(ttftP95.get(key) || 0) : noDataMetric<number>(),
      tpsAvg: Number.isFinite(tpsAvg.get(key)) ? availableMetric(tpsAvg.get(key) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      tpsP10: Number.isFinite(tpsP10.get(key)) ? availableMetric(tpsP10.get(key) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      samples: aggregateSamples(channels),
      channels: channels.sort((left, right) => {
        if (right.failureRate !== left.failureRate) {
          return right.failureRate - left.failureRate
        }
        return right.requestCount - left.requestCount
      }),
    }
  }).sort((left, right) => {
    if (right.failureRate !== left.failureRate) {
      return right.failureRate - left.failureRate
    }
    return right.requestCount - left.requestCount
  })
}

function metricFromMap(values: Map<string, number>, key: string, reason?: string) {
  const value = values.get(key)
  return Number.isFinite(value) ? availableMetric(value || 0) : noDataMetric<number>(reason)
}

function bucketConfidence(sampleCount: number) {
  if (sampleCount <= 0) {
    return "none" as const
  }
  if (sampleCount < 20) {
    return "low" as const
  }
  if (sampleCount < 100) {
    return "medium" as const
  }
  return "high" as const
}

function bucketCoverage(sampleCount: number, total: number) {
  return total > 0 ? sampleCount / total : 0
}

function deltaMetric(primary: MetricValue<number>, secondary: MetricValue<number>, compute: (primary: number, secondary: number) => number) {
  if (primary.status === "available" && secondary.status === "available" && typeof primary.value === "number" && typeof secondary.value === "number") {
    const value = compute(primary.value, secondary.value)
    return value >= 0 ? availableMetric(value) : noDataMetric<number>()
  }
  return noDataMetric<number>()
}

function ratioMetric(numerator: MetricValue<number>, denominator: MetricValue<number>) {
  if (
    numerator.status === "available" &&
    denominator.status === "available" &&
    typeof numerator.value === "number" &&
    typeof denominator.value === "number" &&
    denominator.value > 0
  ) {
    return availableMetric(numerator.value / denominator.value)
  }
  return noDataMetric<number>()
}

function buildLatencyBucketRows(
  buckets: string[],
  avgValues: Map<string, number>,
  p95Values: Map<string, number>,
  countValues = new Map<string, number>()
): BucketBreakdownRow[] {
  const totalSamples = buckets.reduce((total, bucket) => total + (countValues.get(bucket) || 0), 0)
  return buckets.map((bucket) => {
    const avg = metricFromMap(avgValues, bucket)
    const tail = metricFromMap(p95Values, bucket)
    const sampleCount = countValues.get(bucket) || 0
    return {
      bucket,
      sampleCount,
      coverage: bucketCoverage(sampleCount, totalSamples),
      confidence: bucketConfidence(sampleCount),
      avg,
      tail,
      delta: deltaMetric(tail, avg, (p95, avgValue) => p95 - avgValue),
    }
  })
}

function buildTpsBucketRows(
  buckets: string[],
  avgValues: Map<string, number>,
  p10Values: Map<string, number>,
  countValues = new Map<string, number>()
): BucketBreakdownRow[] {
  const totalSamples = buckets.reduce((total, bucket) => total + (countValues.get(bucket) || 0), 0)
  return buckets.map((bucket) => {
    const avg = metricFromMap(avgValues, bucket, "当前时间范围无流式 TPS 样本")
    const tail = metricFromMap(p10Values, bucket, "当前时间范围无流式 TPS 样本")
    const sampleCount = countValues.get(bucket) || 0
    return {
      bucket,
      sampleCount,
      coverage: bucketCoverage(sampleCount, totalSamples),
      confidence: bucketConfidence(sampleCount),
      avg,
      tail,
      delta: deltaMetric(avg, tail, (avgValue, p10) => avgValue - p10),
      ratio: ratioMetric(tail, avg),
    }
  })
}

export async function getCoreData(filters: ChannelHealthFilters, signal: AbortSignal): Promise<ChannelHealthDashboardData> {
  const matcher = requestMatcher(filters)
  const baseUrl = getDefaultPrometheusBaseUrl()
  const rateWindow = filters.timePreset.rateWindow

  const requestMetric = `bella_channel_requests_total${matcher}`
  const tokenMetric = `bella_channel_tokens_total${requestMatcher(filters, ['type=~"total|output"'])}`
  const outputTokenMetric = `bella_channel_tokens_total${requestMatcher(filters, ['type="output"'])}`

  const requestDistributionQuery = `sum by (${CHANNEL_GROUP_LABELS}, status_class, http_status_code) (increase(${requestMetric}[${filters.timePreset.minutes}m]))`
  const requestRateQuery = `sum(rate(${requestMetric}[${rateWindow}])) * 60`
  const tokenTotalByTypeQuery = `sum by (type) (increase(${tokenMetric}[${filters.timePreset.minutes}m]))`
  const tokenTpmByTypeQuery = `sum by (type) (rate(${tokenMetric}[${rateWindow}])) * 60`
  const outputTokenTpmByChannelQuery = `sum by (${CHANNEL_GROUP_LABELS}) (rate(${outputTokenMetric}[${rateWindow}])) * 60`
  const coreQuery = [
    queryWithKind(requestDistributionQuery, "request_distribution"),
    queryWithKind(requestRateQuery, "request_rate"),
    queryWithKind(tokenTotalByTypeQuery, "token_total_by_type"),
    queryWithKind(tokenTpmByTypeQuery, "token_tpm_by_type"),
    queryWithKind(outputTokenTpmByChannelQuery, "output_token_tpm_by_channel"),
  ].join(" or ")
  const coreResult = await queryVector(baseUrl, coreQuery, signal)
  const requestDistribution = filterVectorByKind(coreResult, "request_distribution")
  const requestRate = filterVectorByKind(coreResult, "request_rate")
  const tokenTotalByType = filterVectorByKind(coreResult, "token_total_by_type")
  const tokenTpmByType = filterVectorByKind(coreResult, "token_tpm_by_type")
  const outputTokenTpmByChannel = filterVectorByKind(coreResult, "output_token_tpm_by_channel")

  const emptyMap = new Map<string, number>()
  const emptyBucketMap = new Map<string, Map<string, number>>()
  const outputTokenTpmMap = readQuantileByChannel(outputTokenTpmByChannel)
  const rows = buildRows(
    requestDistribution,
    outputTokenTpmMap,
    emptyMap,
    emptyMap,
    emptyMap,
    emptyMap,
    emptyBucketMap,
    emptyBucketMap,
    emptyBucketMap,
    emptyBucketMap,
    filters.timePreset.minutes
  )

  rows.forEach((row) => {
    row.ttftAvgMs = loadingMetric()
    row.ttftP95Ms = loadingMetric()
    row.tpsAvg = loadingMetric()
    row.tpsP10 = loadingMetric()
    row.inputTokenBuckets = buildLatencyBucketRows(INPUT_BUCKETS, new Map(), new Map())
    row.outputTokenBuckets = buildTpsBucketRows(OUTPUT_BUCKETS, new Map(), new Map())
    row.ttftDistribution = emptyTtftDistributionRows()
    row.tpsDistribution = emptyTpsDistributionRows()
    row.performanceTrend = []
  })

  const groups = buildGroups(rows, filters.groupBy, emptyMap, emptyMap, emptyMap, emptyMap)
  groups.forEach((group) => {
    group.ttftAvgMs = loadingMetric()
    group.ttftP95Ms = loadingMetric()
    group.tpsAvg = loadingMetric()
    group.tpsP10 = loadingMetric()
  })

  const requestCount = rows.reduce((total, row) => total + row.requestCount, 0)
  const tokenTotalValue = (type: string) => sampleValue(tokenTotalByType.find((item) => item.metric.type === type)?.value?.[1])
  const tokenTpmValue = (type: string) => sampleValue(tokenTpmByType.find((item) => item.metric.type === type)?.value?.[1])
  const outputTokenTpmTotal = Array.from(outputTokenTpmMap.values()).reduce((total, value) => total + value, 0)

  const data = {
    generatedAt: new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
    summary: {
      requestCount,
      rpm: sumVector(requestRate),
      totalTokens: tokenTotalValue("total"),
      tokenTpm: tokenTpmValue("total"),
      outputTokens: tokenTotalValue("output"),
      outputTokenTpm: outputTokenTpmTotal,
    },
    groups,
    rows,
    recentAnomalies: buildRecentAnomalies(rows),
  }

  return data
}

export type TimelineQueryData = {
  timeline: PrometheusMatrixResult[]
  start: number
  end: number
  stepSeconds: number
  slotCount: number
}

export async function queryTimelineData(
  filters: ChannelHealthFilters,
  signal: AbortSignal
): Promise<TimelineQueryData> {
  const now = Math.floor(Date.now() / 1000)
  const start = now - filters.timePreset.minutes * 60
  const stepSeconds = filters.timePreset.stepSeconds
  const slotCount = Math.max(1, Math.ceil((now - start) / stepSeconds))
  const timelineWindow = `${Math.max(stepSeconds, 60)}s`
  const matcher = requestMatcher(filters)
  const baseUrl = getDefaultPrometheusBaseUrl()
  const requestMetric = `bella_channel_requests_total${matcher}`
  const timelineQuery = `sum by (${CHANNEL_GROUP_LABELS}, status_class) (increase(${requestMetric}[${timelineWindow}]))`
  const timeline = await queryRange(baseUrl, timelineQuery, { start, end: now, stepSeconds }, signal)

  return {
    timeline,
    start,
    end: now,
    stepSeconds,
    slotCount,
  }
}

export function buildTimelineSamples(
  rows: ChannelHealthRow[],
  timelineData: TimelineQueryData
): Map<string, SampleCell[]> {
  const timelineRows = rows.map((row) => ({ ...row, samples: [] }))

  buildTimeline(timelineRows, timelineData.timeline, timelineData.start, timelineData.end, timelineData.stepSeconds)

  return new Map(timelineRows.map((row) => [row.id, row.samples]))
}

export async function getTimelineSamples(
  filters: ChannelHealthFilters,
  rows: ChannelHealthRow[],
  signal: AbortSignal
): Promise<Map<string, SampleCell[]>> {
  const timelineData = await queryTimelineData(filters, signal)
  return buildTimelineSamples(rows, timelineData)
}

export function mergeTimelineSamples(
  data: ChannelHealthDashboardData,
  samplesByRow: Map<string, SampleCell[]>,
  groupBy: ChannelHealthGroupBy
): ChannelHealthDashboardData {
  const rows = data.rows.map((row) => ({
    ...row,
    samples: samplesByRow.get(row.id) || [],
  }))

  const nextData = regroupChannelHealthData({
    ...data,
    rows,
    recentAnomalies: buildRecentAnomalies(rows),
  }, groupBy)

  return nextData
}

export type DetailScope = {
  selectedRow?: ChannelHealthRow
  expandedGroupLabels?: string[]
}

function scopedMatcher(filters: ChannelHealthFilters, scope?: DetailScope, extraLabels: string[] = []) {
  const labels = [...extraLabels]

  if (scope?.selectedRow) {
    const row = scope.selectedRow
    if (row.model && row.model !== "-") labels.push(`model="${labelValue(row.model)}"`)
    if (row.supplier && row.supplier !== GATEWAY_SUPPLIER_LABEL && row.supplier !== "-") labels.push(`supplier="${labelValue(row.supplier)}"`)
    const channelCode = row.channelCode === GATEWAY_CHANNEL_LABEL ? GATEWAY_CHANNEL_CODE : row.channelCode
    if (channelCode && channelCode !== "-") labels.push(`channel_code="${labelValue(channelCode)}"`)
    if (row.forwardHost && row.forwardHost !== GATEWAY_FORWARD_HOST_LABEL && row.forwardHost !== "-") labels.push(`forward_host="${labelValue(row.forwardHost)}"`)
    if (row.deployName && row.deployName !== GATEWAY_DEPLOY_NAME_LABEL && row.deployName !== "-") labels.push(`deploy_name="${labelValue(row.deployName)}"`)
    return requestMatcher(filters, labels)
  }

  if (scope?.expandedGroupLabels && scope.expandedGroupLabels.length > 0) {
    const groupBy = filters.groupBy
    if (groupBy === "model") {
      labels.push(`model=~"${scope.expandedGroupLabels.map(regexLabelValue).join("|")}"`)
    } else if (groupBy === "supplier") {
      labels.push(`supplier=~"${scope.expandedGroupLabels.map(regexLabelValue).join("|")}"`)
    } else if (groupBy === "forward_host") {
      labels.push(`forward_host=~"${scope.expandedGroupLabels.map(regexLabelValue).join("|")}"`)
    }
    return requestMatcher(filters, labels)
  }

  return requestMatcher(filters, labels)
}

export async function getDetailData(filters: ChannelHealthFilters, signal: AbortSignal, scope?: DetailScope): Promise<ChannelHealthDetailData> {
  const now = Math.floor(Date.now() / 1000)
  const start = now - filters.timePreset.minutes * 60
  const stepSeconds = filters.timePreset.stepSeconds
  const matcher = scopedMatcher(filters, scope)
  const tpsMatcher = scopedMatcher(filters, scope, ['stream="true"'])
  const baseUrl = getDefaultPrometheusBaseUrl()
  const rateWindow = filters.timePreset.rateWindow

  const rangeQuantile = (q: number, metric: string, scopedMetricMatcher: string) =>
    `histogram_quantile(${q}, sum by (le) (rate(${metric}${scopedMetricMatcher}[${rateWindow}])))`
  const inputBucketQuantile = (q: number) =>
    `histogram_quantile(${q}, sum by (le, ${CHANNEL_GROUP_LABELS}, input_token_bucket) (rate(${SYSTEM_TTFT_BUCKET_METRIC}${matcher}[${rateWindow}])))`
  const inputBucketCount = () =>
    `sum by (${CHANNEL_GROUP_LABELS}, input_token_bucket) (increase(${SYSTEM_TTFT_COUNT_METRIC}${matcher}[${filters.timePreset.minutes}m]))`
  const inputBucketDistribution = () =>
    `sum by (${CHANNEL_GROUP_LABELS}, input_token_bucket, le) (increase(${SYSTEM_TTFT_BUCKET_METRIC}${matcher}[${filters.timePreset.minutes}m]))`
  const outputBucketQuantile = (q: number) =>
    `histogram_quantile(${q}, sum by (le, ${CHANNEL_GROUP_LABELS}, output_token_bucket) (rate(${CHAT_COMPLETION_TPS_BUCKET_METRIC}${tpsMatcher}[${rateWindow}])))`
  const outputBucketCount = () =>
    `sum by (${CHANNEL_GROUP_LABELS}, output_token_bucket) (increase(${CHAT_COMPLETION_TPS_COUNT_METRIC}${tpsMatcher}[${filters.timePreset.minutes}m]))`
  const outputBucketDistribution = () =>
    `sum by (${CHANNEL_GROUP_LABELS}, output_token_bucket, le) (increase(${CHAT_COMPLETION_TPS_BUCKET_METRIC}${tpsMatcher}[${filters.timePreset.minutes}m]))`
  const bucketQuery = [
    queryWithKind(inputBucketQuantile(0.5), "input_ttft_p50"),
    queryWithKind(inputBucketQuantile(0.95), "input_ttft_p95"),
    queryWithKind(inputBucketCount(), "input_ttft_count"),
    queryWithKind(inputBucketDistribution(), "input_ttft_distribution"),
    queryWithKind(outputBucketQuantile(0.5), "output_tps_p50"),
    queryWithKind(outputBucketQuantile(0.1), "output_tps_p10"),
    queryWithKind(outputBucketCount(), "output_tps_count"),
    queryWithKind(outputBucketDistribution(), "output_tps_distribution"),
  ].join(" or ")
  const trendQuery = [
    queryWithKind(rangeQuantile(0.5, SYSTEM_TTFT_BUCKET_METRIC, matcher), "ttft_p50"),
    queryWithKind(rangeQuantile(0.95, SYSTEM_TTFT_BUCKET_METRIC, matcher), "ttft_p95"),
    queryWithKind(rangeQuantile(0.5, CHAT_COMPLETION_TPS_BUCKET_METRIC, tpsMatcher), "tps_p50"),
    queryWithKind(rangeQuantile(0.1, CHAT_COMPLETION_TPS_BUCKET_METRIC, tpsMatcher), "tps_p10"),
  ].join(" or ")

  const [bucketResult, trendResult] = await Promise.all([
    queryVector(baseUrl, bucketQuery, signal),
    queryRange(baseUrl, trendQuery, { start, end: now, stepSeconds }, signal),
  ])

  const performanceTrend = buildPerformanceTrend(
    start,
    now,
    stepSeconds,
    filterMatrixByKind(trendResult, "ttft_p50"),
    filterMatrixByKind(trendResult, "ttft_p95"),
    filterMatrixByKind(trendResult, "tps_p50"),
    filterMatrixByKind(trendResult, "tps_p10")
  )
  const selectedRowId = scope?.selectedRow?.id

  return {
    ttftAvg: singleChannelMetric(selectedRowId, latestTrendValue(performanceTrend, "ttftAvgMs")),
    ttftP95: singleChannelMetric(selectedRowId, latestTrendValue(performanceTrend, "ttftP95Ms")),
    tpsAvg: singleChannelMetric(selectedRowId, latestTrendValue(performanceTrend, "tpsAvg")),
    tpsP10: singleChannelMetric(selectedRowId, latestTrendValue(performanceTrend, "tpsP10")),
    inputBucketAvg: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "input_ttft_p50"), "input_token_bucket"),
    inputBucketP95: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "input_ttft_p95"), "input_token_bucket"),
    inputBucketCount: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "input_ttft_count"), "input_token_bucket"),
    outputBucketAvg: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "output_tps_p50"), "output_token_bucket"),
    outputBucketP10: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "output_tps_p10"), "output_token_bucket"),
    outputBucketCount: readBucketQuantilesByChannel(filterVectorByKind(bucketResult, "output_tps_count"), "output_token_bucket"),
    inputTtftDistribution: readTtftDistributionByChannel(filterVectorByKind(bucketResult, "input_ttft_distribution")),
    outputTpsDistribution: readTpsDistributionByChannel(filterVectorByKind(bucketResult, "output_tps_distribution")),
    performanceTrend,
  }
}

export function mergeDetailData(
  data: ChannelHealthDashboardData,
  detail: ChannelHealthDetailData,
  filters: ChannelHealthFilters
): ChannelHealthDashboardData {
  const rows = data.rows.map((row) => ({
    ...row,
    ttftAvgMs: Number.isFinite(detail.ttftAvg.get(row.id)) ? availableMetric(detail.ttftAvg.get(row.id) || 0) : noDataMetric<number>(),
    ttftP95Ms: Number.isFinite(detail.ttftP95.get(row.id)) ? availableMetric(detail.ttftP95.get(row.id) || 0) : noDataMetric<number>(),
    tpsAvg: Number.isFinite(detail.tpsAvg.get(row.id)) ? availableMetric(detail.tpsAvg.get(row.id) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
    tpsP10: Number.isFinite(detail.tpsP10.get(row.id)) ? availableMetric(detail.tpsP10.get(row.id) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
    inputTokenBuckets: buildLatencyBucketRows(
      INPUT_BUCKETS,
      detail.inputBucketAvg.get(row.id) || new Map(),
      detail.inputBucketP95.get(row.id) || new Map(),
      detail.inputBucketCount.get(row.id) || new Map()
    ),
    outputTokenBuckets: buildTpsBucketRows(
      OUTPUT_BUCKETS,
      detail.outputBucketAvg.get(row.id) || new Map(),
      detail.outputBucketP10.get(row.id) || new Map(),
      detail.outputBucketCount.get(row.id) || new Map()
    ),
    ttftDistribution: detail.inputTtftDistribution.get(row.id) || emptyTtftDistributionRows(),
    tpsDistribution: detail.outputTpsDistribution.get(row.id) || emptyTpsDistributionRows(),
  }))

  const groups = buildGroups(
    rows,
    filters.groupBy,
    detail.ttftAvg,
    detail.ttftP95,
    detail.tpsAvg,
    detail.tpsP10
  )

  return { ...data, rows, groups }
}

export function mergeScopedDetailData(
  data: ChannelHealthDashboardData,
  detail: ChannelHealthDetailData,
  filters: ChannelHealthFilters,
  targetRowId: string
): ChannelHealthDashboardData {
  const rows = data.rows.map((row) => {
    if (row.id !== targetRowId) {
      return row
    }

    return {
      ...row,
      ttftAvgMs: Number.isFinite(detail.ttftAvg.get(row.id)) ? availableMetric(detail.ttftAvg.get(row.id) || 0) : noDataMetric<number>(),
      ttftP95Ms: Number.isFinite(detail.ttftP95.get(row.id)) ? availableMetric(detail.ttftP95.get(row.id) || 0) : noDataMetric<number>(),
      tpsAvg: Number.isFinite(detail.tpsAvg.get(row.id)) ? availableMetric(detail.tpsAvg.get(row.id) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      tpsP10: Number.isFinite(detail.tpsP10.get(row.id)) ? availableMetric(detail.tpsP10.get(row.id) || 0) : noDataMetric<number>("当前时间范围无流式 TPS 样本"),
      inputTokenBuckets: buildLatencyBucketRows(
        INPUT_BUCKETS,
        detail.inputBucketAvg.get(row.id) || new Map(),
        detail.inputBucketP95.get(row.id) || new Map(),
        detail.inputBucketCount.get(row.id) || new Map()
      ),
      outputTokenBuckets: buildTpsBucketRows(
        OUTPUT_BUCKETS,
        detail.outputBucketAvg.get(row.id) || new Map(),
        detail.outputBucketP10.get(row.id) || new Map(),
        detail.outputBucketCount.get(row.id) || new Map()
      ),
      ttftDistribution: detail.inputTtftDistribution.get(row.id) || emptyTtftDistributionRows(),
      tpsDistribution: detail.outputTpsDistribution.get(row.id) || emptyTpsDistributionRows(),
      performanceTrend: detail.performanceTrend,
    }
  })

  const groups = buildGroups(
    rows,
    filters.groupBy,
    detail.ttftAvg,
    detail.ttftP95,
    detail.tpsAvg,
    detail.tpsP10
  )

  return { ...data, rows, groups }
}

export function regroupChannelHealthData(
  data: ChannelHealthDashboardData,
  groupBy: ChannelHealthGroupBy
): ChannelHealthDashboardData {
  const emptyMap = new Map<string, number>()
  const groups = buildGroups(data.rows, groupBy, emptyMap, emptyMap, emptyMap, emptyMap)

  groups.forEach((group) => {
    group.ttftAvgMs = loadingMetric()
    group.ttftP95Ms = loadingMetric()
    group.tpsAvg = loadingMetric()
    group.tpsP10 = loadingMetric()
  })

  return { ...data, groups }
}

export async function getChannelHealthDashboardData(filters: ChannelHealthFilters, signal: AbortSignal): Promise<ChannelHealthDashboardData> {
  return getCoreData(filters, signal)
}

export function getDefaultChannelHealthFilters(): ChannelHealthFilters {
  return {
    groupBy: "model",
    model: "",
    supplier: "",
    channelCode: "",
    timePreset: CHANNEL_HEALTH_TIME_PRESETS.find((preset) => preset.minutes === 60) || CHANNEL_HEALTH_TIME_PRESETS[0],
  }
}
