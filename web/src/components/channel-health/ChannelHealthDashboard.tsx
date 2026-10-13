"use client"

import { Fragment, memo, startTransition, useCallback, useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from "react"
import { createPortal } from "react-dom"
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Info,
  Loader2,
  Power,
  RefreshCcw,
  RotateCcw,
  SlidersHorizontal,
} from "lucide-react"
import { toast } from "sonner"
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from "recharts"

import { buildTimelineSamples, getCoreData, getDetailData, getDefaultChannelHealthFilters, mergeScopedDetailData, mergeTimelineSamples, queryTimelineData, regroupChannelHealthData, type DetailScope } from "@/lib/api/channel-health"
import { getModelOfflinePlan, offlineModel } from "@/lib/api/metadata"
import {
  CHANNEL_HEALTH_TIME_PRESETS,
  type ChannelHealthDashboardData,
  type ChannelHealthDetailData,
  type ChannelHealthFilters,
  type ChannelHealthGroup,
  type ChannelHealthRow,
  type MetricValue,
  type SampleCell,
  type BucketDistributionRow,
} from "@/lib/types/channel-health"
import type { ModelOfflinePlan } from "@/lib/types/openapi"
import { Badge } from "@/components/common/badge"
import { Button } from "@/components/common/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/common/dialog"
import { Input } from "@/components/common/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/common/tabs"
import {
  Tooltip as UiTooltip,
  TooltipContent as UiTooltipContent,
  TooltipProvider as UiTooltipProvider,
  TooltipTrigger as UiTooltipTrigger,
} from "@/components/common/tooltip"
import { TopBar } from "@/components/layout/top-bar"
import { cn } from "@/lib/utils"
import { useAuth } from "@/components/providers/auth-provider"
import { hasPermission } from "@/lib/utils/permission"

const emptyData: ChannelHealthDashboardData = {
  generatedAt: "",
  summary: {
    requestCount: 0,
    rpm: 0,
    totalTokens: 0,
    tokenTpm: 0,
    outputTokens: 0,
    outputTokenTpm: 0,
  },
  groups: [],
  rows: [],
  recentAnomalies: [],
}

const formatCompact = (value: number, fractionDigits = 1) => {
  if (!Number.isFinite(value)) {
    return "0"
  }
  if (Math.abs(value) >= 1000000) {
    return `${(value / 1000000).toFixed(fractionDigits)}M`
  }
  if (Math.abs(value) >= 1000) {
    return `${(value / 1000).toFixed(fractionDigits)}k`
  }
  return value.toFixed(fractionDigits)
}

const formatPercent = (value: number) => `${(Number.isFinite(value) ? value * 100 : 0).toFixed(2)}%`

const formatMilliseconds = (value: number) => {
  if (!Number.isFinite(value) || value <= 0) {
    return "-"
  }
  return value >= 1000 ? `${(value / 1000).toFixed(2)}s` : `${value.toFixed(0)}ms`
}

const TTFT_P95_TRUNCATION_MS = 30000
const TPS_QUANTILE_TRUNCATION = 1000

const formatTtftTail = (value: number) =>
  value >= TTFT_P95_TRUNCATION_MS && value < TTFT_P95_TRUNCATION_MS + 1
    ? `>=${formatMilliseconds(value)}`
    : formatMilliseconds(value)

const formatAxisMilliseconds = (value: number) => {
  if (!Number.isFinite(value)) {
    return ""
  }
  if (Math.abs(value) >= 1000) {
    return `${Math.round(value / 1000)}s`
  }
  return `${Math.round(value)}ms`
}

const formatTps = (value: number) => {
  if (!Number.isFinite(value) || value <= 0) {
    return "-"
  }
  if (value >= TPS_QUANTILE_TRUNCATION) {
    return `>=${TPS_QUANTILE_TRUNCATION.toFixed(0)}/s`
  }
  return `${value.toFixed(0)}/s`
}

const metricText = (metric: MetricValue<number>, formatter: (value: number) => string) => {
  if (metric.status === "available" && typeof metric.value === "number") {
    return formatter(metric.value)
  }
  if (metric.status === "loading") {
    return "加载中"
  }
  return metric.status === "unreleased" ? "未发布" : "-"
}

const metricRatioText = (numerator: MetricValue<number>, denominator: MetricValue<number>) => {
  if (
    numerator.status === "available" &&
    denominator.status === "available" &&
    typeof numerator.value === "number" &&
    typeof denominator.value === "number" &&
    denominator.value > 0
  ) {
    return formatPercent(numerator.value / denominator.value)
  }
  if (numerator.status === "loading" || denominator.status === "loading") {
    return "加载中"
  }
  return "-"
}

type ChannelHealthPageSize = 50 | 100 | "all"

const PAGE_SIZE_OPTIONS: Array<{ label: string; value: ChannelHealthPageSize }> = [
  { label: "每页 50", value: 50 },
  { label: "每页 100", value: 100 },
  { label: "全部", value: "all" },
]
const REFRESH_INTERVAL_OPTIONS = [
  { label: "手动刷新", seconds: 0 },
  { label: "5s 刷新", seconds: 5 },
  { label: "15s 刷新", seconds: 15 },
  { label: "30s 刷新", seconds: 30 },
  { label: "60s 刷新", seconds: 60 },
]

type ChannelHealthSortBy = "requestCount" | "failureRate"

const SORT_OPTIONS: Array<{ label: string; value: ChannelHealthSortBy }> = [
  { label: "请求数", value: "requestCount" },
  { label: "渠道异常率", value: "failureRate" },
]

const GROUP_BY_OPTIONS: Array<{ label: string; value: ChannelHealthFilters["groupBy"] }> = [
  { label: "模型", value: "model" },
  { label: "供应商", value: "supplier" },
  { label: "资源 Host", value: "forward_host" },
  { label: "渠道资源", value: "channel" },
]

type ChannelHealthQueryFilters = Omit<ChannelHealthFilters, "groupBy">

const HOVER_DETAIL_DELAY_MS = 650
const HOVER_DETAIL_CLOSE_DELAY_MS = 160

const sortMetric = (item: ChannelHealthGroup | ChannelHealthRow, sortBy: ChannelHealthSortBy) =>
  sortBy === "failureRate" ? item.failureRate : item.requestCount

const secondarySortMetric = (item: ChannelHealthGroup | ChannelHealthRow, sortBy: ChannelHealthSortBy) =>
  sortBy === "failureRate" ? item.requestCount : item.failureRate

const compareBySort = (left: ChannelHealthGroup | ChannelHealthRow, right: ChannelHealthGroup | ChannelHealthRow, sortBy: ChannelHealthSortBy) => {
  const primary = sortMetric(right, sortBy) - sortMetric(left, sortBy)
  if (primary !== 0) {
    return primary
  }
  const secondary = secondarySortMetric(right, sortBy) - secondarySortMetric(left, sortBy)
  if (secondary !== 0) {
    return secondary
  }
  return right.rpm - left.rpm
}

const sortGroupsForView = (groups: ChannelHealthGroup[], sortBy: ChannelHealthSortBy) =>
  groups
    .map((group) => ({
      ...group,
      channels: [...group.channels].sort((left, right) => compareBySort(left, right, sortBy)),
    }))
    .sort((left, right) => compareBySort(left, right, sortBy))

const groupByTitle = (groupBy: ChannelHealthFilters["groupBy"]) => {
  if (groupBy === "supplier") {
    return "供应商聚合视图"
  }
  if (groupBy === "forward_host") {
    return "资源 Host 聚合视图"
  }
  if (groupBy === "channel") {
    return "渠道资源聚合视图"
  }
  return "模型聚合视图"
}

const groupByBadge = (groupBy: ChannelHealthFilters["groupBy"]) => {
  if (groupBy === "supplier") {
    return "供应商"
  }
  if (groupBy === "forward_host") {
    return "Host"
  }
  if (groupBy === "channel") {
    return "资源"
  }
  return "模型"
}

const segmentButtonClass = (active: boolean) => cn(
  "rounded px-3 py-1.5 text-sm transition-colors",
  active
    ? "bg-background font-semibold text-primary shadow-sm"
    : "text-muted-foreground hover:bg-background/60 hover:text-primary"
)

function DashboardTitleInfo({
  data,
  filters,
  groupBy,
  pageStart,
  pageEnd,
  totalGroups,
}: {
  data: ChannelHealthDashboardData
  filters: ChannelHealthQueryFilters
  groupBy: ChannelHealthFilters["groupBy"]
  pageStart: number
  pageEnd: number
  totalGroups: number
}) {
  const items = [
    { label: "显示范围", value: `${pageStart}-${pageEnd} / ${totalGroups} 个聚合对象` },
    { label: "明细对象", value: `${data.rows.length} 个` },
    { label: "时间范围", value: filters.timePreset.label },
    { label: "模型", value: filters.model.trim() || "全部模型" },
    { label: "供应商", value: filters.supplier.trim() || "全部供应商" },
    { label: "请求总数", value: formatCompact(data.summary.requestCount, 0) },
    { label: "RPM", value: formatCompact(data.summary.rpm) },
    { label: "总 Token", value: formatCompact(data.summary.totalTokens, 0) },
    { label: "Output TPM", value: formatCompact(data.summary.outputTokenTpm) },
  ]

  return (
    <UiTooltipProvider delayDuration={250}>
      <UiTooltip>
        <UiTooltipTrigger asChild>
          <span className="inline-flex cursor-help items-center gap-1.5">
            {groupByTitle(groupBy)}
            <Info className="h-3.5 w-3.5 text-muted-foreground" />
          </span>
        </UiTooltipTrigger>
        <UiTooltipContent side="bottom" align="start" className="max-w-[320px] p-3 text-xs">
          <div className="grid gap-2">
            {items.map((item) => (
              <div key={item.label} className="flex min-w-0 items-center justify-between gap-4">
                <span className="shrink-0 text-muted-foreground">{item.label}</span>
                <span className="min-w-0 truncate text-right font-medium">{item.value}</span>
              </div>
            ))}
          </div>
        </UiTooltipContent>
      </UiTooltip>
    </UiTooltipProvider>
  )
}


