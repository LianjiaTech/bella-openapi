/**
 * 元数据相关常量定义
 */

/**
 * 优先级选项
 */
export const priorityOptions = [
  { value: 'high', label: '高' },
  { value: 'normal', label: '中' },
  { value: 'low', label: '低' },
]

/**
 * 优先级映射对象
 */
const priorityMap: Record<string, string> = {
  high: '高',
  normal: '中',
  low: '低',
}

/**
 * 将优先级英文值转换为中文标签
 * @param priority - 优先级英文值 (high/normal/low)
 * @returns 对应的中文标签,如果没有匹配返回原始值
 */
export const getPriorityLabel = (priority: string | undefined): string => {
  if (!priority) return '未设置'
  return priorityMap[priority] || priority
}
