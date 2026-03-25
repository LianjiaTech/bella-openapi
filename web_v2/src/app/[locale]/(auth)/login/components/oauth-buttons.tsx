
"use client"

import { useEffect, useState } from "react"
import { useAuth } from "@/components/providers/auth-provider"
import { Button } from "@/components/common/button"
import { Github } from "lucide-react"
import type { OAuthProvider } from "@/lib/types/auth"

interface OAuthButtonsProps {
  redirect?: string
}

/**
 * OAuth第三方登录按钮组
 * 支持GitHub、Google等OAuth提供商
 */
export function OAuthButtons({ redirect = '/overview' }: OAuthButtonsProps) {
  const [providers, setProviders] = useState<OAuthProvider[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const { getOAuthConfig } = useAuth()

  useEffect(() => {
    loadOAuthProviders()
  }, [redirect])

  const loadOAuthProviders = async () => {
    try {
      const config = await getOAuthConfig(redirect)
      setProviders(config.providers)
    } catch (error) {
      console.error('Failed to load OAuth config:', error)
      
      setProviders([])
    } finally {
      setIsLoading(false)
    }
  }

  const handleOAuthLogin = (authUrl: string) => {
    window.location.href = authUrl
  }

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button variant="outline" className="w-full" disabled>
          加载中...
        </Button>
      </div>
    )
  }

  if (providers.length === 0) {
    // CAS企业登录模式：后端不返回OAuth配置
    // 用户访问受保护页面时会自动跳转到企业登录页
    return (
      <div className="text-center space-y-3 py-4">
        <div className="text-sm text-muted-foreground">
          正在跳转到企业登录页面...
        </div>
        <div className="text-xs text-muted-foreground">
          如果没有自动跳转，请刷新页面或联系管理员
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {providers.map((provider) => (
        <Button
          key={provider.name}
          variant="outline"
          className="w-full"
          onClick={() => handleOAuthLogin(provider.authUrl)}
        >
          <OAuthIcon provider={provider.name} />
          <span className="ml-2">使用 {provider.displayName} 登录</span>
        </Button>
      ))}
    </div>
  )
}

/**
 * OAuth提供商图标组件
 */
function OAuthIcon({ provider }: { provider: string }) {
  switch (provider.toLowerCase()) {
    case 'github':
      return <Github className="h-4 w-4" />
    case 'google':
      // 可以添加Google图标
      return <div className="h-4 w-4 rounded-full bg-gradient-to-br from-red-500 to-yellow-500" />
    default:
      return null
  }
}
