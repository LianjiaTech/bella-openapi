"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/components/providers/auth-provider"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import { useToast } from "@/hooks/use-toast"

interface LoginFormProps {
  redirect?: string
}

/**
 * 密钥登录表单组件
 * 支持用户使用密钥（secret）进行登录
 */
export function LoginForm({ redirect = '/overview' }: LoginFormProps) {
  const [secret, setSecret] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()
  const router = useRouter()
  const { toast } = useToast()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!secret.trim()) {
      toast({
        title: '错误',
        description: '请输入密钥',
        variant: 'destructive'
      })
      return
    }

    setIsLoading(true)
    try {
      await login(secret)
      toast({
        title: '登录成功',
        description: '欢迎回来！'
      })
      router.push(redirect)
    } catch (error) {
      toast({
        title: '登录失败',
        description: error instanceof Error ? error.message : '密钥无效',
        variant: 'destructive'
      })
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="secret">密钥</Label>
        <Input
          id="secret"
          type="password"
          placeholder="请输入您的密钥"
          value={secret}
          onChange={(e) => setSecret(e.target.value)}
          disabled={isLoading}
          autoComplete="off"
        />
      </div>

      <Button
        type="submit"
        className="w-full"
        disabled={isLoading}
      >
        {isLoading ? '登录中...' : '登录'}
      </Button>
    </form>
  )
}
