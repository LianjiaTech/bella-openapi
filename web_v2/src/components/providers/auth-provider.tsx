"use client"

import React, { createContext, useState, useEffect, useContext, useRef } from "react"
import { getUserInfo, login as apiLogin, logout as apiLogout, getOAuthConfig as apiGetOAuthConfig } from "@/lib/api/auth"
import type { UserInfo, OAuthConfig } from "@/lib/types/auth"

/**
 * 认证上下文类型定义
 */
type AuthContextType = {
  // 状态
  user: UserInfo | null
  isLoading: boolean
  isInitialized: boolean
  error: Error | null

  // 方法
  login: (secret: string) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  getOAuthConfig: (redirect?: string) => Promise<OAuthConfig>
}

/**
 * 创建认证上下文
 */
const AuthContext = createContext<AuthContextType | undefined>(undefined)

type AuthProviderProps = {
  children: React.ReactNode
}

/**
 * 认证Provider
 * 管理全局用户状态和认证相关操作
 *
 * 参考现有Provider的设计风格:
 * - language-provider.tsx
 * - sidebar-provider.tsx
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isInitialized, setIsInitialized] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const isInitializing = useRef(false)

  // 初始化：获取当前用户信息
  useEffect(() => {
    if (!isInitializing.current) {
      isInitializing.current = true
      initAuth()
    }
  }, [])

  /**
   * 初始化认证状态
   * 应用启动时自动调用，检查用户是否已登录
   *
   * CAS企业登录模式：
   * - 后端返回401 + X-Redirect-Login响应头
   * - client.ts自动处理跳转到企业登录页
   * - 不需要前端显式重定向到/login
   */
  const initAuth = async () => {
    try {
      setIsLoading(true)
      const userInfo = await getUserInfo()
      setUser(userInfo)
      setError(null)
    } catch (err) {
      // 401错误表示未登录，不算错误
      // CAS模式下，client.ts会自动处理X-Redirect-Login响应头并跳转
      // OAuth模式下，页面会通过路由守卫跳转到/login
      if (err instanceof Error && !err.message.includes('401')) {
        setError(err)
        console.error('Failed to initialize auth:', err)
      }
      setUser(null)
    } finally {
      setIsLoading(false)
      setIsInitialized(true)
    }
  }

  /**
   * 密钥登录
   *
   * @param secret - 用户密钥
   * @throws {Error} 登录失败时抛出错误
   *
   * 使用示例:
   * ```typescript
   * try {
   *   await login('my-secret-key')
   *   router.push('/dashboard')
   * } catch (error) {
   *   toast({ title: '登录失败', description: error.message })
   * }
   * ```
   */
  const login = async (secret: string) => {
    try {
      setIsLoading(true)
      setError(null)
      const userInfo = await apiLogin(secret)
      setUser(userInfo)
    } catch (err) {
      const error = err instanceof Error ? err : new Error('登录失败')
      setError(error)
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  /**
   * 登出
   * 清除用户状态和本地存储
   *
   * 使用示例:
   * ```typescript
   * await logout()
   * router.push('/login')
   * ```
   */
  const logout = async () => {
    try {
      setIsLoading(true)
      await apiLogout()
    } catch (err) {
      console.error('Logout error:', err)
    } finally {
      setUser(null)
      setError(null)
      setIsLoading(false)

      // 清理本地存储（参考sidebar-provider的存储清理模式）
      if (typeof window !== 'undefined') {
        localStorage.removeItem('user-preferences')
        sessionStorage.clear()
      }
    }
  }

  /**
   * 刷新用户信息
   * 用于权限变更后重新加载用户数据
   *
   * 使用示例:
   * ```typescript
   * // 更新用户权限后刷新
   * await updateUserPermissions(userId, newRoles)
   * await refreshUser()
   * ```
   */
  const refreshUser = async () => {
    try {
      setIsLoading(true)
      const userInfo = await getUserInfo()
      setUser(userInfo)
      setError(null)
    } catch (err) {
      const error = err instanceof Error ? err : new Error('刷新用户信息失败')
      setError(error)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  /**
   * 获取OAuth配置
   * 用于登录页面获取可用的OAuth提供商
   *
   * @param redirect - 登录成功后的跳转地址
   * @returns OAuth提供商列表和授权URL
   *
   * 使用示例:
   * ```typescript
   * const config = await getOAuthConfig('/dashboard')
   * config.providers.forEach(provider => {
   *   console.log(provider.name, provider.authUrl)
   * })
   * ```
   */
  const getOAuthConfig = async (redirect?: string) => {
    return apiGetOAuthConfig(redirect)
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isInitialized,
        error,
        login,
        logout,
        refreshUser,
        getOAuthConfig,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

/**
 * 使用认证上下文的Hook
 *
 * 使用示例:
 * ```typescript
 * const { user, login, logout } = useAuth()
 *
 * if (user) {
 *   console.log('当前用户:', user.username)
 * }
 * ```
 *
 * 注意:
 * - 必须在 AuthProvider 内部使用
 * - 遵循 useLanguage、useSidebar 的命名风格
 */
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
