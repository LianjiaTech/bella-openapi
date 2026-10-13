/**
 * 安全解析 JSON 字符串
 * @param jsonString - JSON 字符串
 * @returns 解析后的对象,解析失败返回 null
 */
export function safeParseJSON<T>(jsonString: string): T | null {
    try {
      return JSON.parse(jsonString) as T
    } catch {
      return null
    }
  }
/**
 * 格式化时间戳为 YYYY-MM-DD HH:mm:ss 格式
 * @param timestamp - Unix时间戳(秒)
 * @returns 格式化后的时间字符串
 */
export function formatTimestamp(timestamp: number): string {
    const date = new Date(timestamp * 1000) // 转换为毫秒
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
  
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  }
  