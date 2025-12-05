"use client"

import { Suspense, useEffect, useState } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { Button } from "@/components/ui/button"
import { LucideIcon, Mail as MailIcon, Github, Twitter, Facebook, Key } from "lucide-react"
import { ClientHeader } from "@/components/user/client-header"
import { openapi } from '@/lib/api/openapi'
import {UserInfo} from "@/lib/types/openapi";
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Alert, AlertDescription } from "@/components/ui/alert"

// 定义 provider 类型
interface Provider {
  type: string
  authUrl: string
}

// 定义特定 provider 的图标映射
const providerIcons: Record<string, LucideIcon> = {
  google: MailIcon,
  github: Github,
  twitter: Twitter,
  facebook: Facebook,
}

function LoginContent() {
  const [providers, setProviders] = useState<Provider[]>([])
  const [loading, setLoading] = useState(false)
  const [secret, setSecret] = useState('')
  const [secretError, setSecretError] = useState('')
  const [secretLoading, setSecretLoading] = useState(false)
  const searchParams = useSearchParams()
  const router = useRouter()
  const redirect = searchParams.get('redirect')

  useEffect(() => {
    // 检查是否已登录
    openapi.get<UserInfo>('/openapi/userInfo')
      .then(response => {
        console.log(response)
        if (response.data?.userId) {
          // 已登录，根据 redirect 参数决定跳转
          router.push(redirect || '/')
        } else {
          // 未登录，获取 OAuth 配置
          loadOAuthConfig()
        }
      })
      .catch(() => {
        // 获取用户信息失败，加载 OAuth 配置
        loadOAuthConfig()
      })
  }, [redirect, router])

  const loadOAuthConfig = () => {
    setLoading(true)
    openapi.get<Provider[]>(`/openapi/oauth/config${redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''}`)
      .then(response => {
        setProviders(response.data || [])
      })
      .catch(error => {
        console.error('Failed to fetch auth URLs:', error)
        // 出错时设置空的 providers 列表
        setProviders([])
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const getIcon = (provider: Provider): LucideIcon => {
    return providerIcons[provider.type.toLowerCase()] || MailIcon
  }

  const getProviderName = (type: string): string => {
    const nameMap: Record<string, string> = {
      'google': 'Google',
      'github': 'GitHub',
      'twitter': 'Twitter',
      'facebook': 'Facebook'
    }
    return nameMap[type.toLowerCase()] || type
  }

  const handleLogin = (authUrl: string) => {
    window.location.href = authUrl
  }

  const handleSecretLogin = async () => {
    if (!secret.trim()) {
      setSecretError('请输入密钥')
      return
    }

    setSecretLoading(true)
    setSecretError('')

    try {
      const response = await openapi.post('/openapi/login', { secret: secret.trim() })

      if (response.data) {
        // 登录成功，跳转到首页或重定向页面
        router.push(redirect || '/')
      } else {
        setSecretError('登录失败，请检查密钥是否正确')
      }
    } catch (error: any) {
      console.error('Secret login failed:', error)
      if (error.response?.data?.message) {
        setSecretError(error.response.data.message)
      } else if (error.response?.status === 503) {
        setSecretError('未实现密钥登录功能')
      } else {
        setSecretError('登录失败，请检查密钥是否正确')
      }
    } finally {
      setSecretLoading(false)
    }
  }

  return (
    <>
      <div className='bg-gray-50'>
        <ClientHeader title="Bella Openapi" />
      </div>
      <main className="flex-1 bg-gradient-to-br from-blue-50 to-indigo-50">
        <div className="flex items-center justify-center px-4 sm:px-6 lg:px-8 min-h-[calc(100vh-64px)] py-12">
          <div className="w-full max-w-md">
            <div className="bg-white rounded-lg shadow-lg overflow-hidden">
              <div className="px-8 py-12">
                <div className="text-center mb-8">
                  <h1 className="text-2xl font-semibold tracking-tight text-gray-900">
                    登录
                  </h1>
                  <p className="mt-2 text-sm text-gray-600">
                    请选择登录方式继续
                  </p>
                </div>

                <Tabs defaultValue="oauth" className="w-full">
                  <TabsList className="grid w-full grid-cols-2 mb-6">
                    <TabsTrigger value="oauth">OAuth登录</TabsTrigger>
                    <TabsTrigger value="secret">密钥登录</TabsTrigger>
                  </TabsList>

                  <TabsContent value="oauth" className="space-y-3">
                    {loading ? (
                      <div className="text-center text-gray-500">
                        加载登录选项...
                      </div>
                    ) : providers.length > 0 ? (
                      providers.map((provider) => {
                        const Icon = getIcon(provider)
                        const name = getProviderName(provider.type)

                        return (
                          <Button
                            key={provider.type}
                            className="w-full h-12 shadow-sm bg-white hover:bg-gray-50 text-gray-900 border border-gray-200 transition-all duration-200 ease-in-out transform hover:scale-[1.02]"
                            onClick={() => handleLogin(provider.authUrl)}
                          >
                            <Icon className="mr-2 h-5 w-5" />
                            使用{name}账号登录
                          </Button>
                        )
                      })
                    ) : (
                      <div className="text-center text-gray-500">
                        没有可用的登录选项
                      </div>
                    )}
                  </TabsContent>

                  <TabsContent value="secret" className="space-y-4">
                    {secretError && (
                      <Alert variant="destructive" className="mb-4">
                        <AlertDescription>{secretError}</AlertDescription>
                      </Alert>
                    )}
                    <div className="space-y-2">
                      <Input
                        type="password"
                        placeholder="请输入密钥"
                        value={secret}
                        onChange={(e) => setSecret(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            handleSecretLogin();
                          }
                        }}
                      />
                      <Button
                        className="w-full"
                        onClick={handleSecretLogin}
                        disabled={secretLoading}
                      >
                        <Key className="mr-2 h-4 w-4" />
                        {secretLoading ? '登录中...' : '使用密钥登录'}
                      </Button>
                    </div>
                  </TabsContent>
                </Tabs>
              </div>
              <div className="px-8 py-4 bg-gray-50 border-t border-gray-100">
                <p className="text-xs text-center text-gray-500">
                  登录即表示您同意我们的服务条款和隐私政策
                </p>
              </div>
            </div>
          </div>
        </div>
      </main>
    </>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <LoginContent />
    </Suspense>
  )
}
