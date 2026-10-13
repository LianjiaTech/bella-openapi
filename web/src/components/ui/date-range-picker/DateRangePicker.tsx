"use client"

import * as React from "react"
import { format } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Calendar as CalendarIcon } from "lucide-react"
import { DateRange } from "react-day-picker"

import { cn } from "@/lib/utils"
import { Button } from "@/components/common/button"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/common/popover"
import { Calendar } from "@/components/common/calendar"
import { Input } from "@/components/common/input"
import { DateRangePreset } from "./types"
import { DEFAULT_DATE_RANGE_PRESETS } from "./presets"

interface DateRangePickerProps {
  /** 当前选中的日期范围 */
  value?: DateRange
  /** 日期范围变化回调 */
  onChange?: (range: DateRange | undefined) => void
  /** 占位符文本 */
  placeholder?: string
  /** 自定义样式类名 */
  className?: string
  /** 自定义预设时间选项，如果不传则使用默认预设 */
  presets?: DateRangePreset[]
}

/**
 * 日期范围选择器组件
 *
 * 提供日期范围选择功能，支持：
 * - 快速选择预设时间范围
 * - 日历选择自定义日期范围
 * - 自定义预设选项配置
 *
 * @example
 * ```tsx
 * // 使用默认预设
 * <DateRangePicker value={dateRange} onChange={setDateRange} />
 *
 * // 使用自定义预设
 * const customPresets = [
 *   { label: "最近7天", getValue: () => ({ from: ..., to: ... }) }
 * ]
 * <DateRangePicker value={dateRange} onChange={setDateRange} presets={customPresets} />
 * ```
 */
