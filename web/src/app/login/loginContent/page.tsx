"use client"

import Link from "next/link"
import { Sparkles, Lock, ArrowRight, MailIcon } from "lucide-react"

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Alert, AlertDescription } from "@/components/ui/alert"

import { useLogin } from "@/hooks/use-login"

export default function LoginContent() {
  const {
    loginMethod,
    setLoginMethod,
    providers,
    loadingProviders,
    managerAk,
    setManagerAk,
    isLoading,
    secretError,
    handleSecretLogin,
    handleOAuthLogin,
  } = useLogin()

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="w-full max-w-md">
        {/* 顶部 Logo */}
        <div className="mb-8 text-center">
          <Link href="/" className="inline-flex items-center gap-2">
            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
              <Sparkles className="h-7 w-7 text-primary-foreground" />
            </div>
          </Link>

          <h1 className="mt-6 text-3xl font-bold text-blue-500">欢迎回来</h1>
          <p className="mt-2 text-muted-foreground">登录您的账户以继续使用</p>
        </div>

        {/* 登录卡片 */}
        <Card>
          <CardHeader>
            <CardTitle>登录</CardTitle>
            <CardDescription>选择登录方式</CardDescription>
          </CardHeader>

          <CardContent>
            {/* Login 方法切换 */}
            <div className="mb-6 flex gap-2 rounded-lg bg-muted p-1">
              <button
                type="button"
                onClick={() => setLoginMethod("oauth")}
                className={`flex-1 rounded-md px-3 py-2 text-sm font-medium ${
                  loginMethod === "oauth"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                OAuth 登录
              </button>

              <button
                type="button"
                onClick={() => setLoginMethod("secret")}
                className={`flex-1 rounded-md px-3 py-2 text-sm font-medium ${
                  loginMethod === "secret"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                密钥登录
              </button>
            </div>

            {/* OAuth 登录 UI */}
            {loginMethod === "oauth" && (
              <div className="space-y-3">
                {loadingProviders && (
                  <p className="text-center text-muted-foreground">加载登录选项...</p>
                )}

                {!loadingProviders &&
                  providers.map((p) => (
                    <Button
                      key={p.type}
                      className="w-full h-12 shadow-sm bg-white border border-gray-200"
                      onClick={() => handleOAuthLogin(p.authUrl)}
                    >
                      <MailIcon className="mr-2 h-5 w-5" />
                      使用 {p.type} 登录
                    </Button>
                  ))}

                {!loadingProviders && providers.length === 0 && (
                  <div className="rounded-lg bg-muted p-8 text-center text-sm text-muted-foreground">
                    暂无可用登录方式
                  </div>
                )}
              </div>
            )}

            {/* 密钥登录 UI */}
            {loginMethod === "secret" && (
              <form onSubmit={handleSecretLogin} className="space-y-4">
                {secretError && (
                  <Alert variant="destructive">
                    <AlertDescription>{secretError}</AlertDescription>
                  </Alert>
                )}

                <div className="space-y-2">
                  <Label htmlFor="manager-ak">密钥登录</Label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      id="manager-ak"
                      type="password"
                      value={managerAk}
                      onChange={(e) => setManagerAk(e.target.value)}
                      placeholder="请输入您的API Key"
                      className="pl-10"
                    />
                  </div>
                </div>

                <Button type="submit" className="w-full bg-blue-500 hover:bg-blue-600 text-white" disabled={isLoading}>
                  {isLoading ? "登录中..." : "登录"}
                  {!isLoading && <ArrowRight className="ml-2 h-4 w-4" />}
                </Button>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
