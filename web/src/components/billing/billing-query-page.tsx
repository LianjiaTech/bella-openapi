'use client'

import * as React from 'react'
import { TopBar } from '@/components/layout/top-bar'
import { Button } from '@/components/common/button'
import { Input } from '@/components/common/input'
import { Badge } from '@/components/common/badge'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/common/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/common/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/common/tooltip'
import { Pagination } from '@/components/ui/pagination'
import { useSidebar } from '@/components/providers'
import { flattenCategoryTrees } from '@/components/ui/modelFilterPanel/utils'
import { getModelsForSelection } from '@/lib/api/status'
import { getBillingAkOptions, queryAdminBillingRecords, queryBillingRecords } from '@/lib/api/billing'
import { BillingAkOption, BillingGranularity, BillingRecord, BillingRecordPage, BillingScope } from '@/lib/types/billing'
import { Model } from '@/lib/types/openapi'
import { cn } from '@/lib/utils'
import { CalendarDays, Check, ChevronDown, Info, ReceiptText, RotateCcw, Search } from 'lucide-react'

const ALL_VALUE = '__all__'
const DEFAULT_PAGE_SIZE = 10
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]
const BILLING_DATA_START_MONTH = '2026-06'
const BILLING_TIME_ZONE = 'Asia/Shanghai'
const DAY_MS = 24 * 60 * 60 * 1000
const CHAT_COMPLETIONS_ENDPOINT = '/v1/chat/completions'
const MESSAGES_ENDPOINT = '/v1/messages'
const RESPONSES_ENDPOINT = '/v1/responses'
const SMART_CHAT_LABEL = '智能问答'
const SMART_CHAT_QUERY_ENDPOINTS = [CHAT_COMPLETIONS_ENDPOINT, MESSAGES_ENDPOINT, RESPONSES_ENDPOINT]

interface BillingQueryPageProps {
  mode?: 'user' | 'admin';
}

interface DateParts {
  year: number;
  month: number;
  day: number;
}

function pad2(value: number): string {
  return value.toString().padStart(2, '0')
}

function shanghaiDateParts(date = new Date()): DateParts {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: BILLING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)

  const getPart = (type: string) => Number(parts.find(part => part.type === type)?.value)
  return {
    year: getPart('year'),
    month: getPart('month'),
    day: getPart('day'),
  }
}

function utcDatePartsFromMillis(value: number): DateParts {
  const date = new Date(value)
  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  }
}

function utcMillisFromDateParts(parts: DateParts): number {
  return Date.UTC(parts.year, parts.month - 1, parts.day)
}

function toDateInput(parts: DateParts): string {
  return `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}`
}

function previousMonthInput(): string {
  const today = shanghaiDateParts()
  const previous = new Date(Date.UTC(today.year, today.month - 2, 1))
  const previousMonth = `${previous.getUTCFullYear()}-${pad2(previous.getUTCMonth() + 1)}`
  return previousMonth < BILLING_DATA_START_MONTH ? BILLING_DATA_START_MONTH : previousMonth
}

function clampBillingMonth(value: string): string {
  return value && value < BILLING_DATA_START_MONTH ? BILLING_DATA_START_MONTH : value
}

function yesterdayInput(): string {
  const today = shanghaiDateParts()
  return toDateInput(utcDatePartsFromMillis(utcMillisFromDateParts(today) - DAY_MS))
}

function currentMonthFirstDayInput(): string {
  const today = shanghaiDateParts()
  return `${today.year}-${pad2(today.month)}-01`
}

function monthToPtRange(startMonth: string, endMonth: string): { startPt: string; endPt: string } {
  const [startYear, startMonthNumber] = startMonth.split('-').map(Number)
  const [endYear, endMonthNumber] = endMonth.split('-').map(Number)
  const lastDay = new Date(Date.UTC(endYear, endMonthNumber, 0)).getUTCDate()
  return {
    startPt: `${startYear}${pad2(startMonthNumber)}01000000`,
    endPt: `${endYear}${pad2(endMonthNumber)}${pad2(lastDay)}235959`,
  }
}

function dayToPtRange(startDay: string, endDay: string): { startPt: string; endPt: string } {
  return {
    startPt: `${startDay.replace(/-/g, '')}000000`,
    endPt: `${endDay.replace(/-/g, '')}235959`,
  }
}

