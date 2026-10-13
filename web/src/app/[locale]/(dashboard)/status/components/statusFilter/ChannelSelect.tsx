"use client"

import * as React from "react"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import type { MetricsAtTime } from "@/lib/types/status"

interface ChannelSelectProps {
  /** 当前选中的渠道代码 */
  value?: string
  /** 值变化回调 */
  onValueChange?: (value: string) => void
  /** 渠道列表数据 */
  metricsData?: MetricsAtTime[]
  /** 是否正在加载 */
  loading?: boolean
  /** 占位符文本 */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 自定义类名 */
  className?: string
}

export function ChannelSelect({
  value,
  onValueChange,
  metricsData = [],
  loading = false,
  placeholder = "",
  disabled = false,
  className,
}: ChannelSelectProps) {
  // 转换为 Combobox 选项格式
  const options: ComboboxOption[] = React.useMemo(() => {
    // 使用 Map 对 channel_code 去重
    const channelOptions = Array.from(
      new Map(
        metricsData.map((channel) => [
          channel.channel_code,
          {
            value: channel.channel_code,
            label: channel.channel_code,
          },
        ])
      ).values()
    )
    // 只有当渠道数量大于1时，才在选项列表中添加"全部"选项
    if (channelOptions.length > 1) {
      return [
        { value: "", label: "全部" },
        ...channelOptions,
      ]
    }

    return channelOptions
  }, [metricsData])

  // 处理加载状态
  const displayPlaceholder = loading
    ? "加载中..."
    : options.length > 1
    ? "全部渠道"
    : options.length === 1
    ? options[0].label
    : placeholder || "无渠道"

  // 当加载时禁用组件
  const isDisabled = disabled || loading

  return (
    <Combobox
      options={options}
      value={value}
      onValueChange={onValueChange}
      placeholder={displayPlaceholder}
      searchPlaceholder="搜索渠道..."
      emptyText="未找到匹配的渠道"
      disabled={isDisabled}
      className={className}
    />
  )
}
