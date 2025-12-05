"use client"

import { createContext, useContext, useState, useEffect, ReactNode, useCallback } from "react"
import { EndpointDetails } from "@/lib/types/openapi"
import { getEndpointDetails } from "@/lib/api/meta"

interface PlaygroundContextValue {
  endpointDetails: EndpointDetails | null
  loading: boolean
  error: Error | null
  currentEndpoint: string
  refetch: (endpoint?: string, modelName?: string, features?: string[]) => Promise<void>
  setCurrentEndpoint: (endpoint: string) => void
}

const PlaygroundContext = createContext<PlaygroundContextValue | undefined>(undefined)

interface PlaygroundProviderProps {
  children: ReactNode
  defaultEndpoint?: string
  defaultModelName?: string
  defaultFeatures?: string[]
}

/**
 * PlaygroundProvider - 为所有 Playground 子页面提供共享的端点数据
 */
export function PlaygroundProvider({
  children,
  defaultEndpoint = "chat/completions",
  defaultModelName = "",
  defaultFeatures = [],
}: PlaygroundProviderProps) {
  const [endpointDetails, setEndpointDetails] = useState<EndpointDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [currentEndpoint, setCurrentEndpoint] = useState(defaultEndpoint)

  /**
   * 获取端点详情数据
   */
  const fetchEndpointDetails = useCallback(
    async (endpoint: string, modelName: string = "", features: string[] = []) => {
      if (!endpoint) return

      try {
        setLoading(true)
        setError(null)

        const data = await getEndpointDetails(endpoint, modelName, features)
        setEndpointDetails(data)
      } catch (err) {
        console.error("Error fetching endpoint details:", err)
        setError(err instanceof Error ? err : new Error("Unknown error"))
        setEndpointDetails(null)
      } finally {
        setLoading(false)
      }
    },
    []
  )

  /**
   * 手动刷新数据
   */
  const refetch = useCallback(
    async (endpoint?: string, modelName?: string, features?: string[]) => {
      const targetEndpoint = endpoint || currentEndpoint
      await fetchEndpointDetails(targetEndpoint, modelName || defaultModelName, features || defaultFeatures)
      if (endpoint && endpoint !== currentEndpoint) {
        setCurrentEndpoint(endpoint)
      }
    },
    [currentEndpoint, defaultModelName, defaultFeatures, fetchEndpointDetails]
  )

  /**
   * 初始化加载数据
   */
  useEffect(() => {
    fetchEndpointDetails(defaultEndpoint, defaultModelName, defaultFeatures)
  }, [defaultEndpoint, defaultModelName, defaultFeatures, fetchEndpointDetails])

  const value: PlaygroundContextValue = {
    endpointDetails,
    loading,
    error,
    currentEndpoint,
    refetch,
    setCurrentEndpoint,
  }

  return <PlaygroundContext.Provider value={value}>{children}</PlaygroundContext.Provider>
}

/**
 * 使用 Playground Context 的 Hook
 */
export function usePlaygroundContext() {
  const context = useContext(PlaygroundContext)
  if (context === undefined) {
    throw new Error("usePlaygroundContext must be used within a PlaygroundProvider")
  }
  return context
}