function flattenAkOptions(options: BillingAkOption[]): BillingAkOption[] {
  const result: BillingAkOption[] = []
  const visit = (option: BillingAkOption) => {
    result.push(option)
    option.children?.forEach(visit)
  }
  options.forEach(visit)
  return result
}

function normalizeOwnerType(ownerType?: string): string {
  const value = (ownerType || '').trim().toLowerCase()
  const aliases: Record<string, string> = {
    person: 'person',
    personal: 'person',
    user: 'person',
    '个人': 'person',
    org: 'org',
    organization: 'org',
    '组织': 'org',
    project: 'project',
    '项目': 'project',
    console: 'console',
    '控制台': 'console',
    system: 'system',
    '系统': 'system',
  }
  return aliases[value] || value
}

function ownerTypeLabel(ownerType?: string): string {
  const labels: Record<string, string> = {
    person: '个人',
    org: '组织',
    project: '项目',
    console: '控制台',
    system: '系统',
  }
  if (!ownerType) return '-'
  const normalized = normalizeOwnerType(ownerType)
  return labels[normalized] || ownerType
}

function akTitle(option?: BillingAkOption): string {
  if (!option) return '-'
  return option.name || option.akDisplay || option.code
}

function formatPt(pt: string, granularity: BillingGranularity): string {
  if (!pt || pt.length < 8) return pt || '-'
  if (granularity === 'month') {
    return `${pt.slice(0, 4)}-${pt.slice(4, 6)}`
  }
  return `${pt.slice(0, 4)}-${pt.slice(4, 6)}-${pt.slice(6, 8)}`
}

function formatAmount(value?: number): string {
  return (value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
}

function timeRangeLabel(granularity: BillingGranularity, startMonth: string, endMonth: string, startDay: string, endDay: string): string {
  return granularity === 'month' ? `${startMonth} 至 ${endMonth}` : `${startDay} 至 ${endDay}`
}

function ownerTypeBadgeClass(ownerType?: string): string {
  const classes: Record<string, string> = {
    person: 'border-blue-200 bg-blue-50 text-blue-700',
    org: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    project: 'border-amber-200 bg-amber-50 text-amber-700',
    console: 'border-violet-200 bg-violet-50 text-violet-700',
    system: 'border-slate-200 bg-slate-50 text-slate-700',
  }
  const normalized = normalizeOwnerType(ownerType)
  return normalized ? classes[normalized] || 'border-slate-200 bg-slate-50 text-slate-700' : 'border-slate-200 bg-slate-50 text-slate-700'
}

function AkOptionRow({ option, selected = false }: { option: BillingAkOption; selected?: boolean }) {
  return (
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <Badge variant="outline" className={ownerTypeBadgeClass(option.ownerType)}>{ownerTypeLabel(option.ownerType)}</Badge>
      <span className="max-w-[120px] truncate font-medium">{akTitle(option)}</span>
      <span className="min-w-0 truncate text-xs text-muted-foreground">{option.code}</span>
      {selected && <Check className="ml-auto h-4 w-4 shrink-0 text-primary" />}
    </div>
  )
}

function defaultScopeForAk(option?: BillingAkOption): BillingScope {
  const children = option?.children || []
  if (children.length === 0) return 'current'
  if (option?.directPermission) return 'all'
  return `child:${children[0].code}`
}

function selectedOptionClass(selected: boolean) {
  return cn(
    "flex w-full rounded-sm px-2 py-2 text-left text-sm hover:bg-slate-50",
    selected && "bg-blue-50/70"
  )
}

interface SearchOption {
  value: string;
  label: string;
}

function RequiredLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="text-sm font-medium">
      {children}
      <span className="ml-0.5 text-red-500">*</span>
    </div>
  )
}

