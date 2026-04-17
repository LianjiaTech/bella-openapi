/**
 * 格式化价格显示值
 * @param value - 价格数据，可能是字符串、数字、对象等类型
 * @returns 格式化后的字符串
 */
export function formatPriceValue(value: unknown): string {
  // 处理字符串和数字
  if (typeof value === 'string' || typeof value === 'number') {
    return String(value)
  }

  // 处理对象和数组，格式化为可读的JSON
  if (typeof value === 'object' && value !== null) {
    try {
      return JSON.stringify(value, null, 2)
    } catch (error) {
      return String(value)
    }
  }

  // 处理其他类型（null、undefined、boolean等）
  return String(value)
}