const sampleColors = {
  success: "#34d399",
  error429: "#fbbf24",
  error5xx: "#f43f5e",
  error4xx: "#94a3b8",
  other: "#64748b",
  noTraffic: "#e2e8f0",
}

function SampleLegend({ compact = false }: { compact?: boolean }) {
  const items = [
    { label: "成功请求", color: sampleColors.success },
    { label: "429", color: sampleColors.error429 },
    { label: "5xx", color: sampleColors.error5xx },
    { label: "请求侧 4xx", color: sampleColors.error4xx },
    { label: "其他", color: sampleColors.other },
    { label: "无请求", color: sampleColors.noTraffic },
  ]

  return (
    <div className={cn("flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] font-normal text-muted-foreground", compact && "gap-x-2")}>
      {items.map((item) => (
        <span key={item.label} className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <span className="h-2 w-3 rounded-[2px]" style={{ backgroundColor: item.color }} />
          {item.label}
        </span>
      ))}
    </div>
  )
}

const SAMPLE_BAR_HEIGHT = 18
const MIN_VISIBLE_SEGMENT_HEIGHT = 2

function sampleCounts(sample: SampleCell) {
  const success = Math.max(sample.success, 0)
  const error4xx = Math.max(sample.error4xx, 0)
  const error429 = Math.max(sample.error429, 0)
  const error5xx = Math.max(sample.error5xx, 0)
  const other = Math.max(sample.total - success - error4xx - error429 - error5xx, 0)

  return { success, error4xx, error429, error5xx, other }
}

function sampleSegments(sample: SampleCell) {
  if (sample.total <= 0) {
    return [{ key: "noTraffic", color: sampleColors.noTraffic, height: SAMPLE_BAR_HEIGHT }]
  }

  const counts = sampleCounts(sample)
  const ordered = [
    { key: "error5xx", color: sampleColors.error5xx, value: counts.error5xx, protected: true },
    { key: "error429", color: sampleColors.error429, value: counts.error429, protected: true },
    { key: "error4xx", color: sampleColors.error4xx, value: counts.error4xx, protected: true },
    { key: "other", color: sampleColors.other, value: counts.other, protected: true },
    { key: "success", color: sampleColors.success, value: counts.success, protected: false },
  ].filter((segment) => segment.value > 0)

  const protectedCount = ordered.filter((segment) => segment.protected).length
  const protectedHeight = protectedCount * MIN_VISIBLE_SEGMENT_HEIGHT
  const remainingHeight = Math.max(SAMPLE_BAR_HEIGHT - protectedHeight, 0)

  return ordered.map((segment) => ({
    key: segment.key,
    color: segment.color,
    height: (segment.protected ? MIN_VISIBLE_SEGMENT_HEIGHT : 0) + (segment.value / sample.total) * remainingHeight,
  }))
}

function samplePercent(value: number, total: number) {
  if (total <= 0) {
    return "0.00%"
  }
  return `${((value / total) * 100).toFixed(2)}%`
}

function sampleTooltipRows(sample: SampleCell) {
  const counts = sampleCounts(sample)
  return [
    { label: "成功", value: counts.success, color: sampleColors.success },
    { label: "429", value: counts.error429, color: sampleColors.error429 },
    { label: "5xx", value: counts.error5xx, color: sampleColors.error5xx },
    { label: "请求侧 4xx", value: counts.error4xx, color: sampleColors.error4xx },
    { label: "其他", value: counts.other, color: sampleColors.other },
  ]
}

function sampleStepSeconds(samples: SampleCell[]) {
  if (samples.length < 2) {
    return 0
  }
  return samples[1].timestamp - samples[0].timestamp
}

function formatSampleStep(samples: SampleCell[]) {
  const seconds = sampleStepSeconds(samples)
  if (seconds <= 0) {
    return ""
  }
  if (seconds >= 3600) {
    return `${Number((seconds / 3600).toFixed(1))}h/格`
  }
  if (seconds >= 60) {
    return `${Math.round(seconds / 60)}m/格`
  }
  return `${seconds}s/格`
}

function formatSampleTime(timestamp: number, includeDate: boolean) {
  return new Date(timestamp * 1000).toLocaleString("zh-CN", includeDate
    ? { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }
    : { hour: "2-digit", minute: "2-digit" })
}

function sampleAxisLabel(samples: SampleCell[]) {
  if (samples.length === 0) {
    return ""
  }
  const first = samples[0]
  const last = samples[samples.length - 1]
  const stepSeconds = sampleStepSeconds(samples)
  const endTimestamp = stepSeconds > 0 ? last.timestamp + stepSeconds : last.timestamp
  const includeDate = new Date(first.timestamp * 1000).toDateString() !== new Date(endTimestamp * 1000).toDateString()
  const step = formatSampleStep(samples)
  return `${formatSampleTime(first.timestamp, includeDate)} - ${formatSampleTime(endTimestamp, includeDate)}${step ? ` · ${step}` : ""}`
}

const SAMPLE_TOOLTIP_WIDTH = 196
const SAMPLE_TOOLTIP_HEIGHT = 178
const SAMPLE_TOOLTIP_OFFSET = 12

const SampleTimeline = memo(function SampleTimeline({ samples, loading = false }: { samples: SampleCell[]; loading?: boolean }) {
  const [hoveredSample, setHoveredSample] = useState<{ sample: SampleCell; x: number; y: number }>()

  const updateHoveredSample = useCallback((event: ReactMouseEvent<HTMLDivElement>, sample: SampleCell) => {
    const viewportWidth = typeof window === "undefined" ? 1280 : window.innerWidth
    const viewportHeight = typeof window === "undefined" ? 720 : window.innerHeight
    const rightX = event.clientX + SAMPLE_TOOLTIP_OFFSET
    const leftX = event.clientX - SAMPLE_TOOLTIP_WIDTH - SAMPLE_TOOLTIP_OFFSET
    const belowY = event.clientY + SAMPLE_TOOLTIP_OFFSET
    const aboveY = event.clientY - SAMPLE_TOOLTIP_HEIGHT - SAMPLE_TOOLTIP_OFFSET
    const x = rightX + SAMPLE_TOOLTIP_WIDTH > viewportWidth - SAMPLE_TOOLTIP_OFFSET
      ? Math.max(SAMPLE_TOOLTIP_OFFSET, leftX)
      : rightX
    const y = belowY + SAMPLE_TOOLTIP_HEIGHT > viewportHeight - SAMPLE_TOOLTIP_OFFSET
      ? Math.max(SAMPLE_TOOLTIP_OFFSET, aboveY)
      : belowY

    setHoveredSample({
      sample,
      x,
      y,
    })
  }, [])

  if (samples.length === 0) {
    if (loading) {
      return (
        <div className="flex h-7 w-full min-w-0 items-center gap-2 overflow-hidden rounded-md bg-muted/35 px-3 text-[11px] text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          <span className="shrink-0">采样加载中</span>
          <div className="grid min-w-0 flex-1 grid-cols-12 gap-[3px]">
            {Array.from({ length: 12 }).map((_, index) => (
              <span key={index} className="h-[14px] rounded-[2px] bg-muted animate-pulse" />
            ))}
          </div>
        </div>
      )
    }

    return <div className="h-7 w-full rounded-md bg-muted/40" />
  }

  return (
    <div
      className="w-full min-w-0 overflow-hidden"
      onMouseLeave={() => setHoveredSample(undefined)}
    >
      <div
        className="grid h-7 w-full items-center gap-[2px]"
        style={{ gridTemplateColumns: `repeat(${samples.length}, minmax(2px, 1fr))` }}
      >
        {samples.map((sample) => (
          <div
            key={sample.timestamp}
            className="flex h-[18px] min-w-0 flex-col overflow-hidden rounded-[2px] bg-slate-200"
            onMouseEnter={(event) => updateHoveredSample(event, sample)}
            onMouseMove={(event) => updateHoveredSample(event, sample)}
          >
            {sampleSegments(sample).map((segment) => (
              <div
                key={segment.key}
                className="w-full shrink-0"
                style={{ height: `${segment.height}px`, backgroundColor: segment.color }}
              />
            ))}
          </div>
        ))}
      </div>

      {hoveredSample && typeof document !== "undefined" && createPortal((
        <div
          className="pointer-events-none fixed z-[9999] w-[196px] rounded-md border bg-popover p-2 text-[11px] text-popover-foreground shadow-md"
          style={{ left: hoveredSample.x, top: hoveredSample.y }}
        >
          <div className="mb-2 flex items-center justify-between gap-2 border-b pb-1.5">
            <span className="font-medium">{hoveredSample.sample.label}</span>
            <span className="tabular-nums text-muted-foreground">total {hoveredSample.sample.total.toFixed(0)}</span>
          </div>
          <div className="grid gap-1">
            {sampleTooltipRows(hoveredSample.sample).map((row) => (
              <div key={row.label} className="grid grid-cols-[1fr_auto_auto] items-center gap-2">
                <span className="inline-flex min-w-0 items-center gap-1.5">
                  <span className="h-2 w-2 shrink-0 rounded-[2px]" style={{ backgroundColor: row.color }} />
                  <span className="truncate">{row.label}</span>
                </span>
                <span className="tabular-nums">{row.value.toFixed(0)}</span>
                <span className="w-[46px] text-right tabular-nums text-muted-foreground">
                  {samplePercent(row.value, hoveredSample.sample.total)}
                </span>
              </div>
            ))}
          </div>
        </div>
      ), document.body)}
    </div>
  )
})

const riskDotClass = (failureRate: number) => {
  if (failureRate > 0.02) {
    return "bg-rose-500"
  }
  if (failureRate > 0.005) {
    return "bg-amber-500"
  }
  return "bg-emerald-500"
}

