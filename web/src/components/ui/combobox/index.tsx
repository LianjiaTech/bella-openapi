'use client'

import { useState } from 'react'
import { Check } from 'lucide-react'
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
import { Input } from '@/components/common/input'
import { cn } from '@/lib/utils'

export interface ComboboxOption {
  value: string
  label: string
}

export interface ComboboxProps {
  // 选项列表
  options: ComboboxOption[]
  // 当前值
  value?: string
  // 值变化回调
  onValueChange?: (value: string) => void
  // 占位符文本
  placeholder?: string
  // 搜索框占位符
  searchPlaceholder?: string
  // 空状态提示文本
  emptyText?: string
  // 是否禁用
  disabled?: boolean
  // 是否允许输入选项列表外的自定义值
  allowCustomValue?: boolean
  // 按钮 ID（用于 Label 关联）
  id?: string
  // 自定义类名
  className?: string
}

export function Combobox({
  options,
  value,
  onValueChange,
  placeholder = '选择...',
  searchPlaceholder = '搜索或输入...',
  emptyText,
  disabled = false,
  allowCustomValue = false,
  id,
  className,
}: ComboboxProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')

  // 处理选项选择
  const handleSelect = (selectedValue: string) => {
    const newValue = selectedValue === value ? '' : selectedValue
    onValueChange?.(newValue)
    setSearch('')
    setOpen(false)
  }

  // 处理下拉框关闭（支持自定义输入）
  const handleOpenChange = (isOpen: boolean) => {
    if (disabled) {
      setOpen(false)
      return
    }

    setOpen(isOpen)
    if (!isOpen) {
      // 当下拉框关闭时，如果搜索值不在选项中，且不为空，则保存为自定义值
      if (allowCustomValue && search && !options.some((opt) => opt.value === search)) {
        onValueChange?.(search)
      }
      setSearch('')
    }
  }

  // 获取显示文本
  const selectedOption = options.find((option) => option.value === value)
  const displayText = selectedOption?.label || (allowCustomValue ? value : '') || ''
  const resolvedEmptyText = emptyText ?? (
    allowCustomValue ? '未找到匹配项，将使用自定义值' : '未找到匹配项'
  )
  // 过滤选项
  const filteredOptions = options && options.filter(
    (option) =>
      option.label?.toLowerCase().includes(search.toLowerCase()) ||
      option.value?.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <Popover open={disabled ? false : open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <Input
          type="text"
          role="combobox"
          aria-expanded={!disabled && open}
          aria-disabled={disabled}
          value={displayText}
          readOnly
          disabled={disabled}
          className={cn('w-full pr-8 font-normal', className)}
          id={id}
          placeholder={placeholder}
        />
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
            disabled={disabled}
          />
          <CommandList>
            {filteredOptions.length === 0 && <CommandEmpty>{resolvedEmptyText}</CommandEmpty>}
            <CommandGroup>
              {filteredOptions.map((option) => (
                <CommandItem
                  key={option.value}
                  value={option.value}
                  onSelect={handleSelect}
                >
                  <Check
                    className={cn(
                      'mr-2 h-4 w-4',
                      value === option.value ? 'opacity-100' : 'opacity-0'
                    )}
                  />
                  {option.label}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
