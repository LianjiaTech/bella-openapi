"use client"

import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils/index"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select"
import { Input } from "@/components/common/input"

/**
 * PrefixedInput - 带前缀选择器的输入框组件
 *
 * 将下拉选择器（前缀）和输入框组合成一个整体的输入控件
 * 适用于货币输入、电话区号、单位选择等场景
 */

// 尺寸变体定义
const prefixedInputVariants = cva("", {
  variants: {
    size: {
      sm: "h-8 text-sm",
      md: "h-10 text-base",
      lg: "h-12 text-lg",
    },
  },
  defaultVariants: {
    size: "md",
  },
})

export interface PrefixOption {
  value: string
  label: string
}

export interface PrefixedInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "size" | "prefix">,
    VariantProps<typeof prefixedInputVariants> {
  /** 前缀选项数组（用户自定义） */
  prefixOptions: PrefixOption[]
  /** 默认前缀值，不传则使用 prefixOptions[0] */
  defaultPrefix?: string
  /** 前缀变化回调 */
  onPrefixChange?: (value: string) => void
  /** 输入框值（受控） */
  value?: string
  /** 输入框值变化回调 */
  onValueChange?: (value: string) => void
  /** 错误信息，有值则显示错误状态和错误文本 */
  error?: string
  /** 容器自定义样式 */
  containerClassName?: string
}

const PrefixedInput = React.forwardRef<HTMLInputElement, PrefixedInputProps>(
  (
    {
      prefixOptions,
      defaultPrefix,
      onPrefixChange,
      value,
      onValueChange,
      size = "md",
      error,
      required,
      disabled,
      className,
      containerClassName,
      placeholder,
      ...props
    },
    ref
  ) => {
    // 前缀状态管理
    const [prefix, setPrefix] = React.useState(
      defaultPrefix || prefixOptions[0]?.value || ""
    )

    // 处理前缀变化
    const handlePrefixChange = (newValue: string) => {
      setPrefix(newValue)
      onPrefixChange?.(newValue)
    }

    // 处理输入框变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      onValueChange?.(e.target.value)
      props.onChange?.(e)
    }

    // 是否为错误状态
    const hasError = Boolean(error)
    console.log(prefix,'---prefix---')
    return (
      <div className={cn("w-full", containerClassName)}>
        {/* Select + Input 组合 */}
        <div className="inline-flex w-full -space-x-px shadow-sm shadow-black/5">
          {/* 前缀选择器 */}
          <Select
            value={prefix}
            onValueChange={handlePrefixChange}
            disabled={disabled}
          >
            <SelectTrigger
              className={cn(
                "w-auto min-w-[80px] rounded-r-none border-r-0 font-mono",
                prefixedInputVariants({ size }),
                hasError &&
                  "border-destructive focus:ring-destructive focus:ring-2 focus:ring-offset-0",
                className
              )}
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent className="min-w-24">
              {prefixOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <span className="text-sm">{option.label}</span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* 输入框 */}
          <Input
            ref={ref}
            value={value}
            onChange={handleInputChange}
            placeholder={placeholder}
            required={required}
            disabled={disabled}
            className={cn(
              "rounded-l-none flex-1",
              prefixedInputVariants({ size }),
              hasError &&
                "border-destructive focus-visible:ring-destructive focus-visible:ring-2 focus-visible:ring-offset-0",
              className
            )}
            {...props}
          />
        </div>

        {/* 错误提示文本 */}
        {hasError && (
          <p className="mt-1.5 text-sm text-destructive">{error}</p>
        )}
      </div>
    )
  }
)

PrefixedInput.displayName = "PrefixedInput"

export { PrefixedInput }
