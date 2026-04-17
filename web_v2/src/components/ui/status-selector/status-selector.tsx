'use client'

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select"

export interface StatusOption {
  value: string
  label: string
}

export interface StatusSelectorProps {
  /**
   * 显示标签，如 "状态"、"权限"
   */
  label: string
  /**
   * 当前选中的值
   */
  value: string | null
  /**
   * 值变化时的回调
   */
  onChange: (value: string | null) => void
  /**
   * 选项列表
   */
  options: StatusOption[]
  /**
   * "全部" 选项的占位符文本
   * @default "全部"
   */
  placeholder?: string
  /**
   * 自定义样式类名
   */
  className?: string
}

/**
 * 通用状态选择器组件
 *
 * 支持多种场景:
 * - 状态筛选 (启用/停用)
 * - 权限筛选 (公开/私有)
 * - 其他二/三选一的筛选场景
 *
 * @example
 * ```tsx
 * // 状态筛选
 * <StatusSelector
 *   label="状态"
 *   value={statusFilter}
 *   onChange={setStatusFilter}
 *   options={[
 *     { value: 'active', label: '启用' },
 *     { value: 'inactive', label: '停用' }
 *   ]}
 * />
 *
 * // 权限筛选
 * <StatusSelector
 *   label="权限"
 *   value={visibilityFilter}
 *   onChange={setVisibilityFilter}
 *   options={[
 *     { value: 'public', label: '公开' },
 *     { value: 'private', label: '私有' }
 *   ]}
 * />
 * ```
 */
export function StatusSelector({
  label,
  value,
  onChange,
  options,
  placeholder = "全部",
  className
}: StatusSelectorProps) {
  return (
    <div className={`flex items-center gap-1 text-sm ${className || ''}`}>
      <span className="text-muted-foreground whitespace-nowrap">{label}:</span>
      <Select
        value={value || 'all'}
        onValueChange={(val) => onChange(val === 'all' ? null : val)}
      >
        <SelectTrigger className="h-8 w-auto min-w-[80px] border-none shadow-none px-2 bg-transparent hover:bg-accent/50 transition-colors">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{placeholder}</SelectItem>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
