import { useState, useEffect, useCallback } from 'react'
import { getPriceInfoSchema, getChannelInfoSchema } from '@/lib/api/metadata'
import {
  initializeValuesFromSchema,
  serializeToJson,
} from '../utils/formHelpers'
import type { JsonSchema } from '@/lib/types/metadata'
import type { DialogMode } from '../types'

interface UseSchemaDataProps {
  modelName?: string
  protocol: string
  mode: DialogMode
  open: boolean
  initialPriceInfo?: string
  initialChannelInfo?: string
  onPriceInfoUpdate: (jsonString: string) => void
  onChannelInfoUpdate: (jsonString: string) => void
}

/**
 * Schema 数据管理 Hook
 * 负责管理价格信息和渠道配置的 Schema、字段值及加载状态
 */
export function useSchemaData({
  modelName,
  protocol,
  mode,
  open,
  initialPriceInfo,
  initialChannelInfo,
  onPriceInfoUpdate,
  onChannelInfoUpdate,
}: UseSchemaDataProps) {
  // ========== 价格信息 Schema ==========
  const [priceInfoSchema, setPriceInfoSchema] = useState<JsonSchema>({
    params: [],
  })
  const [priceInfoValues, setPriceInfoValues] = useState<Record<string, any>>(
    {}
  )
  const [priceInfoLoading, setPriceInfoLoading] = useState(false)
  const [priceInfoError, setPriceInfoError] = useState<string | null>(null)

  // ========== 渠道信息 Schema ==========
  const [channelInfoSchema, setChannelInfoSchema] = useState<JsonSchema>({
    params: [],
  })
  const [channelInfoValues, setChannelInfoValues] = useState<
    Record<string, any>
  >({})
  const [channelInfoLoading, setChannelInfoLoading] = useState(false)
  const [channelInfoError, setChannelInfoError] = useState<string | null>(null)

  /**
   * 加载价格信息 Schema
   */
  const loadPriceInfoSchema = useCallback(async () => {
    if (!modelName) return

    setPriceInfoLoading(true)
    setPriceInfoError(null)

    try {
      const schema = await getPriceInfoSchema('model', modelName)
      setPriceInfoSchema(schema)

      // 初始化字段值
      const initialValues = initializeValuesFromSchema(
        schema,
        initialPriceInfo
      )
      setPriceInfoValues(initialValues)

      // 更新表单的 priceInfo 字段
      onPriceInfoUpdate(serializeToJson(initialValues))
    } catch (err) {
      console.error('获取价格信息 Schema 失败:', err)
      const errorMessage =
        err instanceof Error ? err.message : '获取价格配置失败'
      setPriceInfoError(errorMessage)
    } finally {
      setPriceInfoLoading(false)
    }
  }, [modelName, initialPriceInfo, onPriceInfoUpdate])

  /**
   * 加载渠道信息 Schema
   */
  const loadChannelInfoSchema = useCallback(async () => {
    if (!modelName || !protocol) return

    setChannelInfoLoading(true)
    setChannelInfoError(null)

    try {
      const schema = await getChannelInfoSchema('model', modelName, protocol)
      setChannelInfoSchema(schema)

      // 初始化字段值
      const initialValues = initializeValuesFromSchema(
        schema,
        initialChannelInfo
      )
      setChannelInfoValues(initialValues)

      // 更新表单的 channelInfo 字段
      onChannelInfoUpdate(serializeToJson(initialValues))
    } catch (err) {
      console.error('获取渠道信息 Schema 失败:', err)
      const errorMessage =
        err instanceof Error ? err.message : '获取渠道配置失败'
      setChannelInfoError(errorMessage)
      setChannelInfoSchema({ params: [] })
    } finally {
      setChannelInfoLoading(false)
    }
  }, [modelName, protocol, initialChannelInfo, onChannelInfoUpdate])

  /**
   * 更新价格信息字段值
   */
  const updatePriceInfoField = useCallback(
    (fieldCode: string, value: any) => {
      const updatedValues = {
        ...priceInfoValues,
        [fieldCode]: value,
      }
      setPriceInfoValues(updatedValues)

      // 更新表单的 priceInfo 字段
      onPriceInfoUpdate(serializeToJson(updatedValues))
    },
    [priceInfoValues, onPriceInfoUpdate]
  )

  /**
   * 更新渠道信息字段值
   */
  const updateChannelInfoField = useCallback(
    (fieldCode: string, value: any) => {
      const updatedValues = {
        ...channelInfoValues,
        [fieldCode]: value,
      }
      setChannelInfoValues(updatedValues)

      // 更新表单的 channelInfo 字段
      onChannelInfoUpdate(serializeToJson(updatedValues))
    },
    [channelInfoValues, onChannelInfoUpdate]
  )

  /**
   * 加载价格信息 Schema (新增模式或编辑模式)
   */
  useEffect(() => {
    if (!open || !modelName) return

    // 新增模式: 立即加载
    if (mode === 'create') {
      loadPriceInfoSchema()
    }
    // 编辑模式: 后台静默加载
    else if (mode === 'edit') {
      getPriceInfoSchema('model', modelName)
        .then((schema) => {
          setPriceInfoSchema(schema)
          // 解析现有数据
          const initialValues = initializeValuesFromSchema(
            schema,
            initialPriceInfo
          )
          setPriceInfoValues(initialValues)
        })
        .catch((err) => {
          console.error('获取价格信息 schema 失败(编辑模式,非阻塞):', err)
        })
    }
  }, [open, mode, modelName, initialPriceInfo, loadPriceInfoSchema])

  /**
   * 监听协议变化,加载渠道信息 Schema (仅新增模式)
   */
  useEffect(() => {
    if (!open || mode !== 'create' || !protocol || !modelName) return

    loadChannelInfoSchema()
  }, [open, mode, protocol, modelName, loadChannelInfoSchema])

  /**
   * 编辑模式: 加载渠道信息 Schema
   */
  useEffect(() => {
    if (!open || mode !== 'edit' || !protocol || !modelName) return

    getChannelInfoSchema('model', modelName, protocol)
      .then((schema) => {
        setChannelInfoSchema(schema)
        // 解析现有数据
        const initialValues = initializeValuesFromSchema(
          schema,
          initialChannelInfo
        )
        setChannelInfoValues(initialValues)
      })
      .catch((err) => {
        console.error('获取渠道信息 schema 失败(编辑模式,非阻塞):', err)
      })
  }, [open, mode, protocol, modelName, initialChannelInfo])

  return {
    // 价格信息相关
    priceInfoSchema,
    priceInfoValues,
    priceInfoLoading,
    priceInfoError,
    updatePriceInfoField,
    loadPriceInfoSchema,

    // 渠道信息相关
    channelInfoSchema,
    channelInfoValues,
    channelInfoLoading,
    channelInfoError,
    updateChannelInfoField,
    loadChannelInfoSchema,
  }
}