const failureTextClass = (failureRate: number) => {
  if (failureRate > 0.02) {
    return "text-rose-600"
  }
  if (failureRate > 0.005) {
    return "text-amber-600"
  }
  return "text-foreground"
}

function otherErrorCount(item: ChannelHealthGroup | ChannelHealthRow) {
  return Math.max(item.errorCount - item.rate429Count - item.error5xxCount - item.clientErrorCount, 0)
}

function CountRateCell({ count, total }: { count: number; total: number }) {
  return (
    <div className="text-right leading-tight">
      <div className="font-medium tabular-nums">{formatCompact(count, 0)}</div>
      <div className="text-[10px] font-normal text-muted-foreground">{samplePercent(count, total)}</div>
    </div>
  )
}

function OfflineModelButton({
  modelName,
  loading,
  onRequestOfflineModel,
}: {
  modelName: string
  loading: boolean
  onRequestOfflineModel: (modelName: string) => void
}) {
  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      className="h-7 px-2 text-xs text-destructive hover:text-destructive"
      disabled={loading}
      aria-label={`下线模型 ${modelName}`}
      title={`下线模型 ${modelName}`}
      onClick={(event) => {
        event.stopPropagation()
        onRequestOfflineModel(modelName)
      }}
    >
      {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Power className="h-3.5 w-3.5" />}
      下线
    </Button>
  )
}

