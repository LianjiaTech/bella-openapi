"use client"

import * as React from "react"
import { Combobox } from "@/components/ui/combobox"
import { useModelSelect } from "./hooks/useModelSelect"

interface ModelSelectProps {
  /** 当前选中的模型名称 */
  value?: string
  /** 值变化回调 */
  onValueChange?: (value: string) => void
  /** 占位符文本 */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 自定义类名 */
  className?: string
  /** 是否自动选中第一个模型 (默认: false) */
  autoSelectFirst?: boolean
  /** 端点 */
  endpoint: string
}

export function ModelSelect({
  value,
  endpoint,
  onValueChange,
  placeholder = "选择模型",
  disabled = false,
  className,
  autoSelectFirst = false,
}: ModelSelectProps) {
  // 从 useModelSelect 获取所有模型相关数据
  const { models, modelsLoading, modelsError, modelOptions } = useModelSelect(endpoint)

  // 追踪 endpoint 变化，用于判断是否需要重新自动选择
  const prevEndpointRef = React.useRef(endpoint)
  const [shouldAutoSelect, setShouldAutoSelect] = React.useState(autoSelectFirst)

  // 当 endpoint 变化时，标记需要重新自动选择
  React.useEffect(() => {
    if (prevEndpointRef.current !== endpoint) {
      prevEndpointRef.current = endpoint
      if (autoSelectFirst) {
        setShouldAutoSelect(true)
      }
    }
  }, [endpoint, autoSelectFirst])

  // 自动选中第一个模型（仅在标记为需要自动选择时触发）
  React.useEffect(() => {
    if (shouldAutoSelect && models.length > 0 && !modelsLoading) {
      onValueChange?.(models[0].modelName)
      setShouldAutoSelect(false) // 选择完成后重置标记
    }
  }, [shouldAutoSelect, models, modelsLoading, onValueChange])

  // 处理加载和错误状态
  const displayPlaceholder = modelsLoading
    ? "加载中..."
    : modelsError
    ? "加载失败"
    : placeholder

  return (
    <Combobox
      options={modelOptions}
      value={value}
      onValueChange={onValueChange}
      placeholder={displayPlaceholder}
      searchPlaceholder="搜索模型..."
      emptyText="未找到匹配的模型"
      disabled={disabled || modelsLoading}
      className={className}
    />
  )
}
