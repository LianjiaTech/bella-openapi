import { useState, useEffect, useCallback, useRef } from "react"
import { getEndpointDetails } from "@/lib/api/meta"
import { processModelsArray } from "@/components/ui/modelFilterPanel/utils"
import { EndpointDetails } from "@/lib/types/openapi"
import { DEFAULT_ENDPOINT } from "@/lib/constants/constants"

/**
 * SessionStorage 缓存相关常量和类型定义
 */
const CACHE_KEY = 'models-default-endpoint-cache'
const CACHE_EXPIRY_TIME = 2 * 60 * 60 * 1000  // 2小时（单位：毫秒）

interface DefaultEndpointCache {
  endpoint: string
  features: EndpointDetails['features']
  models: EndpointDetails['models']
  timestamp: number
}

/**
 * 从 SessionStorage 读取缓存数据
 * 会自动检查缓存是否过期（超过2小时），过期则清除并返回 null
 */
function getCachedData(): DefaultEndpointCache | null {
  try {
    if (typeof window === 'undefined') return null
    const cached = sessionStorage.getItem(CACHE_KEY)
    if (!cached) return null

    const data: DefaultEndpointCache = JSON.parse(cached)

    // 检查缓存是否过期
    const now = Date.now()
    const age = now - data.timestamp

    if (age > CACHE_EXPIRY_TIME) {
      // 缓存已过期，清除并返回 null
      sessionStorage.removeItem(CACHE_KEY)
      console.log('Models cache expired, removed from storage')
      return null
    }

    return data
  } catch (err) {
    console.warn('Failed to read cached models data:', err)
    return null
  }
}

/**
 * 将数据写入 SessionStorage 缓存
 */
function setCachedData(features: EndpointDetails['features'], models: EndpointDetails['models']): void {
  try {
    if (typeof window === 'undefined') return
    const data: DefaultEndpointCache = {
      endpoint: DEFAULT_ENDPOINT,
      features,
      models,
      timestamp: Date.now()
    }
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(data))
  } catch (err) {
    console.warn('Failed to cache models data:', err)
  }
}

/**
 * 组合 Hook 返回值类型
 */
export interface UseEndpointDataReturn {
  endpoint: string | null
  features: EndpointDetails['features']
  models: EndpointDetails['models']
  initialLoading: boolean
  modelsLoading: boolean
  error: Error | null
  refetch: () => void
}

/**
 * 端点数据管理 Hook（单一 Hook 版本）
 *
 * 整合了状态管理、数据获取和业务编排逻辑，提供完整的端点数据管理功能
 *
 * @param endpoint - 端点名称
 * @param selectedTags - 选中的标签列表
 * @returns 端点数据和操作方法
 *
 * @example
 * ```tsx
 * const { features, models, initialLoading, error, refetch } =
 *   useEndpointData('chat.completion', ['gpt-4', 'claude'])
 * ```
 */
export const useEndpointData = (
  endpoint: string,
  selectedTags: string[]
): UseEndpointDataReturn => {
  // ========== 1. 状态定义 ==========
  const [endpointInfo, setEndpointInfo] = useState<string | null>(null)
  const [features, setFeatures] = useState<EndpointDetails['features']>([])
  const [models, setModels] = useState<EndpointDetails['models']>([])

  // Loading 状态：区分首次加载和局部更新
  const [initialLoading, setInitialLoading] = useState(false)
  const [modelsLoading, setModelsLoading] = useState(false)

  // 错误状态
  const [error, setError] = useState<Error | null>(null)

  // 使用 ref 保存上次的 endpoint，用于判断是否需要更新 features
  const prevEndpointRef = useRef<string>("")

  // 使用 ref 标记是否为页面初始化加载
  const isInitialLoadRef = useRef<boolean>(true)

  // ========== 2. 数据获取和状态更新逻辑 ==========
  /**
   * 获取端点数据并更新状态
   *
   * @param endpoint - 端点名称
   * @param tags - 标签列表
   * @param shouldUpdateFeatures - 是否需要更新 features（endpoint 变化时为 true）
   */
  const fetchAndUpdateState = useCallback(
    async (
      endpoint: string,
      tags: string[],
      shouldUpdateFeatures: boolean
    ) => {
      if (!endpoint) return

      // ========== 缓存检查：仅在页面初始化加载时使用缓存 ==========
      if (isInitialLoadRef.current) {
        const cached = getCachedData()
        if (cached) {
          // 缓存命中，使用缓存数据
          setEndpointInfo(cached.endpoint)
          setFeatures(cached.features)
          setModels(cached.models)

          // 标记初始化加载已完成
          isInitialLoadRef.current = false

          return // 跳过 API 请求
        }
      }

      // ========== 缓存未命中或非初始化加载：发起 API 请求 ==========
      try {
        // 根据是否更新 features 决定使用哪种 loading 状态
        if (shouldUpdateFeatures) {
          setInitialLoading(true)
        } else {
          setModelsLoading(true)
        }
        setError(null)

        // 获取原始数据
        const data = await getEndpointDetails(endpoint, "", tags)

        // 处理模型数据
        const processedModels = data?.models ? processModelsArray(data.models) : []

        // 根据 shouldUpdateFeatures 决定是否更新 features
        if (shouldUpdateFeatures) {
          // endpoint 变化时，完全更新所有数据
          setEndpointInfo(data?.endpoint || null)
          setFeatures(data?.features || [])
          setModels(processedModels)
        } else {
          // 仅 tags 变化时，只更新 models
          setModels(processedModels)
        }

        // ========== 初始化加载时写入缓存 ==========
        if (isInitialLoadRef.current) {
          setCachedData(data?.features || [], processedModels)
        }

        // 标记初始化加载已完成
        isInitialLoadRef.current = false

      } catch (err) {
        console.error("Error fetching endpoint details:", err)
        setError(err instanceof Error ? err : new Error("Unknown error"))
        setEndpointInfo(null)
        setFeatures([])
        setModels([])

        // 即使请求失败也标记初始化已完成
        isInitialLoadRef.current = false
      } finally {
        // 清除对应的 loading 状态
        if (shouldUpdateFeatures) {
          setInitialLoading(false)
        } else {
          setModelsLoading(false)
        }
      }
    },
    [] // 无外部依赖，更稳定
  )

  // ========== 3. 副作用监听 ==========
  /**
   * 当 endpoint 或 selectedTags 变化时，重新获取数据
   */
  useEffect(() => {
    if (endpoint) {
      // 判断是否是 endpoint 变化
      const isEndpointChanged = prevEndpointRef.current !== endpoint

      // 更新 ref
      if (isEndpointChanged) {
        prevEndpointRef.current = endpoint
      }

      // endpoint 变化时更新 features，仅 tags 变化时不更新 features
      fetchAndUpdateState(endpoint, selectedTags, isEndpointChanged)
    }
  }, [endpoint, selectedTags, fetchAndUpdateState])

  // ========== 4. 手动刷新方法 ==========
  /**
   * 手动刷新数据（强制更新所有数据包括 features）
   */
  const refetch = useCallback(() => {
    if (endpoint) {
      fetchAndUpdateState(endpoint, selectedTags, true)
    }
  }, [endpoint, selectedTags, fetchAndUpdateState])

  // ========== 5. 返回值 ==========
  return {
    endpoint: endpointInfo,
    features,
    models,
    initialLoading,
    modelsLoading,
    error,
    refetch,
  }
}
