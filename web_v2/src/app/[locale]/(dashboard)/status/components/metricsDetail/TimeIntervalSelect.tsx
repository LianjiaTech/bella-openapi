"use client"

import * as React from "react"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select"
import type { TimeInterval } from "@/lib/types/status"

/** 时间间隔选项配置 */
const TIME_INTERVAL_OPTIONS = [
  { value: '5' as TimeInterval, label: '5分钟' },
  { value: '15' as TimeInterval, label: '15分钟' },
  { value: '30' as TimeInterval, label: '30分钟' },
  { value: '60' as TimeInterval, label: '1小时' },
  { value: '180' as TimeInterval, label: '6小时' }
]

interface TimeIntervalSelectProps {
  /** 当前选中的时间间隔 */
  value?: TimeInterval
  /** 时间间隔变化回调 */
  onValueChange?: (value: TimeInterval) => void
  /** 默认值 */
  defaultValue?: TimeInterval
  /** 自定义样式类名 */
  className?: string
}

/**
 * 时间间隔选择组件
 * 用于图表中选择数据聚合的时间粒度
 */
export function TimeIntervalSelect({
  value,
  onValueChange,
  defaultValue = '5',
  className,
}: TimeIntervalSelectProps) {
  return (
    <Select value={value} onValueChange={onValueChange} defaultValue={defaultValue}>
      <SelectTrigger className={className || "w-[120px] h-8"}>
        <SelectValue placeholder="选择时间间隔" />
      </SelectTrigger>
      <SelectContent>
        {TIME_INTERVAL_OPTIONS.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