function BillingDelayHint() {
  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <Info className="h-3.5 w-3.5 text-muted-foreground" />
        </TooltipTrigger>
        <TooltipContent side="top" align="start" className="max-w-[280px] text-xs">
          账单均为 T+1：月账单下月 1 号后可查，日账单次日可查；查询时请选择对应账单粒度。
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

function SearchableFilter({
  value,
  options,
  allLabel,
  placeholder,
  className,
  disabled = false,
  disabledLabel,
  onChange,
}: {
  value: string;
  options: SearchOption[];
  allLabel: string;
  placeholder: string;
  className?: string;
  disabled?: boolean;
  disabledLabel?: string;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = React.useState(false)
  const [keyword, setKeyword] = React.useState('')
  const selectedLabel = disabled ? (disabledLabel || allLabel) : value === ALL_VALUE ? allLabel : options.find(option => option.value === value)?.label || value
  const filteredOptions = React.useMemo(() => {
    const normalized = keyword.trim().toLowerCase()
    if (!normalized) return options
    return options.filter(option => option.label.toLowerCase().includes(normalized) || option.value.toLowerCase().includes(normalized))
  }, [keyword, options])

  React.useEffect(() => {
    if (!open) {
      setKeyword('')
    }
  }, [open])

  return (
    <Popover open={open && !disabled} onOpenChange={(nextOpen) => !disabled && setOpen(nextOpen)}>
      <PopoverTrigger asChild>
        <Button variant="outline" disabled={disabled} className={cn("h-10 w-full justify-between overflow-hidden px-3 text-left font-normal", className)}>
          <span className="truncate">{selectedLabel}</span>
          <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[var(--radix-popover-trigger-width)] min-w-[220px] max-w-[calc(100vw-2rem)] p-1">
        <div className="p-2">
          <div className="relative">
            <Search className="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder={placeholder}
              className="h-9 pl-8"
            />
          </div>
        </div>
        <div className="max-h-[280px] overflow-y-auto">
          <button
            type="button"
            className={cn(selectedOptionClass(value === ALL_VALUE), "items-center justify-between")}
            onClick={() => {
              onChange(ALL_VALUE)
              setOpen(false)
            }}
          >
            <span>{allLabel}</span>
            {value === ALL_VALUE && <Check className="h-4 w-4 text-primary" />}
          </button>
          {filteredOptions.map(option => (
            <button
              key={option.value}
              type="button"
              className={cn(selectedOptionClass(value === option.value), "items-center justify-between")}
              onClick={() => {
                onChange(option.value)
                setOpen(false)
              }}
            >
              <span className="truncate">{option.label}</span>
              {value === option.value && <Check className="h-4 w-4 shrink-0 text-primary" />}
            </button>
          ))}
          {filteredOptions.length === 0 && (
            <div className="px-2 py-6 text-center text-sm text-muted-foreground">无匹配项</div>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}

export function BillingQueryPage({ mode = 'user' }: BillingQueryPageProps) {
  const isAdminMode = mode === 'admin'
  const { categoryTrees } = useSidebar()
  const [akOptions, setAkOptions] = React.useState<BillingAkOption[]>([])
  const allAkOptions = React.useMemo(() => flattenAkOptions(akOptions), [akOptions])
  const selectableAkOptions = akOptions
  const [selectedAkCode, setSelectedAkCode] = React.useState('')
  const [adminAkCode, setAdminAkCode] = React.useState('')
  const normalizedAdminAkCode = adminAkCode.trim()
  const selectedAk = React.useMemo(
    () => allAkOptions.find(option => option.code === selectedAkCode),
    [allAkOptions, selectedAkCode]
  )
  const [scope, setScope] = React.useState<BillingScope>('current')
  const [granularity, setGranularity] = React.useState<BillingGranularity>('month')
  const defaultMonth = React.useMemo(previousMonthInput, [])
  const defaultYesterday = React.useMemo(yesterdayInput, [])
  const [startMonth, setStartMonth] = React.useState(defaultMonth)
  const [endMonth, setEndMonth] = React.useState(defaultMonth)
  const [startDay, setStartDay] = React.useState(defaultYesterday)
  const [endDay, setEndDay] = React.useState(defaultYesterday)
  const [endpoint, setEndpoint] = React.useState(ALL_VALUE)
  const [model, setModel] = React.useState(ALL_VALUE)
  const [models, setModels] = React.useState<Model[]>([])
  const [page, setPage] = React.useState(1)
  const [pageSize, setPageSize] = React.useState(DEFAULT_PAGE_SIZE)
  const [result, setResult] = React.useState<BillingRecordPage | null>(null)
  const [loading, setLoading] = React.useState(false)
  const [optionsLoading, setOptionsLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [akOpen, setAkOpen] = React.useState(false)
  const [scopeOpen, setScopeOpen] = React.useState(false)
  const [akKeyword, setAkKeyword] = React.useState('')
  const [scopeKeyword, setScopeKeyword] = React.useState('')

  const endpointOptions = React.useMemo(() => {
    const flattened = flattenCategoryTrees(categoryTrees)
    const byEndpoint = new Map<string, typeof flattened[number]>()
    flattened.forEach(item => byEndpoint.set(item.endpoint, item))
    return Array.from(byEndpoint.values()).map(option => ({
      value: option.endpoint,
      label: option.endpointName || option.endpoint,
    }))
  }, [categoryTrees])
  const modelOptions = React.useMemo(() => models.map(item => ({
    value: item.modelName,
    label: item.modelName,
  })), [models])
  const filteredParentAkOptions = React.useMemo(() => {
    const keyword = akKeyword.trim().toLowerCase()
    if (!keyword) return selectableAkOptions
    return selectableAkOptions.filter(option => option.code.toLowerCase().includes(keyword))
  }, [akKeyword, selectableAkOptions])
  const selectedChildren = selectedAk?.children || []
  const filteredScopeChildren = React.useMemo(() => {
    const keyword = scopeKeyword.trim().toLowerCase()
    if (!keyword) return selectedChildren
    return selectedChildren.filter(option => option.code.toLowerCase().includes(keyword))
  }, [scopeKeyword, selectedChildren])
  const showScopeSummaryOptions = !!selectedAk?.directPermission && scopeKeyword.trim().length === 0

  React.useEffect(() => {
    if (isAdminMode) {
      setAkOptions([])
      setSelectedAkCode('')
      setOptionsLoading(false)
      return
    }
    let ignore = false
    async function loadAkOptions() {
      try {
        setOptionsLoading(true)
        const options = await getBillingAkOptions()
        if (ignore) return
        setAkOptions(options || [])
        if ((options || []).length > 0) {
          setSelectedAkCode(current => current && (options || []).some(option => option.code === current) ? current : options[0].code)
        }
      } catch (err) {
        if (!ignore) setError(err instanceof Error ? err.message : '加载 AK 选项失败')
      } finally {
        if (!ignore) setOptionsLoading(false)
      }
    }
    loadAkOptions()
    return () => {
      ignore = true
    }
  }, [isAdminMode])

  React.useEffect(() => {
    let ignore = false
    async function loadModels() {
      if(endpoint === ALL_VALUE) {
        setModels([])
        setModel(ALL_VALUE)
        return
      }
      try {
        const modelList = await getModelsForSelection(endpoint, 'active')
        if (!ignore) setModels(modelList)
      } catch {
        if (!ignore) setModels([])
      }
    }
    loadModels()
    setModel(ALL_VALUE)
  }, [endpoint])

  React.useEffect(() => {
    if (!selectedAk) return
    setScope(defaultScopeForAk(selectedAk))
  }, [selectedAkCode, selectedAk])

  React.useEffect(() => {
    if (!akOpen) {
      setAkKeyword('')
    }
  }, [akOpen])

  React.useEffect(() => {
    if (!scopeOpen) {
      setScopeKeyword('')
    }
  }, [scopeOpen])

  const queryAkCodes = React.useCallback((): string[] => {
    if (isAdminMode) {
      return normalizedAdminAkCode ? [normalizedAdminAkCode] : []
    }
    if (!selectedAk) return []
    const children = selectedAk.children || []
    if (children.length === 0) {
      return [selectedAk.code]
    }
    if (!selectedAk.directPermission) {
      const childCode = scope.startsWith('child:') ? scope.replace('child:', '') : children[0]?.code
      return childCode ? [childCode] : []
    }
    if (scope === 'all') {
      return [selectedAk.code, ...children.map(child => child.code)]
    }
    if (scope === 'parent') {
      return [selectedAk.code]
    }
    if (scope === 'current') {
      if(children.length > 0) {
        return selectedAk.directPermission ? [selectedAk.code, ...children.map(child => child.code)] : children.map(child => child.code)
      }
      return [selectedAk.code]
    }
    return [scope.replace('child:', '')]
  }, [isAdminMode, normalizedAdminAkCode, scope, selectedAk])

  const queryEndpoints = React.useCallback((): string[] | undefined => {
    if (endpoint === ALL_VALUE) return undefined
    const selectedOption = endpointOptions.find(option => option.value === endpoint)
    if (endpoint === CHAT_COMPLETIONS_ENDPOINT || selectedOption?.label === SMART_CHAT_LABEL) {
      return SMART_CHAT_QUERY_ENDPOINTS
    }
    return [endpoint]
  }, [endpoint, endpointOptions])

  const fetchRecords = React.useCallback(async (targetPage = page, targetPageSize = pageSize) => {
    if (isAdminMode && !normalizedAdminAkCode) {
      setResult(null)
      setError('请输入 ak-code')
      return
    }
    if (!isAdminMode && !selectedAk) {
      setResult(null)
      return
    }
    const { startPt, endPt } = granularity === 'month'
      ? monthToPtRange(startMonth, endMonth)
      : dayToPtRange(startDay, endDay)
    try {
      setLoading(true)
      setError(null)
      const condition = {
        granularity,
        akCodes: queryAkCodes(),
        startPt,
        endPt,
        endpoints: queryEndpoints(),
        model: model === ALL_VALUE ? undefined : model,
        page: targetPage,
        size: targetPageSize,
      }
      const response = isAdminMode ? await queryAdminBillingRecords(condition) : await queryBillingRecords(condition)
      setResult(response)
    } catch (err) {
      setResult(null)
      setError(err instanceof Error ? err.message : '账单查询失败')
    } finally {
      setLoading(false)
    }
  }, [isAdminMode, normalizedAdminAkCode, selectedAk, granularity, startMonth, endMonth, startDay, endDay, model, page, pageSize, queryAkCodes, queryEndpoints])

  const resetQueryState = React.useCallback(() => {
    setPage(1)
    setResult(null)
    setError(null)
  }, [])
  const dayMin = currentMonthFirstDayInput()
  const dayMax = defaultYesterday
  const hasScopeControl = !isAdminMode && !!selectedAk?.children?.length
  const records: BillingRecord[] = result?.data || []
  const hasMore = result?.has_more || false
  const totalPages = Math.max(1, Math.ceil((result?.total || 0) / pageSize))
  const scopeLabel = React.useMemo(() => {
    if (scope === 'parent') return '仅父 AK'
    if (scope.startsWith('child:')) {
      const childCode = scope.replace('child:', '')
      const child = selectedChildren.find(item => item.code === childCode)
      return child ? `${akTitle(child)} · ${child.code}` : '-'
    }
    if (selectedAk && !selectedAk.directPermission) {
      const child = selectedChildren[0]
      return child ? `${akTitle(child)} · ${child.code}` : '-'
    }
    return selectedAk?.directPermission ? '全部（父 AK 及所有子 AK）' : '全部（有权限子 AK）'
  }, [scope, selectedAk, selectedChildren])
  const resetFilters = React.useCallback(() => {
    const firstAk = selectableAkOptions[0]
    setGranularity('month')
    setStartMonth(defaultMonth)
    setEndMonth(defaultMonth)
    setStartDay(defaultYesterday)
    setEndDay(defaultYesterday)
    setSelectedAkCode(isAdminMode ? '' : firstAk?.code || '')
    setAdminAkCode('')
    setScope(isAdminMode ? 'current' : defaultScopeForAk(firstAk))
    setEndpoint(ALL_VALUE)
    setModel(ALL_VALUE)
    setPage(1)
    setPageSize(DEFAULT_PAGE_SIZE)
    setResult(null)
  }, [defaultMonth, defaultYesterday, isAdminMode, selectableAkOptions])

  return (
    <div>
      <TopBar
        title={isAdminMode ? "账单查询（管理员）" : "账单查询"}
        description={isAdminMode ? "查询全量 AK 的月/日账单明细" : "按 AK、时间范围、能力点和模型查询月/日账单明细"}
      />
      <div className="m-4 space-y-4">
        <div className="rounded-md border bg-card p-4">
          {isAdminMode ? (
            <div className="flex flex-wrap items-end gap-x-8 gap-y-4">
              <div className="w-full min-w-0 space-y-2 sm:w-24">
                <div className="flex items-center gap-2">
                  <RequiredLabel>粒度</RequiredLabel>
                  <BillingDelayHint />
                </div>
                <Select
                  value={granularity}
                  onValueChange={(value) => {
                    setGranularity(value as BillingGranularity)
                    resetQueryState()
                  }}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="month">月账单</SelectItem>
                    <SelectItem value="day">日账单</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[280px]">
                <RequiredLabel>时间范围</RequiredLabel>
                <Popover>
                  <PopoverTrigger asChild>
                    <Button variant="outline" className="h-10 w-full justify-start overflow-hidden text-left font-normal">
                      <CalendarDays className="mr-2 h-4 w-4 shrink-0" />
                      <span className="truncate">{timeRangeLabel(granularity, startMonth, endMonth, startDay, endDay)}</span>
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent align="start" className="w-[320px]">
                    <div className="space-y-3">
                      <div className="text-sm font-medium">选择时间范围</div>
                      {granularity === 'month' ? (
                        <div className="grid grid-cols-2 gap-2">
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">开始月份</div>
                            <Input type="month" min={BILLING_DATA_START_MONTH} value={startMonth} onChange={(e) => { setStartMonth(clampBillingMonth(e.target.value)); resetQueryState() }} />
                          </div>
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">结束月份</div>
                            <Input type="month" min={BILLING_DATA_START_MONTH} value={endMonth} onChange={(e) => { setEndMonth(clampBillingMonth(e.target.value)); resetQueryState() }} />
                          </div>
                        </div>
                      ) : (
                        <div className="grid grid-cols-2 gap-2">
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">开始日期</div>
                            <Input type="date" min={dayMin} max={dayMax} value={startDay} onChange={(e) => { setStartDay(e.target.value); resetQueryState() }} />
                          </div>
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">结束日期</div>
                            <Input type="date" min={dayMin} max={dayMax} value={endDay} onChange={(e) => { setEndDay(e.target.value); resetQueryState() }} />
                          </div>
                        </div>
                      )}
                    </div>
                  </PopoverContent>
                </Popover>
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[180px]">
                <div className="text-sm font-medium">能力点</div>
                <SearchableFilter
                  value={endpoint}
                  options={endpointOptions}
                  allLabel="全部能力点"
                  placeholder="搜索能力点"
                  onChange={(value) => { setEndpoint(value); resetQueryState() }}
                />
              </div>
              <div className="w-full min-w-0 space-y-2 sm:w-[260px]">
                <div className="text-sm font-medium">模型</div>
                <SearchableFilter
                  value={model}
                  options={modelOptions}
                  allLabel="全部模型"
                  placeholder="搜索模型"
                  disabled={endpoint === ALL_VALUE}
                  disabledLabel="请先选择能力点"
                  onChange={(value) => { setModel(value); resetQueryState() }}
                />
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[360px]">
                <RequiredLabel>AK</RequiredLabel>
                <Input
                  value={adminAkCode}
                  onChange={(event) => {
                    setAdminAkCode(event.target.value)
                    resetQueryState()
                  }}
                  placeholder="请输入 ak-code"
                  className="h-10"
                />
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap items-end gap-x-8 gap-y-4">
              <div className="w-full min-w-0 space-y-2 sm:w-24">
                <div className="flex items-center gap-2">
                  <RequiredLabel>粒度</RequiredLabel>
                  <BillingDelayHint />
                </div>
                <Select
                  value={granularity}
                  onValueChange={(value) => {
                    setGranularity(value as BillingGranularity)
                    resetQueryState()
                  }}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="month">月账单</SelectItem>
                    <SelectItem value="day">日账单</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[280px]">
                <RequiredLabel>时间范围</RequiredLabel>
                <Popover>
                  <PopoverTrigger asChild>
                    <Button variant="outline" className="h-10 w-full justify-start overflow-hidden text-left font-normal">
                      <CalendarDays className="mr-2 h-4 w-4 shrink-0" />
                      <span className="truncate">{timeRangeLabel(granularity, startMonth, endMonth, startDay, endDay)}</span>
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent align="start" className="w-[320px]">
                    <div className="space-y-3">
                      <div className="text-sm font-medium">选择时间范围</div>
                      {granularity === 'month' ? (
                        <div className="grid grid-cols-2 gap-2">
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">开始月份</div>
                            <Input type="month" min={BILLING_DATA_START_MONTH} value={startMonth} onChange={(e) => { setStartMonth(clampBillingMonth(e.target.value)); resetQueryState() }} />
                          </div>
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">结束月份</div>
                            <Input type="month" min={BILLING_DATA_START_MONTH} value={endMonth} onChange={(e) => { setEndMonth(clampBillingMonth(e.target.value)); resetQueryState() }} />
                          </div>
                        </div>
                      ) : (
                        <div className="grid grid-cols-2 gap-2">
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">开始日期</div>
                            <Input type="date" min={dayMin} max={dayMax} value={startDay} onChange={(e) => { setStartDay(e.target.value); resetQueryState() }} />
                          </div>
                          <div className="space-y-1">
                            <div className="text-xs text-muted-foreground">结束日期</div>
                            <Input type="date" min={dayMin} max={dayMax} value={endDay} onChange={(e) => { setEndDay(e.target.value); resetQueryState() }} />
                          </div>
                        </div>
                      )}
                    </div>
                  </PopoverContent>
                </Popover>
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[180px]">
                <div className="text-sm font-medium">能力点</div>
                <SearchableFilter
                  value={endpoint}
                  options={endpointOptions}
                  allLabel="全部能力点"
                  placeholder="搜索能力点"
                  onChange={(value) => { setEndpoint(value); resetQueryState() }}
                />
              </div>
              <div className="w-full min-w-0 space-y-2 sm:w-[260px]">
                <div className="text-sm font-medium">模型</div>
                <SearchableFilter
                  value={model}
                  options={modelOptions}
                  allLabel="全部模型"
                  placeholder="搜索模型"
                  disabled={endpoint === ALL_VALUE}
                  disabledLabel="请先选择能力点"
                  onChange={(value) => { setModel(value); resetQueryState() }}
                />
              </div>

              <div className="w-full min-w-0 space-y-2 sm:w-[420px]">
                <RequiredLabel>AK</RequiredLabel>
                <Popover open={akOpen} onOpenChange={setAkOpen}>
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      className="h-10 w-full justify-between overflow-hidden px-3 text-left font-normal"
                      disabled={optionsLoading || allAkOptions.length === 0}
                    >
                      <span className="min-w-0 truncate">
                        {selectedAk ? `${akTitle(selectedAk)} · ${selectedAk.code}` : optionsLoading ? '加载 AK 选项中' : '选择 AK'}
                      </span>
                      <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent align="start" className="w-[520px] max-w-[calc(100vw-2rem)] p-1">
                    <div className="p-2">
                      <div className="relative">
                        <Search className="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                        <Input
                          value={akKeyword}
                          onChange={(event) => setAkKeyword(event.target.value)}
                          placeholder="搜索 ak-code"
                          className="h-9 pl-8"
                        />
                      </div>
                    </div>
                    <div className="max-h-[420px] overflow-y-auto">
                      {filteredParentAkOptions.map(option => (
                        <button
                          key={option.code}
                          type="button"
                          className={selectedOptionClass(selectedAkCode === option.code)}
                          onClick={() => {
                            setSelectedAkCode(option.code)
                            resetQueryState()
                            setAkOpen(false)
                          }}
                        >
                          <AkOptionRow option={option} selected={selectedAkCode === option.code} />
                        </button>
                      ))}
                      {filteredParentAkOptions.length === 0 && (
                        <div className="px-2 py-6 text-center text-sm text-muted-foreground">无匹配 AK</div>
                      )}
                    </div>
                  </PopoverContent>
                </Popover>
              </div>

              {hasScopeControl && (
                <div className="w-full min-w-0 space-y-2 sm:w-[420px]">
                  <RequiredLabel>查询范围</RequiredLabel>
                  <Popover open={scopeOpen} onOpenChange={setScopeOpen}>
                    <PopoverTrigger asChild>
                      <Button variant="outline" className="h-10 w-full justify-between overflow-hidden px-3 text-left font-normal">
                        <span className="truncate">{scopeLabel}</span>
                        <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-[520px] max-w-[calc(100vw-2rem)] p-1">
                      <div className="p-2">
                        <div className="relative">
                          <Search className="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                          <Input
                            value={scopeKeyword}
                            onChange={(event) => setScopeKeyword(event.target.value)}
                            placeholder="搜索子 ak-code"
                            className="h-9 pl-8"
                          />
                        </div>
                      </div>
                      <div className="space-y-1">
                        {showScopeSummaryOptions && (
                          <>
                            <button
                              type="button"
                              className={cn(selectedOptionClass(scope === 'all' || scope === 'current'), "items-center justify-between")}
                              onClick={() => { setScope('all'); resetQueryState(); setScopeOpen(false) }}
                            >
                              <span>全部（父 AK 及所有子 AK）</span>
                              {(scope === 'all' || scope === 'current') && <Check className="h-4 w-4 text-primary" />}
                            </button>
                            <button
                              type="button"
                              className={cn(selectedOptionClass(scope === 'parent'), "items-center justify-between")}
                              onClick={() => { setScope('parent'); resetQueryState(); setScopeOpen(false) }}
                            >
                              <span>仅父 AK</span>
                              {scope === 'parent' && <Check className="h-4 w-4 text-primary" />}
                            </button>
                          </>
                        )}
                        {filteredScopeChildren.map(child => {
                          const value: BillingScope = `child:${child.code}`
                          return (
                            <button
                              key={child.code}
                              type="button"
                              className={selectedOptionClass(scope === value)}
                              onClick={() => { setScope(value); resetQueryState(); setScopeOpen(false) }}
                            >
                              <AkOptionRow option={child} selected={scope === value} />
                            </button>
                          )
                        })}
                        {!showScopeSummaryOptions && filteredScopeChildren.length === 0 && (
                          <div className="px-2 py-6 text-center text-sm text-muted-foreground">无匹配子 AK</div>
                        )}
                      </div>
                    </PopoverContent>
                  </Popover>
                </div>
              )}

              <div className="ml-auto flex gap-2">
                <Button onClick={() => { setPage(1); fetchRecords(1, pageSize) }} disabled={loading || !selectedAk}>
                  <Search className="mr-2 h-4 w-4" />
                  查询
                </Button>
                <Button variant="outline" onClick={resetFilters}>
                  <RotateCcw className="mr-2 h-4 w-4" />
                  重置
                </Button>
              </div>
            </div>
          )}

          {isAdminMode && (
            <div className="mt-4 flex justify-end gap-2">
              <Button onClick={() => { setPage(1); fetchRecords(1, pageSize) }} disabled={loading || (isAdminMode ? !normalizedAdminAkCode : !selectedAk)}>
                <Search className="mr-2 h-4 w-4" />
                查询
              </Button>
              <Button variant="outline" onClick={resetFilters}>
                <RotateCcw className="mr-2 h-4 w-4" />
                重置
              </Button>
            </div>
          )}
        </div>

        {error && (
          <div className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            {error}
          </div>
        )}

        <div className="rounded-md border bg-card">
          <div className="flex flex-wrap items-center gap-3 p-4">
            <div className="flex items-center gap-2">
              <ReceiptText className="h-5 w-5" />
              <h3 className="text-sm font-medium">账单明细</h3>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-emerald-700">
                总费用 ¥{formatAmount(result?.totalAmount)}
              </Badge>
              <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                总条数 {result?.total || 0}
              </Badge>
              <div className="flex w-fit max-w-full items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <Info className="h-4 w-4 shrink-0 text-amber-600" />
                <span>当前账单数据仅作为参考，最终费用以正式出账单为准！仅支持查询 2026 年 6 月及之后的数据。</span>
              </div>
            </div>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="text-center whitespace-nowrap">时间</TableHead>
                <TableHead className="text-center min-w-[260px]">ak-code</TableHead>
                <TableHead className="text-center whitespace-nowrap">ak 类型</TableHead>
                <TableHead className="text-center min-w-[160px]">能力点</TableHead>
                <TableHead className="text-center min-w-[160px]">模型</TableHead>
                <TableHead className="text-center whitespace-nowrap">费用（元）</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">加载中...</TableCell>
                </TableRow>
              ) : records.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">暂无账单数据</TableCell>
                </TableRow>
              ) : (
                records.map((record, index) => (
                  <TableRow key={`${record.pt}-${record.akCode}-${record.endpoint}-${record.model}-${index}`}>
                    <TableCell className="text-center whitespace-nowrap">{formatPt(record.pt, granularity)}</TableCell>
                    <TableCell className="text-center font-mono text-xs">{record.akCode}</TableCell>
                    <TableCell className="text-center">
                      <Badge variant="outline" className={ownerTypeBadgeClass(record.accountType)}>{ownerTypeLabel(record.accountType)}</Badge>
                    </TableCell>
                    <TableCell className="text-center text-sm">{record.endpoint || '-'}</TableCell>
                    <TableCell className="text-center text-sm">{record.model || '-'}</TableCell>
                    <TableCell className="text-center font-medium">¥{formatAmount(record.amount)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          <div className={cn('px-4 pb-4', records.length === 0 && 'hidden')}>
            <Pagination
              mode="frontend"
              currentPage={page}
              hasMore={hasMore}
              totalPages={totalPages}
              totalItems={result?.total || 0}
              pageSize={pageSize}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              loading={loading}
              onPrevPage={() => {
                const nextPage = Math.max(1, page - 1)
                setPage(nextPage)
                fetchRecords(nextPage, pageSize)
              }}
              onNextPage={() => {
                const nextPage = page + 1
                setPage(nextPage)
                fetchRecords(nextPage, pageSize)
              }}
              onPageChange={(nextPage) => {
                setPage(nextPage)
                fetchRecords(nextPage, pageSize)
              }}
              onPageSizeChange={(size) => {
                setPageSize(size)
                setPage(1)
                fetchRecords(1, size)
              }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
