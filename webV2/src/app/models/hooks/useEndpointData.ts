import { useState, useEffect, useCallback } from "react"
import { EndpointDetails } from "@/lib/types/openapi"
import { getEndpointDetails } from "@/lib/api/meta"
import { processModelsArray } from "../utils/model-data-processor"

/**
 * 自定义 Hook: 管理端点数据的获取和状态
 */
export const useEndpointData = (endpoint: string, selectedTags: string[]) => {
  const [endpointData, setEndpointData] = useState<EndpointDetails | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  /**
   * 获取并处理端点详情数据
   */
  const fetchAndProcessData = useCallback(async (endpoint: string, tags: string[]) => {
    if (!endpoint) return

    try {
      setLoading(true)
      setError(null)

      // 获取原始数据
      const data = await getEndpointDetails(endpoint, "", tags)

      // 处理模型数据
      if (data && data.models) {
        data.models = processModelsArray(data.models)
      }

      setEndpointData(data)
    } catch (err) {
      console.error('Error fetching endpoint details:', err)
      setError(err instanceof Error ? err : new Error('Unknown error'))
      setEndpointData(null)
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * 当 endpoint 或 selectedTags 变化时,重新获取数据
   */
  useEffect(() => {
    if (endpoint) {
      fetchAndProcessData(endpoint, selectedTags)
    }
  }, [endpoint, selectedTags, fetchAndProcessData])

  /**
   * 手动刷新数据
   */
  const refetch = useCallback(() => {
    if (endpoint) {
      fetchAndProcessData(endpoint, selectedTags)
    }
  }, [endpoint, selectedTags, fetchAndProcessData])

  return {
    endpointData,
    loading,
    error,
    refetch,
  }
}
