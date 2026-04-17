/**
 * 日期格式化工具函数
 */

/**
 * 将 Date 对象格式化为 YYYYMMDDHHmm 格式
 * @param date Date 对象
 * @returns 格式化后的字符串,例如: '202601291455'
 */
export function formatDateToYYYYMMDDHHmm(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}${month}${day}${hours}${minutes}`
}

/**
 * 将 Date 对象转换为13位毫秒时间戳
 * @param date Date 对象
 * @returns 13位毫秒时间戳,例如: 1738138500000
 */
export function dateToTimestamp(date: Date): number {
  return date.getTime()
}