const ChannelGroupTable = memo(function ChannelGroupTable({
  groups,
  expandedGroups,
  selectedId,
  previewId,
  loading,
  timelineLoading,
  canOfflineModel,
  offlineModelName,
  showOfflineActionColumn,
  onToggleGroup,
  onRequestOfflineModel,
  onSelectChannel,
  onPreviewChannel,
  onClearPreview,
}: {
  groups: ChannelHealthGroup[]
  expandedGroups: Set<string>
  selectedId?: string
  previewId?: string
  loading?: boolean
  timelineLoading?: boolean
  canOfflineModel?: boolean
  offlineModelName?: string
  showOfflineActionColumn?: boolean
  onToggleGroup: (groupId: string) => void
  onRequestOfflineModel: (modelName: string) => void
  onSelectChannel: (row: ChannelHealthRow) => void
  onPreviewChannel: (row: ChannelHealthRow) => void
  onClearPreview: (rowId?: string) => void
}) {
  const axisSamples = groups.find((group) => group.samples.length > 0)?.samples || []
  const axisLabel = sampleAxisLabel(axisSamples)
  const openPreviewTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  const closePreviewTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)

  const clearOpenPreviewTimer = useCallback(() => {
    if (openPreviewTimerRef.current) {
      clearTimeout(openPreviewTimerRef.current)
      openPreviewTimerRef.current = undefined
    }
  }, [])

  const clearClosePreviewTimer = useCallback(() => {
    if (closePreviewTimerRef.current) {
      clearTimeout(closePreviewTimerRef.current)
      closePreviewTimerRef.current = undefined
    }
  }, [])

  const schedulePreview = useCallback((row: ChannelHealthRow) => {
    clearOpenPreviewTimer()
    clearClosePreviewTimer()
    openPreviewTimerRef.current = setTimeout(() => {
      onPreviewChannel(row)
      openPreviewTimerRef.current = undefined
    }, HOVER_DETAIL_DELAY_MS)
  }, [clearClosePreviewTimer, clearOpenPreviewTimer, onPreviewChannel])

  const keepPreviewOpen = useCallback(() => {
    clearOpenPreviewTimer()
    clearClosePreviewTimer()
  }, [clearClosePreviewTimer, clearOpenPreviewTimer])

  const schedulePreviewClose = useCallback((rowId?: string) => {
    clearOpenPreviewTimer()
    clearClosePreviewTimer()
    closePreviewTimerRef.current = setTimeout(() => {
      onClearPreview(rowId)
      closePreviewTimerRef.current = undefined
    }, HOVER_DETAIL_CLOSE_DELAY_MS)
  }, [clearClosePreviewTimer, clearOpenPreviewTimer, onClearPreview])

  useEffect(() => {
    return () => {
      clearOpenPreviewTimer()
      clearClosePreviewTimer()
    }
  }, [clearClosePreviewTimer, clearOpenPreviewTimer])

  const detailColSpan = showOfflineActionColumn ? 8 : 7

  return (
    <div className="overflow-x-auto border-y">
      <table className="w-full min-w-[1080px] table-fixed text-sm">
        <colgroup>
          <col className="w-[320px]" />
          <col className="w-[92px]" />
          <col className="w-[82px]" />
          <col className="w-[82px]" />
          <col className="w-[96px]" />
          <col className="w-[82px]" />
          <col />
          {showOfflineActionColumn && <col className="w-[88px]" />}
        </colgroup>
        <thead className="bg-muted/40 text-xs text-muted-foreground">
          <tr>
            <th className="px-4 py-3 text-left font-medium">聚合对象</th>
            <th className="px-2 py-3 text-right font-medium">异常率</th>
            <th className="px-2 py-3 text-right font-medium">429</th>
            <th className="px-2 py-3 text-right font-medium">5xx</th>
            <th className="px-2 py-3 text-right font-medium">4xx</th>
            <th className="px-2 py-3 text-right font-medium">其他</th>
            <th className="px-4 py-2 text-left font-medium">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span className="text-[11px] text-slate-500">采样</span>
                <SampleLegend compact />
              </div>
              <div className="mt-1 text-[10px] font-normal text-muted-foreground">{axisLabel || (timelineLoading ? "采样加载中" : "-")}</div>
            </th>
            {showOfflineActionColumn && <th className="px-2 py-3 text-left font-medium">操作</th>}
          </tr>
        </thead>
        <tbody>
          {groups.length > 0 ? groups.map((group) => {
            const expanded = expandedGroups.has(group.id)
            const groupLabel = groupByBadge(group.groupBy)

            return (
              <Fragment key={group.id}>
                <tr
                  onClick={() => onToggleGroup(group.id)}
                  className="cursor-pointer border-b bg-card transition-colors hover:bg-muted/40"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      {expanded ? <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />}
                      <span className={cn("h-3 w-3 shrink-0 rounded-full", riskDotClass(group.failureRate))} />
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="truncate font-semibold" title={group.label}>{group.label}</span>
                          <Badge variant="outline" className="shrink-0 border-primary/20 bg-primary/5 text-[10px] text-primary">{groupLabel}</Badge>
                        </div>
                        <div className="truncate text-xs text-muted-foreground">{group.subtitle}</div>
                      </div>
                    </div>
                  </td>
                  <td className={cn("px-2 py-3 text-right font-medium", failureTextClass(group.failureRate))}>
                    {formatPercent(group.failureRate)}
                  </td>
                  <td className="px-2 py-3"><CountRateCell count={group.rate429Count} total={group.requestCount} /></td>
                  <td className="px-2 py-3"><CountRateCell count={group.error5xxCount} total={group.requestCount} /></td>
                  <td className="px-2 py-3"><CountRateCell count={group.clientErrorCount} total={group.requestCount} /></td>
                  <td className="px-2 py-3"><CountRateCell count={otherErrorCount(group)} total={group.requestCount} /></td>
                  <td className="overflow-hidden px-4 py-2">
                    <SampleTimeline samples={group.samples} loading={timelineLoading} />
                  </td>
                  {showOfflineActionColumn && (
                    <td className="px-2 py-3">
                      {canOfflineModel && group.groupBy === "model" && group.label !== "-" && (
                        <OfflineModelButton
                          modelName={group.label}
                          loading={offlineModelName === group.label}
                          onRequestOfflineModel={onRequestOfflineModel}
                        />
                      )}
                    </td>
                  )}
                </tr>

                {expanded && group.channels.map((row) => (
                  <Fragment key={row.id}>
                    <tr
                      onClick={(event) => {
                        event.stopPropagation()
                        onClearPreview(row.id)
                        onSelectChannel(row)
                      }}
                      className={cn(
                        "cursor-pointer border-b bg-muted/10 transition-colors hover:bg-muted/40",
                        selectedId === row.id && "bg-muted/50"
                      )}
                    >
                      <td className="px-4 py-3">
                        <Popover
                          open={previewId === row.id && selectedId !== row.id}
                          onOpenChange={(open) => {
                            if (!open) {
                              onClearPreview(row.id)
                            }
                          }}
                        >
                          <PopoverTrigger asChild>
                            <div
                              className="flex items-center gap-2 pl-8"
                              onMouseEnter={() => schedulePreview(row)}
                              onMouseLeave={() => schedulePreviewClose(row.id)}
                            >
                              <span className={cn("h-2.5 w-2.5 shrink-0 rounded-full", riskDotClass(row.failureRate))} />
                              <div className="min-w-0">
                                <div className="flex items-center gap-2">
                                  <span className="truncate font-mono text-xs font-semibold">{row.channelCode}</span>
                                  {selectedId === row.id && (
                                    <Badge variant="outline" className="shrink-0 border-primary/20 bg-primary/5 text-[10px] text-primary">固定</Badge>
                                  )}
                                </div>
                                <div className="truncate text-xs text-muted-foreground" title={`${row.supplier} / ${row.model} / ${row.deployName} @ ${row.forwardHost}`}>
                                  {row.supplier} / {row.model} / {row.deployName} @ {row.forwardHost}
                                </div>
                              </div>
                            </div>
                          </PopoverTrigger>
                          <PopoverContent
                            side="right"
                            align="start"
                            sideOffset={12}
                            onOpenAutoFocus={(event) => event.preventDefault()}
                            onMouseEnter={keepPreviewOpen}
                            onMouseLeave={() => schedulePreviewClose(row.id)}
                            className="w-[920px] max-w-[calc(100vw-3rem)] p-0"
                          >
                            <ChannelDetailInline selected={row} mode="popover" />
                          </PopoverContent>
                        </Popover>
                      </td>
                      <td className={cn("px-2 py-3 text-right font-medium", failureTextClass(row.failureRate))}>
                        {formatPercent(row.failureRate)}
                      </td>
                      <td className="px-2 py-3"><CountRateCell count={row.rate429Count} total={row.requestCount} /></td>
                      <td className="px-2 py-3"><CountRateCell count={row.error5xxCount} total={row.requestCount} /></td>
                      <td className="px-2 py-3"><CountRateCell count={row.clientErrorCount} total={row.requestCount} /></td>
                      <td className="px-2 py-3"><CountRateCell count={otherErrorCount(row)} total={row.requestCount} /></td>
                      <td className="overflow-hidden px-4 py-2">
                        <SampleTimeline samples={row.samples} loading={timelineLoading} />
                      </td>
                      {showOfflineActionColumn && <td className="px-2 py-3" />}
                    </tr>
                    {selectedId === row.id && (
                      <tr className="border-b bg-muted/20">
                        <td colSpan={detailColSpan} className="px-4 py-4">
                          <div className="pl-8">
                            <ChannelDetailInline selected={row} />
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </Fragment>
            )
          }) : loading ? (
            Array.from({ length: 8 }).map((_, index) => (
              <tr key={`loading-${index}`} className="border-b">
                <td className="px-4 py-4">
                  <div className="flex items-center gap-3">
                    <div className="h-3 w-3 rounded-full bg-muted animate-pulse" />
                    <div className="min-w-0 flex-1">
                      <div className="h-4 w-40 rounded bg-muted animate-pulse" />
                      <div className="mt-2 h-3 w-28 rounded bg-muted/70 animate-pulse" />
                    </div>
                  </div>
                </td>
                <td className="px-2 py-4"><div className="ml-auto h-4 w-14 rounded bg-muted animate-pulse" /></td>
                <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
                <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
                <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
                <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
                <td className="overflow-hidden px-4 py-4">
                  <div
                    className="grid h-5 w-full min-w-0 gap-[2px]"
                    style={{ gridTemplateColumns: "repeat(48, minmax(2px, 1fr))" }}
                  >
                    {Array.from({ length: 48 }).map((__, itemIndex) => (
                      <div key={itemIndex} className="h-[18px] rounded-[2px] bg-muted animate-pulse" />
                    ))}
                  </div>
                </td>
                {showOfflineActionColumn && <td className="px-2 py-4" />}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={detailColSpan} className="px-4 py-12 text-center text-muted-foreground">
                当前筛选条件下暂无 LLM 渠道样本
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
})

type DistributionMode = "ttft" | "tps"

function distributionCellClass(row: BucketDistributionRow, label: string, count: number, mode: DistributionMode) {
  if (row.confidence === "none" || count <= 0) {
    return "bg-slate-50 text-slate-400"
  }
  if (row.confidence === "low") {
    return "bg-amber-50 text-amber-700"
  }
  if (mode === "tps") {
    if (label.startsWith("<=")) {
      return "bg-rose-50 text-rose-700"
    }
    if (label.startsWith("5/")) {
      return "bg-orange-50 text-orange-700"
    }
    if (label.startsWith("10/")) {
      return "bg-amber-50 text-amber-700"
    }
    return "bg-emerald-50 text-emerald-700"
  }
  if (label === "<=500ms" || label === "500ms-2s") {
    return "bg-emerald-50 text-emerald-700"
  }
  if (label.startsWith("2s")) {
    return "bg-amber-50 text-amber-700"
  }
  if (label.startsWith("15s")) {
    return "bg-orange-50 text-orange-700"
  }
  return "bg-rose-50 text-rose-700"
}

function DistributionTable({
  rows,
  loading,
  rowHeader,
  mode,
  emptyText,
}: {
  rows: BucketDistributionRow[]
  loading: boolean
  rowHeader: string
  mode: DistributionMode
  emptyText: string
}) {
  if (loading) {
    return (
      <div className="rounded-md border bg-muted/10 p-3">
        <div className="flex items-center justify-between gap-2">
          <div className="h-4 w-44 rounded bg-muted animate-pulse" />
          <div className="h-3 w-16 rounded bg-muted animate-pulse" />
        </div>
        <div className="mt-3 grid gap-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-8 rounded bg-muted/70 animate-pulse" />
          ))}
        </div>
      </div>
    )
  }

  const totalSamples = rows.reduce((sum, row) => sum + row.sampleCount, 0)
  const distributionColumns = rows[0]?.cells.length || 0

  if (rows.length === 0 || totalSamples <= 0) {
    return (
      <div className="flex h-28 items-center justify-center rounded-md border border-dashed text-xs text-muted-foreground">
        {emptyText}
      </div>
    )
  }

  return (
    <>
      <div className="overflow-x-auto pb-1">
        <div
          className="grid min-w-[720px] gap-px overflow-hidden rounded-md border bg-border text-[11px]"
          style={{ gridTemplateColumns: `112px 72px repeat(${distributionColumns}, minmax(64px, 1fr))` }}
        >
          <div className="bg-muted/60 px-2 py-2 text-[10px] text-muted-foreground">{rowHeader}</div>
          <div className="bg-muted/60 px-2 py-2 text-[10px] text-muted-foreground">覆盖</div>
          {rows[0]?.cells.map((cell) => (
            <div key={cell.label} className="bg-muted/60 px-2 py-2 text-[10px] text-muted-foreground">
              {cell.label}
            </div>
          ))}
          {rows.map((row) => (
            <Fragment key={row.bucket}>
              <div className="bg-background px-2 py-1.5">
                <div className="truncate font-mono font-semibold">{row.bucket}</div>
                <div className="mt-0.5 text-[10px] tabular-nums text-muted-foreground">n={formatCompact(row.sampleCount, 0)}</div>
              </div>
              <div className={cn("px-2 py-1.5 font-semibold tabular-nums", row.confidence === "low" ? "bg-amber-50 text-amber-700" : "bg-background text-foreground")}>
                {formatPercent(row.coverage)}
              </div>
              {row.cells.map((cell) => (
                <div
                  key={`${row.bucket}-${cell.label}`}
                  className={cn("px-2 py-1.5 font-semibold tabular-nums", distributionCellClass(row, cell.label, cell.count, mode))}
                  title={`${row.bucket} · ${cell.label} · ${formatPercent(cell.ratio)} · n=${formatCompact(cell.count, 0)}`}
                >
                  {row.confidence === "none" ? "-" : formatPercent(cell.ratio)}
                </div>
              ))}
            </Fragment>
          ))}
        </div>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-3 text-[10px] text-muted-foreground">
        {mode === "ttft" ? (
          <>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-emerald-50 ring-1 ring-emerald-200" />2s 内</span>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-amber-50 ring-1 ring-amber-200" />2s-15s</span>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-orange-50 ring-1 ring-orange-200" />15s-30s</span>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-rose-50 ring-1 ring-rose-200" />30s+</span>
          </>
        ) : (
          <>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-rose-50 ring-1 ring-rose-200" />低速</span>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-amber-50 ring-1 ring-amber-200" />中速</span>
            <span className="inline-flex items-center gap-1"><span className="h-2.5 w-4 rounded-sm bg-emerald-50 ring-1 ring-emerald-200" />高速</span>
          </>
        )}
      </div>
    </>
  )
}

function MetricDistributionTabs({
  ttftRows,
  tpsRows,
  loading,
}: {
  ttftRows: BucketDistributionRow[]
  tpsRows: BucketDistributionRow[]
  loading: boolean
}) {
  return (
    <div className="rounded-md border bg-muted/10 p-3">
      <Tabs defaultValue="ttft">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="text-xs font-semibold text-muted-foreground">当前分布</div>
          <TabsList className="h-8">
            <TabsTrigger value="ttft" className="px-2 py-1 text-xs">TTFT / Input Tokens</TabsTrigger>
            <TabsTrigger value="tps" className="px-2 py-1 text-xs">TPS / Output Tokens</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="ttft" className="mt-3">
          <DistributionTable
            rows={ttftRows}
            loading={loading}
            rowHeader="Input Tokens"
            mode="ttft"
            emptyText="当前时间范围无 TTFT 分布样本"
          />
        </TabsContent>
        <TabsContent value="tps" className="mt-3">
          <DistributionTable
            rows={tpsRows}
            loading={loading}
            rowHeader="Output Tokens"
            mode="tps"
            emptyText="当前时间范围无 TPS 分布样本"
          />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function ChannelDetailInline({ selected, mode = "inline" }: { selected: ChannelHealthRow; mode?: "inline" | "popover" }) {
  const compact = mode === "popover"
  const trendLegend = [
    { label: "TTFT P95", color: "#dc2626" },
    { label: "TTFT P50", color: "#f97316" },
    { label: "TPS P50", color: "#0891b2" },
    { label: "TPS P10", color: "#6366f1" },
  ]
  const trendData = useMemo(() => {
    return selected.performanceTrend.map((point) => ({
      time: point.label,
      ttftAvgMs: point.ttftAvgMs,
      ttftP95Ms: point.ttftP95Ms,
      tpsAvg: point.tpsAvg,
      tpsP10: point.tpsP10,
    }))
  }, [selected])
  const hasPerformanceTrend = trendData.some((point) =>
    point.ttftAvgMs !== null ||
    point.ttftP95Ms !== null ||
    point.tpsAvg !== null ||
    point.tpsP10 !== null
  )
  const renderTrend = (className?: string) => (
    <div className={className}>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-semibold text-muted-foreground">
        <span>TTFT / TPS 性能走势</span>
        {trendLegend.map((item) => (
          <span key={item.label} className="inline-flex items-center gap-1 text-[10px] font-normal">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
            {item.label}
          </span>
        ))}
      </div>
      {hasPerformanceTrend ? (
        <div className={cn("mt-2 min-w-0 overflow-visible", compact ? "h-[150px]" : "h-[168px]")}>
          <ResponsiveContainer
            width="100%"
            height="100%"
            minWidth={0}
            minHeight={compact ? 150 : 168}
            initialDimension={{ width: compact ? 520 : 680, height: compact ? 150 : 168 }}
          >
            <LineChart data={trendData} margin={{ left: 6, right: 6, top: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
              <XAxis dataKey="time" tick={false} axisLine={false} tickLine={false} />
              <YAxis
                yAxisId="ttft"
                tick={{ fontSize: 10 }}
                tickFormatter={(value) => formatAxisMilliseconds(Number(value))}
                tickMargin={4}
                axisLine={false}
                tickLine={false}
                width={48}
              />
              <YAxis
                yAxisId="tps"
                orientation="right"
                tick={{ fontSize: 10 }}
                tickFormatter={(value) => formatCompact(Number(value), 0)}
                tickMargin={4}
                axisLine={false}
                tickLine={false}
                width={42}
              />
              <RechartsTooltip
                formatter={(value, name) => [
                  name === "ttftAvgMs" || name === "ttftP95Ms"
                    ? name === "ttftP95Ms" ? formatTtftTail(Number(value)) : formatMilliseconds(Number(value))
                    : formatTps(Number(value)),
                  name === "ttftAvgMs" ? "TTFT P50" :
                    name === "ttftP95Ms" ? "TTFT P95" :
                      name === "tpsAvg" ? "TPS P50" : "TPS P10",
                ]}
              />
              <Line yAxisId="ttft" type="monotone" dataKey="ttftP95Ms" stroke="#dc2626" strokeWidth={2} dot={false} connectNulls />
              <Line yAxisId="ttft" type="monotone" dataKey="ttftAvgMs" stroke="#f97316" strokeWidth={1.5} dot={false} connectNulls />
              <Line yAxisId="tps" type="monotone" dataKey="tpsAvg" stroke="#0891b2" strokeWidth={1.5} dot={false} connectNulls />
              <Line yAxisId="tps" type="monotone" dataKey="tpsP10" stroke="#6366f1" strokeWidth={2} dot={false} connectNulls />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className={cn("mt-2 flex items-center justify-center rounded-md border border-dashed text-xs text-muted-foreground", compact ? "h-[150px]" : "h-[168px]")}>当前时间范围无性能趋势</div>
      )}
    </div>
  )

  if (compact) {
    const metricCards = [
      { label: "TTFT P50", value: metricText(selected.ttftAvgMs, formatMilliseconds) },
      { label: "TTFT P95", value: metricText(selected.ttftP95Ms, formatTtftTail) },
      { label: "TPS P50", value: metricText(selected.tpsAvg, formatTps) },
      { label: "TPS P10", value: metricText(selected.tpsP10, formatTps) },
      { label: "P10/P50", value: metricRatioText(selected.tpsP10, selected.tpsAvg) },
    ]

    return (
      <div className="rounded-md border bg-background p-3 shadow-sm">
        <div className="grid gap-4 lg:grid-cols-[220px_minmax(420px,1fr)]">
          <div className="min-w-0">
            <div className="text-xs font-semibold text-muted-foreground">渠道预览</div>
            <div className="mt-2 truncate font-mono text-xs font-semibold" title={selected.channelCode}>{selected.channelCode}</div>
            <div className="mt-3 grid gap-2 text-xs">
              <div className="flex min-w-0 justify-between gap-3"><span className="shrink-0 text-muted-foreground">Deploy Name</span><span className="min-w-0 truncate font-mono font-semibold" title={selected.deployName}>{selected.deployName}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">请求数</span><span className="font-semibold">{formatCompact(selected.requestCount, 0)}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">RPM</span><span className="font-semibold">{formatCompact(selected.rpm)}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Output TPM</span><span className="font-semibold">{formatCompact(selected.outputTokenTpm)}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">渠道异常率</span><span className={cn("font-semibold", failureTextClass(selected.failureRate))}>{formatPercent(selected.failureRate)}</span></div>
            </div>
          </div>

          <div className="min-w-0">
            <div className="mb-3 grid grid-cols-5 gap-2">
              {metricCards.map((item) => (
                <div key={item.label} className="min-w-0 rounded-md border bg-muted/20 px-2 py-1.5">
                  <div className="truncate text-[10px] text-muted-foreground">{item.label}</div>
                  <div className="mt-0.5 truncate text-xs font-semibold tabular-nums">{item.value}</div>
                </div>
              ))}
            </div>
            {renderTrend()}
          </div>
        </div>
      </div>
    )
  }

  const distributionLoading =
    selected.ttftAvgMs.status === "loading" ||
    selected.ttftP95Ms.status === "loading" ||
    selected.tpsAvg.status === "loading" ||
    selected.tpsP10.status === "loading"

  return (
    <div className="rounded-md border bg-background p-3 shadow-sm">
      <div className="grid gap-4 xl:grid-cols-[220px_minmax(0,1fr)]">
        <div className="min-w-0">
          <div className="text-xs font-semibold text-muted-foreground">渠道详情</div>
          <div className="mt-2 truncate font-mono text-xs font-semibold">{selected.channelCode}</div>
          <div className="mt-3 grid gap-2 text-xs">
            <div className="flex min-w-0 justify-between gap-3"><span className="shrink-0 text-muted-foreground">Deploy Name</span><span className="min-w-0 truncate font-mono font-semibold" title={selected.deployName}>{selected.deployName}</span></div>
            <div className="flex justify-between gap-3"><span className="text-muted-foreground">请求数</span><span className="font-semibold">{formatCompact(selected.requestCount, 0)}</span></div>
            <div className="flex justify-between gap-3"><span className="text-muted-foreground">RPM</span><span className="font-semibold">{formatCompact(selected.rpm)}</span></div>
            <div className="flex justify-between gap-3"><span className="text-muted-foreground">Output TPM</span><span className="font-semibold">{formatCompact(selected.outputTokenTpm)}</span></div>
            <div className="flex justify-between gap-3"><span className="text-muted-foreground">渠道异常率</span><span className={cn("font-semibold", failureTextClass(selected.failureRate))}>{formatPercent(selected.failureRate)}</span></div>
            <div className="flex justify-between gap-3"><span className="text-muted-foreground">请求侧 4xx</span><span className="font-semibold text-muted-foreground">{formatPercent(selected.clientErrorRate)}</span></div>
          </div>
        </div>

        {renderTrend()}

        <div className="min-w-0 xl:col-span-2">
          <MetricDistributionTabs
            ttftRows={selected.ttftDistribution || []}
            tpsRows={selected.tpsDistribution || []}
            loading={distributionLoading}
          />
        </div>
      </div>
    </div>
  )
}

function detailCacheKey(row: ChannelHealthRow, filters: ChannelHealthQueryFilters) {
  return [
    row.id,
    filters.timePreset.label,
    filters.timePreset.rateWindow,
    filters.model.trim(),
    filters.supplier.trim(),
    filters.channelCode.trim(),
  ].join("||")
}

function rowHasDetailData(row: ChannelHealthRow) {
  return row.performanceTrend.length > 0 ||
    (row.ttftDistribution?.some((distributionRow) => distributionRow.sampleCount > 0) || false) ||
    (row.tpsDistribution?.some((distributionRow) => distributionRow.sampleCount > 0) || false) ||
    row.inputTokenBuckets.some((bucket) => bucket.avg.status === "available" || bucket.tail.status === "available") ||
    row.outputTokenBuckets.some((bucket) => bucket.avg.status === "available" || bucket.tail.status === "available")
}

function preserveDetailRows(
  nextData: ChannelHealthDashboardData,
  previousData: ChannelHealthDashboardData,
  groupBy: ChannelHealthFilters["groupBy"]
) {
  const previousRows = new Map(previousData.rows.map((row) => [row.id, row]))
  const rows = nextData.rows.map((row) => {
    const previous = previousRows.get(row.id)
    if (!previous) {
      return row
    }

    const withPreviousSamples = {
      ...row,
      samples: previous.samples.length > 0 ? previous.samples : row.samples,
    }

    if (!rowHasDetailData(previous)) {
      return withPreviousSamples
    }

    return {
      ...withPreviousSamples,
      ttftAvgMs: previous.ttftAvgMs,
      ttftP95Ms: previous.ttftP95Ms,
      tpsAvg: previous.tpsAvg,
      tpsP10: previous.tpsP10,
      inputTokenBuckets: previous.inputTokenBuckets,
      outputTokenBuckets: previous.outputTokenBuckets,
      ttftDistribution: previous.ttftDistribution || [],
      tpsDistribution: previous.tpsDistribution || [],
      performanceTrend: previous.performanceTrend,
    }
  })

  return regroupChannelHealthData({ ...nextData, rows }, groupBy)
}

const OFFLINE_PLAN_CHANGED_MESSAGE = "软链关系已变化，请重新确认后下线"
const OFFLINE_TOPOLOGY_NODE_MIN_WIDTH = 150
const OFFLINE_TOPOLOGY_NODE_HEIGHT = 36
const OFFLINE_TOPOLOGY_COLUMN_GAP = 64
const OFFLINE_TOPOLOGY_ROW_GAP = 64
const OFFLINE_TOPOLOGY_PADDING_X = 120
const OFFLINE_TOPOLOGY_PADDING_Y = 52

type OfflineTopologyNode = {
  modelName: string
  level: number
  x: number
  y: number
  width: number
  target: boolean
  linked: boolean
}

type OfflineTopologyEdge = {
  from: string
  to: string
  fromX: number
  fromY: number
  toX: number
  toY: number
  path: string
}

function modelNameVisualLength(modelName: string) {
  return Array.from(modelName).reduce((length, char) => length + (char.charCodeAt(0) > 255 ? 2 : 1), 0)
}

function offlineTopologyNodeWidth(modelName: string) {
  const textWidth = modelNameVisualLength(modelName) * 7.5
  return Math.max(OFFLINE_TOPOLOGY_NODE_MIN_WIDTH, Math.ceil(textWidth + 28))
}

function buildOfflineModelLevels(plan: ModelOfflinePlan) {
  const levels = new Map<string, number>([[plan.root, 0]])
  const childrenByParent = new Map<string, string[]>()

  plan.edges.forEach((edge) => {
    const children = childrenByParent.get(edge.to) || []
    children.push(edge.from)
    childrenByParent.set(edge.to, children)
  })

  const queue = [plan.root]
  for (let index = 0; index < queue.length; index += 1) {
    const current = queue[index]
    const currentLevel = levels.get(current) || 0
    const children = childrenByParent.get(current) || []
    children.forEach((child) => {
      if (!levels.has(child)) {
        levels.set(child, currentLevel + 1)
        queue.push(child)
      }
    })
  }

  return levels
}

function buildOfflineTopology(plan: ModelOfflinePlan) {
  const levels = buildOfflineModelLevels(plan)
  const linkedModels = new Set(plan.edges.map((edge) => edge.from))
  const maxLevel = Math.max(0, ...Array.from(levels.values()))
  const groups = new Map<number, string[]>()

  plan.affectedModels.forEach((modelName) => {
    const level = levels.get(modelName)
    if (level === undefined) {
      return
    }
    const group = groups.get(level) || []
    group.push(modelName)
    groups.set(level, group)
  })

  const maxRows = Math.max(1, ...Array.from(groups.values()).map((group) => group.length))
  const height = Math.max(220, OFFLINE_TOPOLOGY_PADDING_Y * 2 + (maxRows - 1) * OFFLINE_TOPOLOGY_ROW_GAP)
  const levelWidths = Array.from({ length: maxLevel + 1 }, (_, level) => {
    const group = groups.get(level) || []
    return Math.max(
      OFFLINE_TOPOLOGY_NODE_MIN_WIDTH,
      ...group.map((modelName) => offlineTopologyNodeWidth(modelName))
    )
  })
  const levelStarts = levelWidths.reduce<number[]>((starts, width, index) => {
    if (index === 0) {
      starts.push(OFFLINE_TOPOLOGY_PADDING_X)
    } else {
      starts.push(starts[index - 1] + levelWidths[index - 1] + OFFLINE_TOPOLOGY_COLUMN_GAP)
    }
    return starts
  }, [])
  const width = OFFLINE_TOPOLOGY_PADDING_X * 2 +
    levelWidths.reduce((total, levelWidth) => total + levelWidth, 0) +
    Math.max(0, levelWidths.length - 1) * OFFLINE_TOPOLOGY_COLUMN_GAP
  const nodes = plan.affectedModels
    .map((modelName) => {
      const level = levels.get(modelName)
      if (level === undefined) {
        return null
      }
      const group = groups.get(level) || [modelName]
      const index = group.indexOf(modelName)
      const groupHeight = (group.length - 1) * OFFLINE_TOPOLOGY_ROW_GAP
      const groupOffset = (height - OFFLINE_TOPOLOGY_PADDING_Y * 2 - groupHeight) / 2
      const target = modelName === plan.root
      const nodeWidth = offlineTopologyNodeWidth(modelName)
      return {
        modelName,
        level,
        x: levelStarts[level] + levelWidths[level] / 2,
        y: OFFLINE_TOPOLOGY_PADDING_Y + groupOffset + index * OFFLINE_TOPOLOGY_ROW_GAP,
        width: nodeWidth,
        target,
        linked: linkedModels.has(modelName),
      }
    })
    .filter((node): node is OfflineTopologyNode => node !== null)
  const nodeByName = new Map(nodes.map((node) => [node.modelName, node]))

  return {
    height,
    width: Math.max(560, width),
    nodes,
    edges: plan.edges.flatMap((edge): OfflineTopologyEdge[] => {
      const from = nodeByName.get(edge.from)
      const to = nodeByName.get(edge.to)
      if (!from || !to) {
        return []
      }
      return [{
        from: edge.from,
        to: edge.to,
        fromX: from.x - from.width / 2,
        fromY: from.y,
        toX: to.x + to.width / 2,
        toY: to.y,
        path: `M ${from.x - from.width / 2} ${from.y} C ${from.x - from.width / 2 - OFFLINE_TOPOLOGY_COLUMN_GAP / 2} ${from.y}, ${to.x + to.width / 2 + OFFLINE_TOPOLOGY_COLUMN_GAP / 2} ${to.y}, ${to.x + to.width / 2} ${to.y}`,
      }]
    }),
  }
}

function OfflinePlanTopology({
  plan,
}: {
  plan: ModelOfflinePlan
}) {
  const topology = useMemo(() => buildOfflineTopology(plan), [plan])

  return (
    <div className="max-h-[60vh] overflow-auto rounded-md border bg-background p-3 shadow-inner">
      <div className="relative" style={{ width: topology.width, height: topology.height }}>
        <div className="absolute inset-0 rounded-md bg-[radial-gradient(circle_at_1px_1px,hsl(var(--muted-foreground)/0.16)_1px,transparent_0)] [background-size:18px_18px]" />
        <svg className="absolute inset-0 h-full w-full" viewBox={`0 0 ${topology.width} ${topology.height}`} aria-hidden="true">
          <defs>
            <marker id="offline-plan-arrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L8,4 L0,8 Z" className="fill-amber-500" />
            </marker>
          </defs>
          {topology.edges.map((edge) => (
            <path
              key={`${edge.from}->${edge.to}`}
              d={edge.path}
              className="fill-none stroke-amber-500/65"
              strokeWidth="1.8"
              strokeLinecap="round"
              markerEnd="url(#offline-plan-arrow)"
            />
          ))}
          {topology.edges.map((edge) => (
            <Fragment key={`${edge.from}->${edge.to}:dots`}>
              <circle cx={edge.fromX} cy={edge.fromY} r="3" className="fill-cyan-400 stroke-background" strokeWidth="1.5" />
              <circle cx={edge.toX} cy={edge.toY} r="3" className="fill-rose-400 stroke-background" strokeWidth="1.5" />
            </Fragment>
          ))}
        </svg>
        {topology.nodes.map((node) => (
          <div
            key={node.modelName}
            className={cn(
              "absolute flex -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-md border bg-background px-3 text-center text-xs shadow-sm ring-1 ring-border/50",
              node.linked && "border-cyan-300 bg-cyan-50/95 text-cyan-950 ring-cyan-100",
              node.target && "border-rose-300 bg-rose-50/95 text-rose-950 ring-rose-100"
            )}
            style={{ left: node.x, top: node.y, width: node.width, height: OFFLINE_TOPOLOGY_NODE_HEIGHT }}
          >
            {node.target && (
              <Badge
                variant="outline"
                className="absolute right-[calc(100%+8px)] top-1/2 -translate-y-1/2 border-rose-300 bg-rose-50 text-rose-700 shadow-sm"
              >
                目标
              </Badge>
            )}
            <span className="min-w-0 truncate font-medium" title={node.modelName}>{node.modelName}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export function ChannelHealthDashboard() {
  const { user } = useAuth()
  const [groupBy, setGroupBy] = useState<ChannelHealthFilters["groupBy"]>(() => getDefaultChannelHealthFilters().groupBy)
  const latestGroupByRef = useRef<ChannelHealthFilters["groupBy"]>(groupBy)
  const [filters, setFilters] = useState<ChannelHealthQueryFilters>(() => {
    const defaults = getDefaultChannelHealthFilters()
    return {
      model: defaults.model,
      supplier: defaults.supplier,
      channelCode: defaults.channelCode,
      timePreset: defaults.timePreset,
    }
  })
  const [modelInput, setModelInput] = useState("")
  const [supplierInput, setSupplierInput] = useState("")
  const modelDebounceRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  const supplierDebounceRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  const [data, setData] = useState<ChannelHealthDashboardData>(emptyData)
  const [selectedId, setSelectedId] = useState<string>()
  const [previewId, setPreviewId] = useState<string>()
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set())
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState<ChannelHealthPageSize>(50)
  const [sortBy, setSortBy] = useState<ChannelHealthSortBy>("failureRate")
  const [refreshIntervalSeconds, setRefreshIntervalSeconds] = useState(30)
  const [refreshNonce, setRefreshNonce] = useState(0)
  const [loading, setLoading] = useState(false)
  const [timelineLoading, setTimelineLoading] = useState(false)
  const [offlineTargetModel, setOfflineTargetModel] = useState<string | null>(null)
  const [offlineSubmitting, setOfflineSubmitting] = useState(false)
  const [offlinePlanLoading, setOfflinePlanLoading] = useState(false)
  const [offlinePlan, setOfflinePlan] = useState<ModelOfflinePlan | null>(null)
  const [offlinePlanError, setOfflinePlanError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const dataRef = useRef<ChannelHealthDashboardData>(emptyData)
  const detailControllerRef = useRef<AbortController>(undefined)
  const detailCacheRef = useRef<Map<string, ChannelHealthDetailData>>(new Map())
  const offlinePlanRequestRef = useRef(0)

  const resetViewForQueryChange = useCallback(() => {
    setPage(1)
    setSelectedId(undefined)
    setPreviewId(undefined)
    setExpandedGroups(new Set())
  }, [])

  const updateQueryFilters = useCallback((updater: (current: ChannelHealthQueryFilters) => ChannelHealthQueryFilters) => {
    resetViewForQueryChange()
    setFilters(updater)
  }, [resetViewForQueryChange])

  const handleModelChange = useCallback((value: string) => {
    setModelInput(value)
    if (modelDebounceRef.current) {
      clearTimeout(modelDebounceRef.current)
    }
    modelDebounceRef.current = setTimeout(() => {
      updateQueryFilters((current) => ({ ...current, model: value }))
    }, 400)
  }, [updateQueryFilters])

  const handleSupplierChange = useCallback((value: string) => {
    setSupplierInput(value)
    if (supplierDebounceRef.current) {
      clearTimeout(supplierDebounceRef.current)
    }
    supplierDebounceRef.current = setTimeout(() => {
      updateQueryFilters((current) => ({ ...current, supplier: value }))
    }, 400)
  }, [updateQueryFilters])

  const commitModelFilter = useCallback(() => {
    if (modelDebounceRef.current) {
      clearTimeout(modelDebounceRef.current)
      modelDebounceRef.current = undefined
    }
    updateQueryFilters((current) => ({ ...current, model: modelInput }))
  }, [modelInput, updateQueryFilters])

  const commitSupplierFilter = useCallback(() => {
    if (supplierDebounceRef.current) {
      clearTimeout(supplierDebounceRef.current)
      supplierDebounceRef.current = undefined
    }
    updateQueryFilters((current) => ({ ...current, supplier: supplierInput }))
  }, [supplierInput, updateQueryFilters])

  const handleGroupByChange = useCallback((nextGroupBy: ChannelHealthFilters["groupBy"]) => {
    if (nextGroupBy === groupBy) {
      return
    }
    setPage(1)
    setSelectedId(undefined)
    setPreviewId(undefined)
    setExpandedGroups(new Set())
    latestGroupByRef.current = nextGroupBy
    setGroupBy(nextGroupBy)
  }, [groupBy])

  const resetConfiguration = useCallback(() => {
    const defaults = getDefaultChannelHealthFilters()
    if (modelDebounceRef.current) {
      clearTimeout(modelDebounceRef.current)
      modelDebounceRef.current = undefined
    }
    if (supplierDebounceRef.current) {
      clearTimeout(supplierDebounceRef.current)
      supplierDebounceRef.current = undefined
    }

    setModelInput("")
    setSupplierInput("")
    setSortBy("failureRate")
    setPageSize(50)
    setRefreshIntervalSeconds(30)
    updateQueryFilters(() => ({
      model: defaults.model,
      supplier: defaults.supplier,
      channelCode: defaults.channelCode,
      timePreset: defaults.timePreset,
    }))
  }, [updateQueryFilters])

  useEffect(() => {
    latestGroupByRef.current = groupBy
    const nextData = regroupChannelHealthData(dataRef.current, groupBy)
    dataRef.current = nextData
    setData(nextData)
  }, [groupBy])

  useEffect(() => {
    const coreController = new AbortController()
    const timelineController = new AbortController()
    setLoading(true)
    setTimelineLoading(false)
    setError(null)
    detailCacheRef.current.clear()

    const requestFilters: ChannelHealthFilters = { ...filters, groupBy: latestGroupByRef.current }
    const timelineQueryPromise = queryTimelineData(requestFilters, timelineController.signal)
      .then((timelineData) => ({ status: "fulfilled" as const, timelineData }))
      .catch(() => ({ status: "rejected" as const }))

    getCoreData(requestFilters, coreController.signal)
      .then((result) => {
        if (coreController.signal.aborted) {
          return
        }

        const nextData = regroupChannelHealthData(result, latestGroupByRef.current)
        const preservedData = preserveDetailRows(nextData, dataRef.current, latestGroupByRef.current)
        dataRef.current = preservedData
        startTransition(() => {
          setData(preservedData)
          setSelectedId((current) => current && nextData.rows.some((row) => row.id === current) ? current : undefined)
          setPreviewId((current) => current && nextData.rows.some((row) => row.id === current) ? current : undefined)
          setExpandedGroups((current) => {
            return new Set(Array.from(current).filter((id) => nextData.groups.some((group) => group.id === id)))
          })
        })

        setLoading(false)
        setTimelineLoading(result.rows.length > 0)

        if (result.rows.length === 0) {
          timelineController.abort()
          setTimelineLoading(false)
          return
        }

        timelineQueryPromise
          .then((timelineResult) => {
            if (timelineController.signal.aborted) {
              return
            }
            if (timelineResult.status === "rejected") {
              setTimelineLoading(false)
              return
            }

            const samplesByRow = buildTimelineSamples(result.rows, timelineResult.timelineData)
            const nextData = mergeTimelineSamples(dataRef.current, samplesByRow, latestGroupByRef.current)
            dataRef.current = nextData
            startTransition(() => {
              setData(nextData)
              setTimelineLoading(false)
            })
          })
      })
      .catch((err) => {
        if (coreController.signal.aborted) {
          return
        }
        timelineController.abort()
        dataRef.current = emptyData
        setData(emptyData)
        setError(err instanceof Error ? err.message : "Prometheus 查询失败")
        setLoading(false)
        setTimelineLoading(false)
      })

    return () => {
      coreController.abort()
      timelineController.abort()
    }
  }, [filters, refreshNonce])

  const detailTargetId = previewId || selectedId

  useEffect(() => {
    if (!detailTargetId) {
      return undefined
    }

    const row = data.rows.find((r) => r.id === detailTargetId)
    if (!row) {
      return undefined
    }

    if (detailControllerRef.current) {
      detailControllerRef.current.abort()
    }
    const controller = new AbortController()
    detailControllerRef.current = controller

    const scope: DetailScope = { selectedRow: row }
    const detailFilters: ChannelHealthFilters = { ...filters, groupBy }
    const cacheKey = detailCacheKey(row, filters)
    const cachedDetail = detailCacheRef.current.get(cacheKey)

    if (cachedDetail) {
      const nextData = mergeScopedDetailData(dataRef.current, cachedDetail, detailFilters, row.id)
      dataRef.current = nextData
      startTransition(() => {
        setData(nextData)
      })
      return undefined
    }

    getDetailData(detailFilters, controller.signal, scope)
      .then((detail) => {
        if (controller.signal.aborted) {
          return
        }
        detailCacheRef.current.set(cacheKey, detail)
        const nextData = mergeScopedDetailData(dataRef.current, detail, detailFilters, row.id)
        dataRef.current = nextData
        startTransition(() => {
          setData(nextData)
        })
      })
      .catch(() => {})

    return () => controller.abort()
  }, [detailTargetId, filters, groupBy])

  useEffect(() => {
    if (refreshIntervalSeconds <= 0) {
      return undefined
    }

    const timer = window.setInterval(() => {
      setRefreshNonce((current) => current + 1)
    }, refreshIntervalSeconds * 1000)

    return () => window.clearInterval(timer)
  }, [refreshIntervalSeconds])

  useEffect(() => {
    return () => {
      if (modelDebounceRef.current) {
        clearTimeout(modelDebounceRef.current)
      }
      if (supplierDebounceRef.current) {
        clearTimeout(supplierDebounceRef.current)
      }
      detailControllerRef.current?.abort()
    }
  }, [])

  const fetchOfflinePlan = useCallback(async (modelName: string) => {
    const requestId = offlinePlanRequestRef.current + 1
    offlinePlanRequestRef.current = requestId
    setOfflinePlan(null)
    setOfflinePlanError(null)
    setOfflinePlanLoading(true)
    try {
      const plan = await getModelOfflinePlan(modelName)
      if (offlinePlanRequestRef.current === requestId) {
        setOfflinePlan(plan)
      }
    } catch (err) {
      if (offlinePlanRequestRef.current === requestId) {
        setOfflinePlanError(err instanceof Error ? err.message : "下线影响计划加载失败")
      }
    } finally {
      if (offlinePlanRequestRef.current === requestId) {
        setOfflinePlanLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    if (!offlineTargetModel) {
      offlinePlanRequestRef.current += 1
      setOfflinePlan(null)
      setOfflinePlanError(null)
      setOfflinePlanLoading(false)
      return undefined
    }

    fetchOfflinePlan(offlineTargetModel)
    return undefined
  }, [fetchOfflinePlan, offlineTargetModel])

  const selected = data.rows.find((row) => row.id === selectedId)
  const canOfflineModel = useMemo(() => hasPermission(user, "/console/**"), [user])
  const initialLoading = loading && data.rows.length === 0
  const sortedGroups = useMemo(() => sortGroupsForView(data.groups, sortBy), [data.groups, sortBy])
  const totalPages = pageSize === "all" ? 1 : Math.max(1, Math.ceil(sortedGroups.length / pageSize))
  const currentPage = Math.min(page, totalPages)
  const pageStart = sortedGroups.length > 0 ? (pageSize === "all" ? 1 : (currentPage - 1) * pageSize + 1) : 0
  const pageEnd = pageSize === "all" ? sortedGroups.length : Math.min(currentPage * pageSize, sortedGroups.length)
  const pagedGroups = useMemo(() => {
    if (pageSize === "all") {
      return sortedGroups
    }
    const start = (currentPage - 1) * pageSize
    return sortedGroups.slice(start, start + pageSize)
  }, [currentPage, sortedGroups, pageSize])
  const activeFilterCount = [filters.model.trim(), filters.supplier.trim()].filter(Boolean).length

  useEffect(() => {
    if (page > totalPages) {
      setPage(totalPages)
    }
  }, [page, totalPages])

  const handleRefresh = useCallback(() => {
    setRefreshNonce((current) => current + 1)
  }, [])

  const toggleGroup = useCallback((groupId: string) => {
    const isClosing = expandedGroups.has(groupId)
    if (isClosing) {
      const group = data.groups.find((item) => item.id === groupId)
      if (group?.channels.some((channel) => channel.id === selectedId)) {
        setSelectedId(undefined)
      }
    }

    setExpandedGroups((current) => {
      const next = new Set(current)
      if (next.has(groupId)) {
        next.delete(groupId)
      } else {
        next.add(groupId)
      }
      return next
    })
  }, [data.groups, expandedGroups, selectedId])

  const handlePreviewChannel = useCallback((row: ChannelHealthRow) => {
    setPreviewId(row.id)
  }, [])

  const handleClearPreview = useCallback((rowId?: string) => {
    setPreviewId((current) => (!rowId || current === rowId ? undefined : current))
  }, [])

  const handleSelectChannel = useCallback((row: ChannelHealthRow) => {
    setSelectedId((current) => current === row.id ? undefined : row.id)
  }, [])

  const handleRequestOfflineModel = useCallback((modelName: string) => {
    setOfflineTargetModel(modelName)
  }, [])

  const handleConfirmOfflineModel = useCallback(async () => {
    if (!offlineTargetModel || !offlinePlan) {
      return
    }

    const targetModel = offlinePlan.root
    const expectedModels = offlinePlan.affectedModels
    setOfflineSubmitting(true)
    try {
      await offlineModel({
        modelName: targetModel,
        expectedModels,
      })
      toast.success(`模型 ${targetModel} 已下线，共影响 ${expectedModels.length} 个模型`)
      detailCacheRef.current.clear()
      setSelectedId(undefined)
      setPreviewId(undefined)
      setOfflineTargetModel(null)
      setRefreshNonce((current) => current + 1)
    } catch (err) {
      const message = err instanceof Error ? err.message : "模型下线失败"
      if (message.includes(OFFLINE_PLAN_CHANGED_MESSAGE)) {
        toast.error(OFFLINE_PLAN_CHANGED_MESSAGE)
        await fetchOfflinePlan(offlineTargetModel)
      } else {
        toast.error(message)
      }
    } finally {
      setOfflineSubmitting(false)
    }
  }, [fetchOfflinePlan, offlinePlan, offlineTargetModel])

  return (
    <div className="h-full overflow-y-auto bg-background">
      <TopBar
        title="渠道健康度大盘"
        description="Bella OpenAPI / LLM 能力点聚合"
        action={
          <div className="flex items-center gap-2">
            <div className="hidden items-center rounded-md border bg-muted p-1 xl:inline-flex">
              {GROUP_BY_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => handleGroupByChange(option.value)}
                  className={segmentButtonClass(groupBy === option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <Button
              size="sm"
              className="h-8 px-3"
              onClick={handleRefresh}
              disabled={loading}
            >
              <RefreshCcw className={cn("h-4 w-4", loading && "animate-spin")} />
              刷新
            </Button>

            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" size="sm" className="h-8 px-3">
                  <SlidersHorizontal className="h-4 w-4" />
                  配置
                  {activeFilterCount > 0 && (
                    <Badge variant="outline" className="ml-1 h-5 min-w-5 justify-center rounded-full border-primary/30 bg-primary/5 px-1 text-[10px] text-primary">
                      {activeFilterCount}
                    </Badge>
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent align="end" className="w-[360px] p-4">
                <div className="space-y-4">
                  <div>
                    <div className="text-sm font-semibold">看盘配置</div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      更新时间 {data.generatedAt || "-"}
                    </div>
                  </div>

                  <div className="grid gap-3">
                    <div className="grid gap-1.5">
                      <div className="text-xs font-medium text-muted-foreground">聚合维度</div>
                      <div className="inline-flex w-fit rounded-md border bg-muted p-1">
                        {GROUP_BY_OPTIONS.map((option) => (
                          <button
                            key={option.value}
                            type="button"
                            onClick={() => handleGroupByChange(option.value)}
                            className={segmentButtonClass(groupBy === option.value)}
                          >
                            {option.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div className="grid gap-1.5">
                      <div className="text-xs font-medium text-muted-foreground">时间范围</div>
                      <Select
                        value={String(CHANNEL_HEALTH_TIME_PRESETS.findIndex((preset) => preset.label === filters.timePreset.label))}
                        onValueChange={(value) => updateQueryFilters((current) => ({
                          ...current,
                          timePreset: CHANNEL_HEALTH_TIME_PRESETS[Number(value)] || CHANNEL_HEALTH_TIME_PRESETS.find((preset) => preset.minutes === 60) || CHANNEL_HEALTH_TIME_PRESETS[0],
                        }))}
                      >
                        <SelectTrigger className="h-8">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {CHANNEL_HEALTH_TIME_PRESETS.map((preset, index) => (
                            <SelectItem key={preset.label} value={String(index)}>{preset.label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="grid gap-1.5">
                      <div className="text-xs font-medium text-muted-foreground">自动刷新</div>
                      <Select
                        value={String(refreshIntervalSeconds)}
                        onValueChange={(value) => setRefreshIntervalSeconds(Number(value))}
                      >
                        <SelectTrigger className="h-8">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {REFRESH_INTERVAL_OPTIONS.map((option) => (
                            <SelectItem key={option.seconds} value={String(option.seconds)}>{option.label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="grid gap-1.5">
                      <div className="text-xs font-medium text-muted-foreground">模型</div>
                      <Input
                        id="model-filter"
                        value={modelInput}
                        onChange={(event) => handleModelChange(event.target.value)}
                        onBlur={commitModelFilter}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            commitModelFilter()
                          }
                        }}
                        placeholder="全部模型"
                        className="h-8"
                      />
                    </div>

                    <div className="grid gap-1.5">
                      <div className="text-xs font-medium text-muted-foreground">供应商</div>
                      <Input
                        id="supplier-filter"
                        value={supplierInput}
                        onChange={(event) => handleSupplierChange(event.target.value)}
                        onBlur={commitSupplierFilter}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            commitSupplierFilter()
                          }
                        }}
                        placeholder="全部供应商"
                        className="h-8"
                      />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div className="grid gap-1.5">
                        <div className="text-xs font-medium text-muted-foreground">排序</div>
                        <Select
                          value={sortBy}
                          onValueChange={(value) => {
                            setPage(1)
                            setSortBy(value as ChannelHealthSortBy)
                          }}
                        >
                          <SelectTrigger className="h-8">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {SORT_OPTIONS.map((option) => (
                              <SelectItem key={option.value} value={option.value}>按{option.label}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="grid gap-1.5">
                        <div className="text-xs font-medium text-muted-foreground">分页</div>
                        <Select
                          value={String(pageSize)}
                          onValueChange={(value) => {
                            setPage(1)
                            setPageSize(value === "all" ? "all" : Number(value) as ChannelHealthPageSize)
                          }}
                        >
                          <SelectTrigger className="h-8">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {PAGE_SIZE_OPTIONS.map((option) => (
                              <SelectItem key={String(option.value)} value={String(option.value)}>{option.label}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center justify-between border-t pt-3">
                    <div className="text-xs text-muted-foreground">
                      {filters.timePreset.label} · {refreshIntervalSeconds > 0 ? `${refreshIntervalSeconds}s 自动` : "手动刷新"} · {pageSize === "all" ? "全部" : `每页 ${pageSize}`}
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 px-2"
                      onClick={resetConfiguration}
                    >
                      <RotateCcw className="h-4 w-4" />
                      重置
                    </Button>
                  </div>
                </div>
              </PopoverContent>
            </Popover>
          </div>
        }
      />

      <main className="m-4 space-y-3">
        {error && (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            {error}
          </div>
        )}

        <Card className="rounded-lg">
          <CardHeader className="flex flex-col gap-2 border-b p-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <CardTitle className="text-base">
                <DashboardTitleInfo
                  data={data}
                  filters={filters}
                  groupBy={groupBy}
                  pageStart={pageStart}
                  pageEnd={pageEnd}
                  totalGroups={sortedGroups.length}
                />
              </CardTitle>
            </div>
            <div className="text-xs text-muted-foreground">
              {loading ? "数据刷新中" : timelineLoading ? "采样加载中" : `${currentPage} / ${totalPages}`}
            </div>
          </CardHeader>

          <ChannelGroupTable
            groups={pagedGroups}
            expandedGroups={expandedGroups}
            selectedId={selected?.id}
            previewId={previewId}
            loading={initialLoading}
            timelineLoading={timelineLoading}
            canOfflineModel={canOfflineModel}
            offlineModelName={offlineSubmitting ? offlineTargetModel || undefined : undefined}
            showOfflineActionColumn={canOfflineModel && groupBy === "model"}
            onToggleGroup={toggleGroup}
            onRequestOfflineModel={handleRequestOfflineModel}
            onPreviewChannel={handlePreviewChannel}
            onClearPreview={handleClearPreview}
            onSelectChannel={handleSelectChannel}
          />
          <CardContent className="flex flex-wrap items-center justify-end gap-3 border-t p-3 text-xs text-muted-foreground">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              disabled={currentPage <= 1}
            >
              <ChevronLeft className="h-4 w-4" />
              上一页
            </Button>
            <span className="min-w-[72px] text-center tabular-nums">{currentPage} / {totalPages}</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
              disabled={currentPage >= totalPages}
            >
              下一页
              <ChevronRight className="h-4 w-4" />
            </Button>
          </CardContent>
        </Card>
      </main>

      <Dialog
        open={offlineTargetModel !== null}
        onOpenChange={(open) => {
          if (!open && !offlineSubmitting) {
            setOfflineTargetModel(null)
          }
        }}
      >
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>下线影响计划确认</DialogTitle>
            <DialogDescription>
              确认后会将图中完整影响范围的模型设置为 inactive，并停用这些模型下的所有渠道。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 text-sm">
            {offlinePlanLoading && (
              <div className="flex items-center gap-2 rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                正在生成下线影响计划
              </div>
            )}
            {!offlinePlanLoading && offlinePlan && (
              <div className="space-y-3">
                <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-900">
                  <div className="font-medium">下线 {offlinePlan.root} 将影响下面图中 {offlinePlan.affectedModels.length} 个模型</div>
                </div>

                <div className="space-y-1.5">
                  <div className="text-xs font-medium text-muted-foreground">拓扑图</div>
                  <OfflinePlanTopology plan={offlinePlan} />
                </div>
              </div>
            )}
            {offlinePlanError && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {offlinePlanError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOfflineTargetModel(null)}
              disabled={offlineSubmitting}
            >
              取消
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleConfirmOfflineModel}
              disabled={offlineSubmitting || offlinePlanLoading || !offlinePlan || !offlineTargetModel}
            >
              {offlineSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              确认下线
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
