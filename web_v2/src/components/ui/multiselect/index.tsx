'use client'

import { useState } from 'react'
import { Check, ChevronsUpDown, X } from 'lucide-react'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/common/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/common/command'
import { cn } from '@/lib/utils'

export interface MultiSelectOption {
  value: string
  label: string
}

export interface MultiSelectProps {
  // 选项列表
  options: MultiSelectOption[]
  // 当前值（数组）
  value?: string[]
  // 值变化回调
  onValueChange?: (value: string[]) => void
  // 占位符文本
  placeholder?: string
  // 搜索框占位符
  searchPlaceholder?: string
  // 空状态提示文本
  emptyText?: string
  // 是否禁用
  disabled?: boolean
  // 按钮 ID（用于 Label 关联）
  id?: string
  // 自定义类名
  className?: string
}

export function MultiSelect({
  options,
  value = [],
  onValueChange,
  placeholder = '选择...',
  searchPlaceholder = '搜索...',
  emptyText = '未找到匹配项',
  disabled = false,
  id,
  className,
}: MultiSelectProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')

  // 处理选项选择/取消
  const handleSelect = (selectedValue: string) => {
    const newValue = value.includes(selectedValue)
      ? value.filter((v) => v !== selectedValue)
      : [...value, selectedValue]
    onValueChange?.(newValue)
  }

  // 移除已选项
  const handleRemove = (valueToRemove: string, e: React.MouseEvent) => {
    e.stopPropagation()
    const newValue = value.filter((v) => v !== valueToRemove)
    onValueChange?.(newValue)
  }

  // 获取已选项的选项对象
  const getSelectedOptions = () => {
    return value
      .map((val) => options.find((opt) => opt.value === val))
      .filter(Boolean) as MultiSelectOption[]
  }
  // 过滤选项
  const filteredOptions = options && options.filter(
    (option) =>
      option.label.toLowerCase().includes(search.toLowerCase()) ||
      option.value.toLowerCase().includes(search.toLowerCase())
  )

  const selectedOptions = getSelectedOptions()

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <div
          className={cn(
            'relative flex min-h-10 w-full flex-wrap items-center gap-1 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
            disabled && 'cursor-not-allowed opacity-50',
            className
          )}
          onClick={() => !disabled && setOpen(true)}
        >
          {selectedOptions.length === 0 ? (
            <span className="text-muted-foreground">{placeholder}</span>
          ) : (
            <>
              {selectedOptions.map((option) => (
                <span
                  key={option.value}
                  className="inline-flex items-center gap-1 rounded-md bg-secondary px-2 py-1 text-sm"
                >
                  <span>{option.label}</span>
                  {!disabled && (
                    <button
                      type="button"
                      onClick={(e) => handleRemove(option.value, e)}
                      className="ml-1 rounded-full hover:bg-secondary-foreground/20"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  )}
                </span>
              ))}
            </>
          )}
          <ChevronsUpDown className="ml-auto h-4 w-4 shrink-0 opacity-50" />
        </div>
      </PopoverTrigger>
      <PopoverContent
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
      >
        <Command>
          <CommandInput
            placeholder={searchPlaceholder}
            value={search}
            onValueChange={setSearch}
          />
          <CommandList>
            {filteredOptions.length === 0 && (
              <CommandEmpty>{emptyText}</CommandEmpty>
            )}
            <CommandGroup>
              {filteredOptions.map((option) => {
                const isSelected = value.includes(option.value)
                return (
                  <CommandItem
                    key={option.value}
                    value={option.value}
                    onSelect={() => handleSelect(option.value)}
                  >
                    <Check
                      className={cn(
                        'mr-2 h-4 w-4',
                        isSelected ? 'opacity-100' : 'opacity-0'
                      )}
                    />
                    {option.label}
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

