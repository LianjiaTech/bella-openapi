"use client"

import * as React from "react"
import { Input } from "@/components/common/input"
import { cn } from "@/lib/utils"

interface HttpStatusInputProps {
  value?: string
  onValueChange?: (value: string) => void
  placeholder?: string
  className?: string
}

/**
 * HTTP 状态码输入组件
 *
 * 功能:
 * - 支持单个或多个状态码输入 (用逗号分隔, 如: 404 或 404,500,502)
 * - 仅允许输入数字和逗号
 * - 自动去除多余空格
 */
export function HttpStatusInput({
  value = "",
  onValueChange,
  placeholder = "输入状态码 (如: 404,500)",
  className,
}: HttpStatusInputProps) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputValue = e.target.value

    // 仅允许数字和逗号
    const sanitized = inputValue.replace(/[^\d,]/g, "")

    onValueChange?.(sanitized)
  }

  return (
    <Input
      type="text"
      value={value}
      onChange={handleChange}
      placeholder={placeholder}
      className={cn(className)}
    />
  )
}
