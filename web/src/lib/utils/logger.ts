/**
 * 统一日志工具
 * 开发环境输出日志，生产环境禁用日志（除了error）
 */

const isDevelopment = process.env.NODE_ENV === 'development'

export const logger = {
  /**
   * 普通日志 - 仅在开发环境输出
   */
  log: (...args: any[]) => {
    if (isDevelopment) {
      console.log(...args)
    }
  },

  /**
   * 警告日志 - 仅在开发环境输出
   */
  warn: (...args: any[]) => {
    if (isDevelopment) {
      console.warn(...args)
    }
  },

  /**
   * 错误日志 - 始终输出（生产环境也需要）
   */
  error: (...args: any[]) => {
    console.error(...args)
  },

  /**
   * 调试日志 - 仅在开发环境输出
   */
  debug: (...args: any[]) => {
    if (isDevelopment) {
      console.debug(...args)
    }
  }
}
