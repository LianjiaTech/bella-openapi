"use client"

import { useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { getUserInfo } from "@/lib/api/auth"
import { LoginLayout } from "./components/login-layout"
import { LoginForm } from "./components/login-form"
import { OAuthButtons } from "./components/oauth-buttons"
import { Separator } from "@/components/common/separator"

/**
 * 登录页面
 * 支持OAuth第三方登录、密钥登录和CAS企业登录三种方式
 *
 * 职责说明:
 * - 检查用户登录状态,已登录则跳转
 * - 始终显示密钥登录表单,不受认证模式影响
 * - CAS自动跳转由client.ts拦截器统一处理
 *
 * 工作流程:
 * 1. CAS模式:
 *    - 页面初始化触发getUserInfo()
 *    - 后端返回401 + X-Redirect-Login响应头
 *    - client.ts拦截器自动跳转到企业登录页
 *    - 用户在此页面看到的时间极短(加载状态)
 *
 * 2. OAuth/密钥模式:
 *    - getUserInfo()返回401,无X-Redirect-Login响应头
 *    - 显示OAuth按钮和密钥登录表单
 *    - 用户手动选择登录方式
 *
 * 设计说明:
 * - 密钥登录是基础功能,始终可用,不依赖OAuth配置
 * - CAS企业登录与密钥登录可以并存
 * - 简化登录模式判断逻辑,避免误隐藏密钥登录
 *
 * 避免re-render:
 * - 使用单一useEffect处理初始化逻辑
 * - 仅检查登录状态,不额外请求认证配置
 */
export default function LoginPage() {
  const searchParams = useSearchParams()
  const redirect = searchParams.get('redirect') || '/overview'
  const router = useRouter()
  const [isChecking, setIsChecking] = useState(true)

  // 页面加载时检查认证状态
  useEffect(() => {
    const checkAuthStatus = async () => {
      try {
        // 检查是否已登录
        const userInfo = await getUserInfo()

        if (userInfo && userInfo.userId !== undefined) {
          // 已登录,直接跳转到目标页面
          console.log('[Login] User already authenticated, redirecting to:', redirect)
          router.push(redirect)
          return
        }

        // 未登录,显示登录表单
        console.log('[Login] Not authenticated, showing login form')
        setIsChecking(false)
      } catch (error: any) {
        // 401错误会由client.ts拦截器处理:
        // - CAS模式: 检测到X-Redirect-Login响应头,自动跳转企业登录页(页面会离开)
        // - 其他模式: 没有X-Redirect-Login响应头,继续显示登录表单
        console.log('[Login] Not authenticated, showing login form')
        setIsChecking(false)
      }
    }

    checkAuthStatus()
  }, [redirect, router])

  // 认证检查中,显示加载状态
  // CAS模式下,用户看到此状态的时间极短(即将跳转到企业登录页)
  if (isChecking) {
    return (
      <LoginLayout>
        <div className="space-y-4 w-full max-w-sm text-center">
          <div className="animate-spin inline-block h-8 w-8 border-4 border-current border-t-transparent rounded-full" />
          <p className="text-sm text-muted-foreground">正在检查登录状态...</p>
        </div>
      </LoginLayout>
    )
  }

  return (
    <LoginLayout>
      <div className="space-y-6 w-full max-w-sm">
        {/* 页面标题 */}
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight">
            登录 Bella OpenAPI
          </h1>
          <p className="text-sm text-muted-foreground">
            选择一种方式登录您的账户
          </p>
        </div>

        {/* OAuth第三方登录 */}
        <OAuthButtons redirect={redirect} />

        {/* 密钥登录 - 始终显示 */}
        <>
          {/* 分隔线 */}
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <Separator />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-background px-2 text-muted-foreground">
                或使用密钥登录
              </span>
            </div>
          </div>

          {/* 密钥登录 */}
          <LoginForm redirect={redirect} />
        </>
      </div>
    </LoginLayout>
  )
}
