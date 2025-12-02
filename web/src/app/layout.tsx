import type React from "react"
import type { Metadata } from "next"
import "./globals.css"
import { AppSidebar } from "@/components/app-sidebar"
import { ThemeProvider } from "@/components/theme-provider"
import { LanguageProvider } from "@/components/language-provider"
import { UserProvider } from   "@/lib/context/user-context"

export const metadata: Metadata = {
  title: "Bella - AI OpenAPI Platform",
  description: "Bella - 大模型 OpenAPI 能力平台 - 提供 ChatCompletion、图像生成、语音识别等 AI 能力",
  generator: "v0.app",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <ThemeProvider defaultTheme="dark">
          <UserProvider>
          <LanguageProvider>
            <div className="flex h-screen overflow-hidden bg-background">
              <AppSidebar />
              <main className="flex-1 overflow-hidden pl-64">{children}</main>
            </div>
          </LanguageProvider>
          </UserProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