export function DateRangePicker({
  value,
  onChange,
  placeholder = "选择日期范围",
  className,
  presets = DEFAULT_DATE_RANGE_PRESETS,
}: DateRangePickerProps) {
  const [open, setOpen] = React.useState(false)
  const [draftFrom, setDraftFrom] = React.useState("")
  const [draftTo, setDraftTo] = React.useState("")

  const formatDateTimeInput = React.useCallback((date?: Date) => {
    if (!date) return ""
    return format(date, "yyyy-MM-dd'T'HH:mm")
  }, [])

  const parseDateTimeInput = React.useCallback((input: string) => {
    if (!input) return undefined
    const parsed = new Date(input)
    return Number.isNaN(parsed.getTime()) ? undefined : parsed
  }, [])

  const maxDateTime = React.useMemo(() => formatDateTimeInput(new Date()), [formatDateTimeInput])

  const syncDraftRange = React.useCallback((range?: DateRange) => {
    setDraftFrom(formatDateTimeInput(range?.from))
    setDraftTo(formatDateTimeInput(range?.to))
  }, [formatDateTimeInput])

  const normalizeRange = React.useCallback((range?: DateRange) => {
    if (!range?.from && !range?.to) {
      return undefined
    }

    const from = range?.from ?? range?.to
    const to = range?.to

    if (!from) {
      return undefined
    }

    if (!to) {
      return { from, to: from }
    }

    if (from.getTime() > to.getTime()) {
      return { from, to: from }
    }

    return { from, to }
  }, [])

  React.useEffect(() => {
    if (open) {
      syncDraftRange(value)
    }
  }, [open, value, syncDraftRange])

  const commitDraftRange = React.useCallback((closeAfterCommit = false) => {
    const nextRange = normalizeRange({
      from: parseDateTimeInput(draftFrom),
      to: parseDateTimeInput(draftTo),
    })
    onChange?.(nextRange)
    syncDraftRange(nextRange)
    if (closeAfterCommit) {
      setOpen(false)
    }
  }, [draftFrom, draftTo, normalizeRange, onChange, parseDateTimeInput, syncDraftRange])

  const handlePresetClick = (preset: DateRangePreset) => {
    const range = preset.getValue()
    onChange?.(range)
    syncDraftRange(range)
    setOpen(false)
  }

  // 判断当前日期范围是否匹配某个预设项
  const isPresetSelected = (preset: DateRangePreset) => {
    if (!value?.from || !value?.to) return false

    const presetRange = preset.getValue()
    if (!presetRange.from || !presetRange.to) return false

    const timeDiffFrom = Math.abs(value.from.getTime() - presetRange.from.getTime())
    const timeDiffTo = Math.abs(value.to.getTime() - presetRange.to.getTime())

    // 允许5秒的容差（考虑初始化和点击之间的时间差）
    return timeDiffFrom < 5000 && timeDiffTo < 5000
  }

  const applyTimeToDate = React.useCallback((date: Date, timeSource: Date) => {
    const nextDate = new Date(date)
    nextDate.setHours(timeSource.getHours(), timeSource.getMinutes(), 0, 0)
    return nextDate
  }, [])

  // 处理日历选择，保留当前已选时分
  const handleCalendarSelect = (range: DateRange | undefined) => {
    if (!range || !range.from) {
      onChange?.(undefined)
      syncDraftRange(undefined)
      return
    }

    const now = new Date()
    const fromTimeSource = value?.from ?? parseDateTimeInput(draftFrom) ?? now
    const toTimeSource = value?.to ?? parseDateTimeInput(draftTo) ?? fromTimeSource
    const fromDate = applyTimeToDate(range.from, fromTimeSource)
    let toDate: Date | undefined
    if (range.to) {
      toDate = applyTimeToDate(range.to, toTimeSource)
    }

    const nextRange = normalizeRange({
      from: fromDate,
      to: toDate,
    })
    onChange?.(nextRange)
    syncDraftRange(nextRange)

    // 当选择了完整的日期范围时，自动关闭面板
    if (nextRange?.to) {
      setOpen(false)
    }
  }

  const formatDateRange = (range?: DateRange) => {
    if (!range?.from) {
      return placeholder
    }

    if (!range.to || range.from.getTime() === range.to.getTime()) {
      return format(range.from, "yyyy-MM-dd HH:mm", { locale: zhCN })
    }

    return `${format(range.from, "yyyy-MM-dd HH:mm", { locale: zhCN })} - ${format(
      range.to,
      "yyyy-MM-dd HH:mm",
      { locale: zhCN }
    )}`
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn(
            "w-full justify-start overflow-hidden text-left font-normal",
            !value && "text-muted-foreground",
            className
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4 shrink-0" />
          <span className="truncate">{formatDateRange(value)}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[min(92vw,920px)] p-0" align="start">
        <div className="flex flex-col lg:flex-row">
          {/* 预设选项 */}
          {presets.length > 0 && (
            <div className="flex flex-wrap gap-1 border-b border-border p-4 lg:w-[220px] lg:flex-col lg:border-b-0 lg:border-r lg:p-6">
              <div className="text-xs font-medium text-muted-foreground mb-2">
                快速选择
              </div>
              {presets.map((preset) => {
                const isSelected = isPresetSelected(preset)
                return (
                  <Button
                    key={preset.label}
                    variant={isSelected ? "secondary" : "ghost"}
                    size="sm"
                    className={cn(
                      "justify-start text-sm",
                      isSelected && "font-medium"
                    )}
                    onClick={() => handlePresetClick(preset)}
                  >
                    {preset.label}
                  </Button>
                )
              })}
            </div>
          )}

          <div className="flex min-w-0 flex-1 flex-col">
            <div className="grid gap-3 border-b border-border p-4 md:grid-cols-2">
              <div className="min-w-0">
                <div className="mb-1 text-xs font-medium text-muted-foreground">开始时间</div>
                <Input
                  type="datetime-local"
                  value={draftFrom}
                  max={maxDateTime}
                  onChange={(event) => setDraftFrom(event.target.value)}
                  className="min-w-0"
                />
              </div>
              <div className="min-w-0">
                <div className="mb-1 text-xs font-medium text-muted-foreground">结束时间</div>
                <Input
                  type="datetime-local"
                  value={draftTo}
                  min={draftFrom || undefined}
                  max={maxDateTime}
                  onChange={(event) => setDraftTo(event.target.value)}
                  className="min-w-0"
                />
              </div>
            </div>

            <div className="overflow-x-auto p-3">
              <Calendar
                mode="range"
                selected={value}
                onSelect={handleCalendarSelect}
                numberOfMonths={2}
                defaultMonth={value?.from}
                disabled={(date) => date > new Date()} // 禁用未来日期
                toDate={new Date()} // 最大日期为今天
              />
            </div>

            <div className="flex items-center justify-between border-t border-border p-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  onChange?.(undefined)
                  syncDraftRange(undefined)
                }}
              >
                清空
              </Button>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setOpen(false)}
                >
                  取消
                </Button>
                <Button
                  size="sm"
                  onClick={() => commitDraftRange(true)}
                >
                  应用
                </Button>
              </div>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}
