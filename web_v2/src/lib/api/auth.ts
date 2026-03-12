/**
 * 认证API客户端
 * 封装所有与后端认证相关的HTTP请求
 * 复用 lib/api/client.ts 的axios实例和401处理逻辑
 */

import { get, post } from './client'
import { AxiosError } from 'axios';
import type { UserInfo, OAuthConfig, LoginResponse } from '@/lib/types/auth'

/**
 * 获取当前用户信息
 *
 * 对应后端: GET /console/userInfo
 *
 * @returns 用户信息，未登录返回null
 *
 * 使用场景:
 * - 应用初始化时检查登录状态
 * - 刷新用户信息
 */
export async function getUserInfo(): Promise<UserInfo | null> {
  try {
    const data = await get<UserInfo>('/console/userInfo')

    // 验证返回数据是否包含userId
    if (data?.userId) {
      return data
    }

    return null
  } catch (error: unknown) {
    const axiosError = error as AxiosError;
    // 401错误表示未登录，不抛出错误
    if (axiosError?.response?.status === 401) {
      return null
    }

    // 其他错误向上抛出
    throw error
  }
}

/**
 * 密钥登录
 *
 * 对应后端: POST /openapi/login
 *
 * @param secret - 用户密钥
 * @returns 用户信息
 * @throws {Error} 登录失败时抛出错误
 *
 * 使用场景:
 * - 用户在登录页面输入密钥进行登录
 */
export async function login(secret: string): Promise<UserInfo> {
  const response = await post<LoginResponse>(
    '/openapi/login',
    { secret },
    { timeout: 10000 } // 10秒超时
  )

  // 验证响应数据
  if (!response.success || !response.user) {
    throw new Error(response.message || '登录失败')
  }

  return response.user
}

/**
 * 登出
 *
 * 对应后端: POST /openapi/logout
 *
 * 后端会清除Session并删除Cookie
 *
 * 使用场景:
 * - 用户点击登出按钮
 */
export async function logout(): Promise<void> {
  await post('/openapi/logout')
}

/**
 * 获取OAuth配置
 *
 * 对应后端: GET /openapi/oauth/config?redirect=xxx
 *
 * @param redirect - 登录成功后的跳转地址（可选）
 * @returns OAuth提供商列表和授权URL
 *
 * 使用场景:
 * - 登录页面加载时获取可用的OAuth提供商
 */
export async function getOAuthConfig(redirect?: string): Promise<OAuthConfig> {
  const params = redirect ? { redirect } : {}
  const data = await get<OAuthConfig>('/openapi/oauth/config', params)

  return data
}

/**
 * 刷新用户信息
 *
 * 内部调用getUserInfo，但会抛出错误（如果未登录）
 *
 * @returns 用户信息
 * @throws {Error} 用户未登录时抛出错误
 *
 * 使用场景:
 * - 权限变更后重新加载用户信息
 * - 需要确保用户已登录的场景
 */
export async function refreshUserInfo(): Promise<UserInfo> {
  const userInfo = await getUserInfo()

  if (!userInfo) {
    throw new Error('用户未登录')
  }

  return userInfo
}
