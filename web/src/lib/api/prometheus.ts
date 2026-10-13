export type PrometheusVectorResult = {
  metric: Record<string, string>
  value: [number, string]
}

export type PrometheusMatrixResult = {
  metric: Record<string, string>
  values: Array<[number, string]>
}

type PrometheusResponse<T> = {
  status: "success" | "error"
  data?: {
    resultType: "vector" | "matrix" | "scalar" | "string"
    result: T[]
  }
  error?: string
  errorType?: string
}

const DEFAULT_PROMETHEUS_BASE_URL =
  process.env.NEXT_PUBLIC_PROMETHEUS_PROXY_URL || "/api/prometheus"

export function getDefaultPrometheusBaseUrl() {
  return DEFAULT_PROMETHEUS_BASE_URL
}

export function normalizePrometheusBaseUrl(value: string) {
  const trimmed = value.trim().replace(/\/+$/, "")
  if (!trimmed) {
    throw new Error("未配置 Prometheus 代理地址")
  }
  if (/^https?:\/\//.test(trimmed) || trimmed.startsWith("/") || trimmed.startsWith("//")) {
    return trimmed
  }
  return `//${trimmed}`
}

async function requestPrometheus<T>(
  baseUrl: string,
  path: "query" | "query_range",
  params: Record<string, string>,
  signal: AbortSignal
) {
  const url = new URL(normalizePrometheusBaseUrl(baseUrl), window.location.origin)
  url.searchParams.set("path", path)
  Object.entries(params).forEach(([key, value]) => url.searchParams.set(key, value))

  const response = await fetch(url.toString(), { signal })
  if (!response.ok) {
    throw new Error(`Prometheus HTTP ${response.status}`)
  }

  const body = (await response.json()) as PrometheusResponse<T>
  if (body.status !== "success") {
    throw new Error(body.error || body.errorType || "Prometheus query failed")
  }

  return body.data?.result || []
}

export function queryVector(baseUrl: string, query: string, signal: AbortSignal) {
  return requestPrometheus<PrometheusVectorResult>(baseUrl, "query", { query }, signal)
}

export function queryRange(
  baseUrl: string,
  query: string,
  range: { start: number; end: number; stepSeconds: number },
  signal: AbortSignal
) {
  return requestPrometheus<PrometheusMatrixResult>(
    baseUrl,
    "query_range",
    {
      query,
      start: String(range.start),
      end: String(range.end),
      step: `${range.stepSeconds}s`,
    },
    signal
  )
}

export function sampleValue(value?: string) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export function sumVector(result: PrometheusVectorResult[] = []) {
  return result.reduce((total, item) => total + sampleValue(item.value?.[1]), 0)
}
