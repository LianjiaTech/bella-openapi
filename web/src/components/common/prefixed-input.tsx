"use client"

import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { Check } from "lucide-react"

import { cn } from "@/lib/utils/index"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select"
import { Input } from "@/components/common/input"
import {
  Popover,
  PopoverAnchor,
  PopoverContent,
} from "@/components/common/popover"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/common/command"

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

export interface InputSuggestionOption {
  value: string
  label: string
  description?: string
  badge?: {
    label: string
    className?: string
  }
  details?: Array<{
    label: string
    value?: string
  }>
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
  /** 输入建议列表开关 */
  suggestionsEnabled?: boolean
  /** 输入建议列表 */
  suggestions?: InputSuggestionOption[]
  /** 建议列表过滤值，不传则不过滤 */
  suggestionsFilterValue?: string
  /** 当前选中的建议值 */
  selectedSuggestionValue?: string
  /** 建议列表展开时回调 */
  onSuggestionsOpen?: () => void
  /** 建议列表展开状态变化回调 */
  onSuggestionsOpenChange?: (open: boolean) => void
  /** 建议选中回调 */
  onSuggestionSelect?: (option: InputSuggestionOption) => void
  /** 建议列表加载状态 */
  suggestionsLoading?: boolean
  /** 建议列表错误文案 */
  suggestionsError?: string
  /** 建议列表顶部提示文案 */
  suggestionsHint?: string
  /** 建议列表加载中文案 */
  suggestionsLoadingText?: string
  /** 建议列表空状态文案 */
  suggestionsEmptyText?: string
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
      suggestionsEnabled,
      suggestions = [],
      suggestionsFilterValue,
      selectedSuggestionValue,
      onSuggestionsOpen,
      onSuggestionsOpenChange,
      onSuggestionSelect,
      suggestionsLoading,
      suggestionsError,
      suggestionsHint,
      suggestionsLoadingText = "Loading...",
      suggestionsEmptyText = "No options",
      ...props
    },
    ref
  ) => {
    // 前缀状态管理
    const [prefix, setPrefix] = React.useState(
      defaultPrefix || prefixOptions[0]?.value || ""
    )
    const [suggestionsOpen, setSuggestionsOpen] = React.useState(false)

    const setSuggestionsOpenState = React.useCallback(
      (open: boolean) => {
        setSuggestionsOpen(open)
        onSuggestionsOpenChange?.(open)
      },
      [onSuggestionsOpenChange]
    )

    React.useEffect(() => {
      setPrefix(defaultPrefix || prefixOptions[0]?.value || "")
    }, [defaultPrefix, prefixOptions])

    React.useEffect(() => {
      if (!suggestionsEnabled) {
        setSuggestionsOpenState(false)
      }
    }, [setSuggestionsOpenState, suggestionsEnabled])

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

    const openSuggestions = () => {
      if (!suggestionsEnabled || disabled) {
        return
      }

      if (!suggestionsOpen) {
        setSuggestionsOpenState(true)
        onSuggestionsOpen?.()
      }
    }

    const handleSuggestionsOpenChange = (open: boolean) => {
      setSuggestionsOpenState(open)
      if (open) {
        onSuggestionsOpen?.()
      }
    }

    const handleSuggestionSelect = (option: InputSuggestionOption) => {
      onValueChange?.(option.value)
      onSuggestionSelect?.(option)
      setSuggestionsOpenState(false)
    }

    const searchValue = (suggestionsFilterValue || "").trim().toLowerCase()
    const filteredSuggestions = suggestions.filter((option) => {
      if (!searchValue) {
        return true
      }

      const detailValues = option.details?.flatMap((detail) => [
        detail.label,
        detail.value,
      ]) || []

      return [
        option.value,
        option.label,
        option.description,
        option.badge?.label,
        ...detailValues,
      ]
        .filter(Boolean)
        .some((item) => item?.toLowerCase().includes(searchValue))
    })

    // 是否为错误状态
    const hasError = Boolean(error)
    const inputElement = (
      <Input
        {...props}
        ref={ref}
        value={value}
        onChange={handleInputChange}
        onFocus={(event) => {
          openSuggestions()
          props.onFocus?.(event)
        }}
        onClick={(event) => {
          openSuggestions()
          props.onClick?.(event)
        }}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        role={suggestionsEnabled ? "combobox" : props.role}
        aria-expanded={suggestionsEnabled ? suggestionsOpen : props["aria-expanded"]}
        className={cn(
          "rounded-l-none flex-1",
          prefixedInputVariants({ size }),
          hasError &&
            "border-destructive focus-visible:ring-destructive focus-visible:ring-2 focus-visible:ring-offset-0",
          className
        )}
      />
    )

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
          {suggestionsEnabled ? (
            <Popover open={suggestionsOpen} onOpenChange={handleSuggestionsOpenChange}>
              <PopoverAnchor asChild>{inputElement}</PopoverAnchor>
              <PopoverContent
                className="w-[var(--radix-popper-anchor-width)] p-0"
                align="start"
                onOpenAutoFocus={(event) => event.preventDefault()}
              >
                <Command>
                  {suggestionsHint && (
                    <div className="border-b px-3 py-2 text-xs text-muted-foreground">
                      {suggestionsHint}
                    </div>
                  )}
                  <CommandList>
                    {suggestionsLoading ? (
                      <CommandEmpty>{suggestionsLoadingText}</CommandEmpty>
                    ) : suggestionsError ? (
                      <CommandEmpty className="px-3 text-destructive">
                        {suggestionsError}
                      </CommandEmpty>
                    ) : filteredSuggestions.length === 0 ? (
                      <CommandEmpty>{suggestionsEmptyText}</CommandEmpty>
                    ) : (
                      <CommandGroup>
                        {filteredSuggestions.map((option) => {
                          const selected = option.value === selectedSuggestionValue

                          return (
                            <CommandItem
                              key={option.value}
                              value={option.value}
                              onSelect={() => handleSuggestionSelect(option)}
                              className={cn(
                                "items-start gap-2 border border-transparent px-3 py-2",
                                selected && "border-primary/25 bg-primary/5"
                              )}
                            >
                              <Check
                                className={cn(
                                  "mt-0.5 h-4 w-4 shrink-0 text-primary",
                                  selected ? "opacity-100" : "opacity-0"
                                )}
                              />
                              <div className="min-w-0 space-y-0.5">
                                <div className="flex min-w-0 items-center gap-3">
                                  {option.badge && (
                                    <span
                                      className={cn(
                                        "inline-flex h-6 min-w-14 shrink-0 items-center justify-center rounded-full px-3 text-sm font-semibold leading-none",
                                        option.badge.className
                                      )}
                                    >
                                      {option.badge.label}
                                    </span>
                                  )}
                                  <div className="min-w-0 space-y-0.5">
                                    <div className="break-all font-mono text-sm">
                                      {option.value}
                                    </div>
                                    {option.description && (
                                      <div className="break-words text-xs text-muted-foreground">
                                        {option.description}
                                      </div>
                                    )}
                                    {option.details && option.details.length > 0 && (
                                      <div className="grid gap-x-4 gap-y-1 pt-1 text-xs text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
                                        {option.details.map((detail) => (
                                          <div
                                            key={detail.label}
                                            className="min-w-0"
                                            title={detail.value || "-"}
                                          >
                                            <span className="text-foreground/70">
                                              {detail.label}:
                                            </span>{" "}
                                            <span className="break-words">
                                              {detail.value || "-"}
                                            </span>
                                          </div>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                </div>
                              </div>
                            </CommandItem>
                          )
                        })}
                      </CommandGroup>
                    )}
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          ) : (
            inputElement
          )}
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
