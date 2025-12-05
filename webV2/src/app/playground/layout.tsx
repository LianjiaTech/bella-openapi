"use client"

import type { ReactNode } from "react"
import { useSearchParams } from "next/navigation"
import { PlaygroundProvider } from "@/lib/context/playground-context"

export default function PlaygroundLayout({ children }: { children: ReactNode }) {
  const searchParams = useSearchParams()
  
  // 从 URL 查询参数获取 endpoint，如果没有则使用默认值
  const endpoint = searchParams.get("endpoint") || "/v1/chat/completions"

  return (
    <PlaygroundProvider
      defaultEndpoint={endpoint}
      defaultModelName=""
      defaultFeatures={[]}
    >
      {children}
    </PlaygroundProvider>
  )
}

