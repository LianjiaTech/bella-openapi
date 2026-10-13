import { useState, useEffect, useCallback } from 'react'
import { listProtocols } from '@/lib/api/metadata'
import { transformProtocolsToOptions } from '../utils/formHelpers'
import type { ProtocolOption, DialogMode } from '../types'

interface UseProtocolDataProps {
  modelName?: string
  mode: DialogMode
  open: boolean
}

/**
 * 协议数据管理 Hook
 * 负责加载和管理协议列表数据
 */
export function useProtocolData({
  modelName,
  mode,
  open,
}: UseProtocolDataProps) {
  // 协议列表
  const [protocols, setProtocols] = useState<ProtocolOption[]>([])

  // 加载状态
  const [loading, setLoading] = useState(false)

  // 错误信息
  const [error, setError] = useState<string | null>(null)

  /**
   * 加载协议列表
   */
  const loadProtocols = useCallback(async () => {
    if (!modelName) {
      setError('缺少必要参数: modelName')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const data = await listProtocols('model', modelName)
      const options = transformProtocolsToOptions(data)
      setProtocols(options)
      setError(null)
    } catch (err) {
      console.error('获取协议列表失败:', err)
      const errorMessage = err instanceof Error ? err.message : '获取协议列表失败'
      setError(errorMessage)
      setProtocols([])
    } finally {
      setLoading(false)
    }
  }, [modelName])

  /**
   * 重试加载
   */
  const retry = useCallback(() => {
    loadProtocols()
  }, [loadProtocols])

  /**
   * 根据模式和打开状态自动加载协议列表
   */
  useEffect(() => {
    if (!open) return

    // 新增模式: 必须先加载协议列表
    if (mode === 'create') {
      loadProtocols()
    }
    // 编辑模式: 后台静默加载(用于显示友好名称)
    else if (mode === 'edit' && modelName) {
      listProtocols('model', modelName)
        .then((data) => {
          const options = transformProtocolsToOptions(data)
          setProtocols(options)
        })
        .catch((err) => {
          console.error('获取协议列表失败(编辑模式,非阻塞):', err)
          // 编辑模式下协议字段禁用,加载失败不影响功能
        })
    }
  }, [open, mode, modelName, loadProtocols])

  return {
    protocols,
    loading,
    error,
    retry,
    loadProtocols,
  }
}
