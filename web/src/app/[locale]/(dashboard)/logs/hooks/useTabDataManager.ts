import { useState, useRef, useCallback } from 'react'

/**
 * 单个 Tab 的数据状态
 */
interface TabDataState {
  /** 查询结果数据 */
  data: any[]
  /** 是否已加载过（用于判断是否需要自动请求） */
  isLoaded: boolean
  /** 当前是否正在加载 */
  isLoading: boolean
}

/**
 * Tab 数据管理 Hook
 * 用于管理多个 tab 的独立数据状态和缓存
 */
export function useTabDataManager() {
  // 使用 useRef 存储 Map，避免重渲染导致数据丢失
  const tabDataMapRef = useRef<Map<string, TabDataState>>(new Map())

  // 使用 useState 触发组件重新渲染
  const [, forceUpdate] = useState({})

  /**
   * 获取指定 tab 的数据状态
   * @param serviceId - 服务 ID
   * @returns Tab 数据状态
   */
  const getTabData = useCallback((serviceId: string): TabDataState => {
    const existingData = tabDataMapRef.current.get(serviceId)

    // 如果不存在，返回默认空状态
    if (!existingData) {
      return {
        data: [],
        isLoaded: false,
        isLoading: false
      }
    }

    return existingData
  }, [])

  /**
   * 设置指定 tab 的数据并标记为已加载
   * @param serviceId - 服务 ID
   * @param data - 查询结果数据
   */
  const setTabData = useCallback((serviceId: string, data: any[]) => {
    tabDataMapRef.current.set(serviceId, {
      data,
      isLoaded: true,
      isLoading: false
    })

    // 触发组件重新渲染
    forceUpdate({})
  }, [])

  /**
   * 判断指定 tab 是否已加载过
   * @param serviceId - 服务 ID
   * @returns 是否已加载
   */
  const isTabLoaded = useCallback((serviceId: string): boolean => {
    const tabData = tabDataMapRef.current.get(serviceId)
    return tabData?.isLoaded ?? false
  }, [])

  /**
   * 设置指定 tab 的加载状态
   * @param serviceId - 服务 ID
   * @param loading - 是否正在加载
   */
  const setTabLoading = useCallback((serviceId: string, loading: boolean) => {
    const existingData = tabDataMapRef.current.get(serviceId)

    tabDataMapRef.current.set(serviceId, {
      data: existingData?.data ?? [],
      isLoaded: existingData?.isLoaded ?? false,
      isLoading: loading
    })

    // 触发组件重新渲染
    forceUpdate({})
  }, [])

  /**
   * 清空所有 tab 的缓存
   * 用于筛选条件变化或重置时
   */
  const clearAllCache = useCallback(() => {
    tabDataMapRef.current.clear()

    // 触发组件重新渲染
    forceUpdate({})
  }, [])

  return {
    getTabData,
    setTabData,
    isTabLoaded,
    setTabLoading,
    clearAllCache
  }
}
