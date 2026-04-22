"use client"

import * as React from "react"
import { Input } from "@/components/common/input"
import { cn } from "@/lib/utils"

interface LimitInputProps {
  value?: number
  onValueChange?: (value: number) => void
  placeholder?: string
  className?: string
  min?: number
  max?: number
}

/**
 * 查询条数限制输入组件
 *
 * 功能:
 * - 支持数字输入，限制查询返回的记录条数
 * - 默认值为 100
 * - 取值范围: 10-1000
 * - 自动校验并限制输入范围
 */
export function LimitInput({
  value = 100,
  onValueChange,
  placeholder = "100",
  className,
  min = 10,
  max = 1000,
}: LimitInputProps) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputValue = e.target.value

    // 如果输入为空，不触发更新
    if (inputValue === "") {
      return
    }

    // 转换为数字
    const numValue = parseInt(inputValue, 10)

    // 验证是否为有效数字
    if (isNaN(numValue)) {
      return
    }

    // 限制范围
    const clampedValue = Math.min(Math.max(numValue, min), max)

    onValueChange?.(clampedValue)
  }

  const handleBlur = () => {
    // 失去焦点时，如果值为空或无效，恢复为默认值
    if (!value || value < min || value > max) {
      onValueChange?.(100)
    }
  }

  return (
    <Input
      type="number"
      value={value}
      onChange={handleChange}
      onBlur={handleBlur}
      placeholder={placeholder}
      min={min}
      max={max}
      className={cn(className)}
    />
  )
}
