"use client"

import { useState, useEffect } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { openapi } from "@/lib/api/openapi"

interface Provider {
  type: string
  authUrl: string
}

export function useLogin() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const redirect = searchParams.get("redirect") || "/"

  // 登录方式
  const [loginMethod, setLoginMethod] = useState<"oauth" | "secret">("secret")

  // OAuth 相关
  const [providers, setProviders] = useState<Provider[]>([])
  const [loadingProviders, setLoadingProviders] = useState(false)

  // 密钥登录
  const [managerAk, setManagerAk] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [secretError, setSecretError] = useState("")

  /**
   * 1. 首屏：检查是否已登录
   */
  useEffect(() => {
    openapi
      .get("/openapi/userInfo")
      .then((res) => {
        if (res.data?.userId) {
          // router.push(redirect)
        } else {
          // loadOAuthConfig()
        }
      })
      .catch(() => {
        // loadOAuthConfig()
      })
  }, [redirect])

  /**
   * 2. 拉取 OAuth provider 配置
   */
  const loadOAuthConfig = () => {
    setLoadingProviders(true)
    openapi
      .get(`/openapi/oauth/config${redirect ? `?redirect=${encodeURIComponent(redirect)}` : ""}`)
      .then((res) => {
        setProviders(res.data || [])
      })
      .catch(() => {
        setProviders([])
      })
      .finally(() => {
        setLoadingProviders(false)
      })
  }

  /**
   * 3. OAuth 登录
   */
  const handleOAuthLogin = (url: string) => {
    window.location.href = url
  }

  /**
   * 4. 密钥登录
   */
  const handleSecretLogin = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!managerAk.trim()) {
      setSecretError("请输入密钥")
      return
    }

    setSecretError("")
    setIsLoading(true)

    try {
      const res = await openapi.post("/openapi/login", {
        secret: managerAk.trim(),
      })

      if (res.data) {
        router.push(redirect)
      } else {
        setSecretError("登录失败，请检查密钥是否正确")
      }
    } catch (err: any) {
      console.error("Secret login failed:", err)

      if (err.response?.data?.message) {
        setSecretError(err.response.data.message)
      } else if (err.response?.status === 503) {
        setSecretError("未实现密钥登录功能")
      } else {
        setSecretError("登录失败，请检查密钥是否正确")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return {
    // 状态
    loginMethod,
    setLoginMethod,
    providers,
    loadingProviders,
    managerAk,
    setManagerAk,
    isLoading,
    secretError,

    // 动作
    handleSecretLogin,
    handleOAuthLogin,
  }
}
