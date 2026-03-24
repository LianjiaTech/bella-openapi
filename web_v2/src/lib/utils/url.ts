/**
 * URL 工具函数
 *
 * 职责：
 * - 提供获取完整请求 URL 的工具函数
 * - 用于调试日志，显示真实后端域名
 *
 * 设计说明：
 * - 开发环境：如果配置了 realBackendUrl，返回完整 URL（用于日志调试）
 * - 生产环境：返回相对路径（由 Nginx 代理处理）
 */

import { config } from '@/config'

/**
 * 获取完整请求 URL（用于日志输出）
 *
 * @param relativePath - 相对路径，如 '/v1/chat/completions'
 * @returns 完整 URL 或相对路径
 *
 * @example
 * // 开发环境（配置了 NEXT_PUBLIC_REAL_BACKEND_URL=http://test-bella-openapi.ke.com）
 * getFullUrl('/v1/chat/completions')
 * // => 'http://test-bella-openapi.ke.com/v1/chat/completions'
 *
 * // 生产环境（未配置 realBackendUrl）
 * getFullUrl('/v1/chat/completions')
 * // => '/v1/chat/completions'
 */
export function getFullUrl(relativePath: string): string {
  const { realBackendUrl } = config.api

  // 如果配置了真实后端域名，返回完整 URL
  if (realBackendUrl) {
    // 移除末尾斜杠，避免重复
    const baseUrl = realBackendUrl.replace(/\/$/, '')
    // 确保路径以斜杠开头
    const path = relativePath.startsWith('/') ? relativePath : `/${relativePath}`
    return `${baseUrl}${path}`
  }

  // 否则返回相对路径（由 Next.js rewrites 或 Nginx 处理）
  return relativePath
}

/**
 * 判断是否为相对路径
 */
export function isRelativePath(url: string): boolean {
  return url.startsWith('/')
}

/**
 * 获取域名（用于日志显示）
 *
 * @param url - 完整 URL 或相对路径
 * @returns 域名或 '(相对路径)'
 */
export function getDomain(url: string): string {
  try {
    if (isRelativePath(url)) {
      return '(相对路径 - 由 Next.js/Nginx 代理)'
    }
    const urlObj = new URL(url)
    return urlObj.origin
  } catch {
    return '(无效 URL)'
  }
}
