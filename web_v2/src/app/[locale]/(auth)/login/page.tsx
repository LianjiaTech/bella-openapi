"use client"

import { useEffect } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { useAuth } from "@/components/providers/auth-provider"
import { LoginLayout } from "./components/login-layout"
import { LoginForm } from "./components/login-form"
import { OAuthButtons } from "./components/oauth-buttons"
import { Separator } from "@/components/common/separator"

/**
 * 登录页面
 * 支持OAuth第三方登录和密钥登录两种方式
 */
export default function LoginPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { user, isLoading } = useAuth()
  const redirect = searchParams.get('redirect') || '/overview'

  // 已登录自动跳转
  useEffect(() => {
    if (user && !isLoading) {
      router.push(redirect)
    }
  }, [user, isLoading, redirect, router])

  // if (isLoading) {
  //   return (
  //     <LoginLayout>
  //       <div className="flex items-center justify-center">
  //         <div className="text-muted-foreground">加载中...</div>
  //       </div>
  //     </LoginLayout>
  //   )
  // }

  if (user) {
    return null // 重定向中
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
      </div>
    </LoginLayout>
  )
}
