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

  const handlePresetClick = (preset: DateRangePreset) => {
    const range = preset.getValue()
    onChange?.(range)
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

  // 处理日历选择，保留当前时分
  const handleCalendarSelect = (range: DateRange | undefined) => {
    if (!range || !range.from) {
      onChange?.(undefined)
      return
    }

    const now = new Date()

    // 为 from 日期设置当前时分
    const fromDate = new Date(range.from)
    fromDate.setHours(now.getHours())
    fromDate.setMinutes(now.getMinutes())
    fromDate.setSeconds(0)
    fromDate.setMilliseconds(0)

    // 为 to 日期设置当前时分（如果存在）
    let toDate: Date | undefined
    if (range.to) {
      toDate = new Date(range.to)
      toDate.setHours(now.getHours())
      toDate.setMinutes(now.getMinutes())
      toDate.setSeconds(0)
      toDate.setMilliseconds(0)
    }

    onChange?.({
      from: fromDate,
      to: toDate,
    })

    // 当选择了完整的日期范围时，自动关闭面板
    if (toDate) {
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
            "w-full justify-start text-left font-normal",
            !value && "text-muted-foreground",
            className
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {formatDateRange(value)}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <div className="flex">
          {/* 预设选项 */}
          {presets.length > 0 && (
            <div className="flex flex-col gap-1 border-r border-border p-6">
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

          {/* 日历选择器 */}
          <div className="p-3">
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
        </div>
      </PopoverContent>
    </Popover>
  )
}
