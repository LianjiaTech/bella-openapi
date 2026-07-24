import { LucideIcon } from "lucide-react"

/**
 * 自定义筛选项配置
 */
export interface CustomFilterOption {
  value: string           // 选项值
  label: string          // 选项显示文本
  description?: string   // 选项描述(可选)
  color?: string        // 自定义颜色类(可选)
}

/**
 * 自定义筛选器配置
 */
export interface CustomFilter {
  id: string                    // 筛选器唯一标识
  label: string                 // 筛选器标题
  icon?: LucideIcon            // 筛选器图标(可选)
  type: 'single' | 'multiple'  // 单选或多选
  options: CustomFilterOption[] // 筛选选项列表
  defaultValue?: string | string[] // 默认值
  className?: string           // 自定义样式类(可选)
  layout?: 'horizontal' | 'grid' // 布局方式(可选)
}

/**
 * 自定义筛选器状态
 */
export type CustomFilterValues = Record<string, string | string[]>

/**
 * 自定义筛选器 Action
 */
export type CustomFilterAction =
  | { type: 'SET_VALUE'; filterId: string; value: string | string[] }
  | { type: 'RESET' }
  | { type: 'INIT'; filters: CustomFilter[] }
