"use client"

import { useEffect, useState } from "react"
import { useAuth } from "@/components/providers/auth-provider"
import { Button } from "@/components/common/button"
import { Github } from "lucide-react"
import type { OAuthProvider } from "@/lib/types/auth"
import { toast } from "sonner"

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
      toast.error('OAuth配置加载失败')
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
