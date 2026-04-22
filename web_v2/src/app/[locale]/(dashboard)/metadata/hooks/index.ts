/**
 * Metadata Hooks - 统一的元数据管理 Hooks
 *
 * 本文件整合了所有元数据相关的 React Hooks:
 * 1. useMetadataData - 元数据管理 (状态 + 数据获取)
 * 2. useEndpoints - 端点列表管理
 */

import { useState, useEffect, useCallback, useRef } from 'react'
import { listConsoleModels, listSuppliers, listEndpoints } from '@/lib/api/metadata'
import { processModelsArray } from '@/components/ui/modelFilterPanel/utils'
import { Model, MetadataFeature, Endpoint } from '@/lib/types/openapi'

interface EndpointOption {
  value: string
  label: string
  code: string
}
export interface UseEndpointsReturn {
  endpoints: EndpointOption[]
  loading: boolean
  error: Error | null
  refetch: () => Promise<void>
}

/**
 * 元数据管理 Hook: 整合状态管理和数据获取
 * 职责: 管理元数据的状态、获取和更新
 */
export const useMetadataData = (endpoint: string, selectedTags: string[], selectedStatus: string|null, selectedVisibility: string|null) => {
  // 数据状态
  const [endpointInfo, setEndpointInfo] = useState<string | null>(null)
  const [features, setFeatures] = useState<MetadataFeature[]>([])
  const [models, setModels] = useState<Model[]>([])

  // Loading 状态: 区分首次加载和局部更新
  const [initialLoading, setInitialLoading] = useState(false)
  const [modelsLoading, setModelsLoading] = useState(false)

  // 错误状态
  const [error, setError] = useState<Error | null>(null)

  // 使用 ref 保存上次的 endpoint, 用于判断是否需要更新 endpoint 信息
  const prevEndpointRef = useRef<string>("")

  /**
   * 获取 suppliers 数据（独立获取，与 models 无关）
   * @returns suppliers 转换为 MetadataFeature 格式
   */
  async function fetchSuppliers(): Promise<MetadataFeature[]> {
    const suppliersList = await listSuppliers() || [];
    if (!suppliersList) {
      return [];
    }

    // 仅保留真实的 suppliers 转 MetadataFeature 格式
    return suppliersList.map(supplier => ({
      code: supplier,
      name: supplier
    }))
  }

  /**
   * 组件挂载时获取 suppliers（只执行一次）
   */
  useEffect(() => {
    const loadSuppliers = async () => {
      try {
        const featuresData = await fetchSuppliers()
        setFeatures(featuresData)
      } catch (err) {
        console.error("Error fetching suppliers:", err)
        setFeatures([])
      }
    }

    loadSuppliers()
  }, [])

  /**
   * 获取 models 数据（独立获取，根据 endpoint 和 tags 筛选）
   * @param endpoint 端点名称
   * @param tags 标签列表(suppliers)
   * @returns 处理后的模型列表
   */
  const fetchModels = useCallback(
    async (endpoint: string, tags: string[] = [], status: string|null, visibility: string|null): Promise<Model[]> => {
      console.log(status, visibility, 'status, visibility')
      if (!endpoint) {
        throw new Error("Endpoint is required")
      }

      const modelsData = await listConsoleModels(
        endpoint,
        "", // modelName: 空字符串获取所有模型
        tags.length > 0 ? tags.join(',') : "", // supplier: 如果有选中的 tags，传入作为 supplier 筛选
        status || "", // status: 只获取激活的模型
        visibility || "" // visibility: 空字符串获取所有可见性的模型
      )
      // 处理模型数据
      return modelsData ? processModelsArray(modelsData) : []
    },
    []
  )

  /**
   * 获取并更新状态（独立获取 suppliers 和 models）
   * @param endpoint 端点名称
   * @param tags 标签列表(suppliers)
   * @param shouldUpdateFeatures 是否更新 features（只在 endpoint 变化时为 true）
   */
  const fetchAndUpdateState = useCallback(
    async (
      endpoint: string,
      tags: string[],
      status: string|null,
      visibility: string|null,
      shouldUpdateFeatures: boolean
    ) => {
      if (!endpoint) return

      try {
        setError(null)

        if (shouldUpdateFeatures) {
          // endpoint 变化时: 并行获取 suppliers 和 models
          setInitialLoading(true)
          const [featuresData] = await Promise.all([
            fetchModels(endpoint, tags, status, visibility)
          ])
          setEndpointInfo(endpoint)
          // setFeatures(featuresData)
          // setModels(modelsData)
        } else {
          // 仅 tags 变化时: 只获取 models，不重新获取 suppliers
          setModelsLoading(true)
          const modelsData = await fetchModels(endpoint, tags, status, visibility)
          setModels(modelsData)
        }
      } catch (err) {
        console.error("Error fetching metadata details:", err)
        setError(err instanceof Error ? err : new Error("Unknown error"))
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
    },
    [fetchSuppliers, fetchModels]
  )

  /**
   * 当 endpoint 或 selectedTags 变化时,重新获取数据
   */
  useEffect(() => {
    if (!endpoint) {
      // endpoint 为空时,清空所有数据
      setEndpointInfo(null)
      setFeatures([])
      setModels([])
      setError(null)
      return
    }

    // 判断是否是 endpoint 变化
    const isEndpointChanged = prevEndpointRef.current !== endpoint
    if (isEndpointChanged) {
      prevEndpointRef.current = endpoint
    }

    // 执行数据获取
    const fetchData = async () => {
      try {
        setError(null)

        if (isEndpointChanged) {
          // endpoint 变化时: 更新 endpoint 信息并获取 models
          setInitialLoading(true)
          const modelsData = await fetchModels(endpoint, selectedTags, selectedStatus, selectedVisibility)
          setEndpointInfo(endpoint)
          setModels(modelsData)
          setInitialLoading(false)
        } else {
          // 仅 tags/status/visibility 变化时: 只获取 models
          setModelsLoading(true)
          const modelsData = await fetchModels(endpoint, selectedTags, selectedStatus, selectedVisibility)
          setModels(modelsData)
          setModelsLoading(false)
        }
      } catch (err) {
        console.error("Error fetching metadata details:", err)
        setError(err instanceof Error ? err : new Error("Unknown error"))
        setEndpointInfo(null)
        setModels([])
        setInitialLoading(false)
        setModelsLoading(false)
      }
    }

    fetchData()

    // 清理函数:在 endpoint 变化或组件卸载时,清除加载状态和错误状态
    return () => {
      setInitialLoading(false)
      setModelsLoading(false)
      setError(null)
    }
  }, [endpoint, selectedTags, selectedStatus, selectedVisibility, fetchModels])

  /**
   * 手动刷新数据
   */
  const refetch = useCallback(() => {
    if (endpoint) {
      fetchAndUpdateState(endpoint, selectedTags, selectedStatus, selectedVisibility, true)
    }
  }, [endpoint, selectedTags, selectedStatus, selectedVisibility, fetchAndUpdateState])

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

// ==================== 2. Endpoints 列表管理 Hook ====================

/**
 * Endpoints 数据获取 Hook
 *
 * 职责:
 * - 获取激活状态的能力点列表
 * - 提供加载、错误状态
 * - 转换数据格式供下拉选择使用
 * - 支持手动刷新
 *
 * @returns {UseEndpointsReturn} endpoints 数据、加载状态、错误状态、刷新方法
 */
export const useEndpoints = (): UseEndpointsReturn => {
  const [endpoints, setEndpoints] = useState<EndpointOption[]>([])
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<Error | null>(null)

  /**
   * 获取并处理 endpoints 数据
   */
  const fetchEndpoints = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      // 调用 API 获取激活状态的端点
      const data = await listEndpoints('active')

      // 转换为下拉选项格式
      const options: EndpointOption[] = data.map((endpoint: Endpoint) => ({
        value: endpoint.endpoint,
        label: endpoint.endpoint,
        code: endpoint.endpoint,
      }))

      setEndpoints(options)
    } catch (err) {
      console.error('Error fetching endpoints:', err)
      const errorMessage = err instanceof Error ? err : new Error('获取能力点列表失败')
      setError(errorMessage)
      setEndpoints([])
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * 组件挂载时自动获取数据
   */
  useEffect(() => {
    fetchEndpoints()
  }, [fetchEndpoints])

  /**
   * 手动刷新数据
   */
  const refetch = useCallback(async () => {
    await fetchEndpoints()
  }, [fetchEndpoints])

  return {
    endpoints,
    loading,
    error,
    refetch,
  }
}
