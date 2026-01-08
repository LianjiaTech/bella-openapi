import { useState, useEffect, useCallback, useRef } from "react"
import { EndpointDetails } from "@/lib/types/openapi"
import { getEndpointDetails } from "@/lib/api/meta"
import { processModelsArray } from "@/components/ui/models/utils"

/**
 * 自定义 Hook: 管理端点数据的获取和状态
 */
export const useEndpointData = (endpoint: string, selectedTags: string[]) => {
  // 拆分为三个独立的 state
  const [endpointInfo, setEndpointInfo] = useState<EndpointDetails['endpoint'] | null>(null)
  const [features, setFeatures] = useState<EndpointDetails['features']>([])
  const [models, setModels] = useState<EndpointDetails['models']>([])

  // 区分两种 loading 状态
  const [initialLoading, setInitialLoading] = useState(false) // 首次加载或切换 endpoint 时的 loading
  const [modelsLoading, setModelsLoading] = useState(false)   // 仅更新模型列表时的 loading
  const [error, setError] = useState<Error | null>(null)

  // 使用 ref 保存上次的 endpoint,用于判断是否需要更新 features
  const prevEndpointRef = useRef<string>("")

  /**
   * 获取并处理端点详情数据
   */
  const fetchAndProcessData = useCallback(async (endpoint: string, modelName: string = "", tags: string[], shouldUpdateFeatures: boolean) => {
    if (!endpoint) return

    try {
      // 根据是否更新 features 决定使用哪种 loading 状态
      if (shouldUpdateFeatures) {
        setInitialLoading(true)
      } else {
        setModelsLoading(true)
      }
      setError(null)

      // 获取原始数据
      const data = await getEndpointDetails(endpoint, modelName, tags)

      // 处理模型数据
      const processedModels = data?.models ? processModelsArray(data.models) : []

      // 根据 shouldUpdateFeatures 决定是否更新 features
      if (shouldUpdateFeatures) {
        // endpoint 变化时,完全更新所有数据(包括 endpoint、features 和 models)
        setEndpointInfo(data?.endpoint || null)
        setFeatures(data?.features || [])
        setModels(processedModels)
      } else {
        // 仅 tags 变化时,只更新 models,保持 endpoint 和 features 不变
        setModels(processedModels)
      }
    } catch (err) {
      console.error('Error fetching endpoint details:', err)
      setError(err instanceof Error ? err : new Error('Unknown error'))
      setEndpointInfo(null)
      setFeatures([])
      setModels([])
    } finally {
      // 清除对应的 loading 状态
      if (shouldUpdateFeatures) {
        setInitialLoading(false)
      } else {
        setModelsLoading(false)
      }
    }
  }, [])

  /**
   * 当 endpoint 或 selectedTags 变化时,重新获取数据
   */
  useEffect(() => {
    if (endpoint) {
      // 判断是否是 endpoint 变化
      const isEndpointChanged = prevEndpointRef.current !== endpoint
      // 更新 ref
      if (isEndpointChanged) {
        prevEndpointRef.current = endpoint
      }

      // endpoint 变化时更新 features,仅 tags 变化时不更新 features
      fetchAndProcessData(endpoint, "", selectedTags, isEndpointChanged)
    }
  }, [endpoint, selectedTags, fetchAndProcessData])

  /**
   * 手动刷新数据
   */
  const refetch = useCallback(() => {
    if (endpoint) {
      fetchAndProcessData(endpoint, "", selectedTags, true)
    }
  }, [endpoint, selectedTags, fetchAndProcessData])

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
