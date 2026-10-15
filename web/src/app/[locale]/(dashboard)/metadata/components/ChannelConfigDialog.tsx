'use client'

import { useState, useEffect, useRef } from 'react'
import { Info } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/common/dialog'
import { Button } from '@/components/common/button'
import { Switch } from '@/components/common/switch'
import { Label } from '@/components/common/label'
import { Input } from '@/components/common/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/common/tooltip'
import type { ChannelDetails } from '@/lib/types/metadata'
import type { JsonSchema } from '@/lib/types/metadata'
import { listProtocols, getPriceInfoSchema, getChannelInfoSchema, createChannel, updateChannel, createPrivateChannel, updatePrivateChannel } from '@/lib/api/metadata'
import { FieldRenderer } from './fieldRenderer/FieldRenderer'
import { priorityOptions } from '@/lib/constants/metadata'
import { TOKEN_PRICE_FIELDS, shouldConvertTokenPrice, convertSchemaLabels, convertSpeakerHourlyPriceObj, convertTtsPriceObj, convertVideoDurationPriceObj } from '@/lib/utils/price'

function isTokenBased(schema: JsonSchema, existingUnit?: string): boolean {
  if (shouldConvertTokenPrice(existingUnit)) return true
  return schema.params.some(p =>
    p.code === 'tiers' ||
    shouldConvertTokenPrice(undefined, p.name) ||
    (p.child != null && isTokenBased(p.child))
  )
}

function removeEmptyChannelInfoFields(channelInfo: string): string {
  if (!channelInfo) return channelInfo

  const parsed = parseJsonObject(channelInfo)
  const fieldsToRemove = new Set([
    'queueName',
    'sessionId',
    'messageEndpointUrl',
    'encodingType',
    'defaultMaxToken',
    'anthropicVersion',
    'deployName',
    'auth',
    'accessKey',
    'appId',
  ])

  const filtered = Object.fromEntries(
    Object.entries(parsed).filter(([key, value]) => {
      if (!fieldsToRemove.has(key)) return true
      return !isEmptyChannelInfoValue(value)
    })
  )

  return JSON.stringify(filtered)
}

function isEmptyChannelInfoValue(value: unknown): boolean {
  if (value == null || value === 0) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0 || value.every(isEmptyChannelInfoValue)
  if (typeof value === 'object') return Object.values(value).every(isEmptyChannelInfoValue)
  return false
}


interface ChannelConfigDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mode: 'create' | 'edit'
  initialData?: Partial<ChannelDetails>
  modelName?: string
  endpoint?: string
  onSuccess?: () => void
  isPrivate?: boolean
}

// 表单数据类型
interface ChannelFormData {
  url: string
  protocol: string
  supplier: string
  dataDestination: string
  priceInfo: string
  priority: string
  channelInfo: string
  trialEnabled: number
  billingEnabled: number
  queueMode: number
  queueName: string
  akCodes: string[]
}

interface CapacityFormData {
  rpm: string
  tpm: string
  parallelLimit: string
  qps: string
}

const emptyCapacity: CapacityFormData = {
  rpm: '',
  tpm: '',
  parallelLimit: '',
  qps: '',
}

function parseJsonObject(value: string): Record<string, any> {
  if (!value) return {}
  let parsed: unknown
  try {
    parsed = JSON.parse(value)
  } catch {
    throw new Error('协议配置信息必须是合法 JSON 对象')
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('协议配置信息必须是 JSON 对象')
  }
  return parsed
}

function parseJsonObjectOrEmpty(value: string): Record<string, any> {
  try {
    return parseJsonObject(value)
  } catch {
    return {}
  }
}

function capacityFromChannelInfo(channelInfo?: string): CapacityFormData {
  const parsed = parseJsonObjectOrEmpty(channelInfo || '')
  const capacity = parsed.capacity && typeof parsed.capacity === 'object' ? parsed.capacity : {}
  const parallelLimit = capacity.parallelLimit ?? capacity.concurrentLimit ?? ''
  return {
    rpm: capacity.rpm == null ? '' : String(capacity.rpm),
    tpm: capacity.tpm == null ? '' : String(capacity.tpm),
    parallelLimit: parallelLimit == null ? '' : String(parallelLimit),
    qps: capacity.qps == null ? '' : String(capacity.qps),
  }
}

function parseCapacityInteger(value: string, label: string): number {
  const trimmed = value.trim()
  if (!trimmed) return 0
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${label}必须是非负整数`)
  }
  return parsed
}

type CapacityType = 'llm' | 'realtime' | 'qps'

function capacityTypeForEndpoint(endpoint?: string): CapacityType {
  if (endpoint === '/v1/chat/completions' || endpoint === '/v1/responses' || endpoint === '/v1/messages') {
    return 'llm'
  }
  if (endpoint === '/v1/audio/asr/stream' || endpoint === '/v1/audio/tts/stream' || endpoint === '/v1/audio/realtime'
    || endpoint === '/v1/live/sessions') {
    return 'realtime'
  }
  return 'qps'
}

function isVideoEndpoint(endpoint?: string): boolean {
  return endpoint === '/v1/videos'
}

function isWebSearchEndpoint(endpoint?: string): boolean {
  return endpoint === '/v1/web/search'
}

function normalizeWebSearchPriceValues(values: Record<string, any>): Record<string, any> {
  const billingMode = values.billingMode === 'search_type'
    || (!values.billingMode && values.requestPrice == null
      && (values.basicSearchPrice != null || values.advancedSearchPrice != null))
    ? 'search_type'
    : 'request'
  return { ...values, billingMode }
}

function cleanWebSearchPriceValues(values: Record<string, any>): Record<string, any> {
  const cleaned = normalizeWebSearchPriceValues(values)
  if (cleaned.billingMode === 'request') {
    delete cleaned.basicSearchPrice
    delete cleaned.advancedSearchPrice
  } else {
    delete cleaned.requestPrice
  }
  return cleaned
}

function shouldShowWebSearchPriceParam(paramCode: string, billingMode?: string): boolean {
  if (paramCode === 'billingMode') return true
  return (billingMode || 'request') === 'search_type'
    ? paramCode === 'basicSearchPrice' || paramCode === 'advancedSearchPrice'
    : paramCode === 'requestPrice'
}

function sortWebSearchPriceParams(params: JsonSchema['params']): JsonSchema['params'] {
  const order = new Map([
    ['billingMode', 0],
    ['requestPrice', 1],
    ['basicSearchPrice', 2],
    ['advancedSearchPrice', 3],
  ])
  return [...params].sort((a, b) => (order.get(a.code) ?? 99) - (order.get(b.code) ?? 99))
}

function isTtsEndpoint(endpoint?: string): boolean {
  return endpoint === '/v1/audio/speech' || endpoint === '/v1/audio/tts/stream'
}

function defaultPriceUnit(endpoint?: string): string {
  return isTtsEndpoint(endpoint) ? '分/万字' : '分/千token'
}

function isInputReplacementTextsField(paramCode: string): boolean {
  return paramCode === 'inputReplacementTexts'
}

function isInputReplacementEnabledField(paramCode: string): boolean {
  return paramCode === 'inputReplacementEnabled'
}

function validateInputReplacementTexts(value: unknown): string | null {
  if (value == null) return 'key 和 value 不能为空'
  if (typeof value !== 'object' || Array.isArray(value)) {
    return '输入块类型替换文本映射格式不正确'
  }
  const entries = Object.entries(value as Record<string, unknown>)
  if (entries.length === 0) {
    return 'key 和 value 不能为空'
  }
  for (const [key, rawValue] of entries) {
    if (!String(key).trim() || !String(rawValue ?? '').trim()) {
      return 'key 和 value 不能为空'
    }
  }
  return null
}

function getInputReplacementTextsValidationMessage(channelInfoValues: Record<string, any>): string | null {
  if (!channelInfoValues.inputReplacementEnabled) {
    return null
  }
  return validateInputReplacementTexts(channelInfoValues.inputReplacementTexts)
}

function getValidationDialogTitle(message: string | null): string {
  if (message === 'key 和 value 不能为空' || message === '输入块类型替换文本映射格式不正确') {
    return '输入块类型替换配置校验失败'
  }
  return '价格配置校验失败'
}

function shouldShowVideoPriceParam(paramCode: string, billingMode?: string): boolean {
  if (paramCode === 'billingMode') {
    return true
  }
  if (billingMode === 'duration') {
    return paramCode === 'details'
  }
  if (billingMode === 'minimax') {
    return paramCode === 'details' || paramCode === 'inputImagePricePerImage' || paramCode === 'inputAudioPricePerSecond' || paramCode === 'freeInputImageCount'
  }
  if (billingMode === 'gemini') {
    return paramCode === 'input' || paramCode === 'textOutput' || paramCode === 'videoOutput'
  }
  return paramCode === 'input' || paramCode === 'output'
}

function normalizeVideoPriceValues(values: Record<string, any>): Record<string, any> {
  return {
    ...values,
    billingMode: values.billingMode || 'token',
  }
}

function cleanVideoPriceValues(values: Record<string, any>): Record<string, any> {
  const cleaned = normalizeVideoPriceValues(values)
  if (cleaned.billingMode === 'duration') {
    delete cleaned.input
    delete cleaned.output
    delete cleaned.textOutput
    delete cleaned.videoOutput
    delete cleaned.inputImagePricePerImage
    delete cleaned.inputAudioPricePerSecond
    delete cleaned.freeInputImageCount
  } else if (cleaned.billingMode === 'minimax') {
    delete cleaned.input
    delete cleaned.output
    delete cleaned.textOutput
    delete cleaned.videoOutput
  } else if (cleaned.billingMode === 'gemini') {
    delete cleaned.output
    delete cleaned.details
    delete cleaned.inputImagePricePerImage
    delete cleaned.inputAudioPricePerSecond
    delete cleaned.freeInputImageCount
  } else {
    delete cleaned.details
    delete cleaned.textOutput
    delete cleaned.videoOutput
    delete cleaned.inputImagePricePerImage
    delete cleaned.inputAudioPricePerSecond
    delete cleaned.freeInputImageCount
  }
  return cleaned
}

function validateVideoPriceValues(values: Record<string, any>): string | null {
  const billingMode = values.billingMode || 'token'
  if (billingMode !== 'duration' && billingMode !== 'minimax') {
    return null
  }
  if (!Array.isArray(values.details) || values.details.length === 0) {
    return '视频按秒计费明细不能为空'
  }
  const invalidIndex = values.details.findIndex((detail: any) => {
    const resolution = String(detail?.resolution || '').trim()
    return !/^\d+(P|K)$/.test(resolution)
  })
  if (invalidIndex >= 0) {
    return `第 ${invalidIndex + 1} 条视频计费明细的分辨率需使用大写 P/K 后缀，例如 720P、768P、2K`
  }
  return null
}

function convertVideoPriceSchemaLabels(params: JsonSchema['params']): JsonSchema['params'] {
  return params.map((param) => {
    if (param.code === 'details') {
      return {
        ...param,
        name: '按秒计费明细',
        child: {
          params: (param.child?.params?.length ? param.child.params : [
            { code: 'resolution', name: '分辨率档位', valueType: 'string', selections: [] },
            { code: 'audio', name: '是否有声视频', valueType: 'bool', selections: [] },
            { code: 'pricePerSecond', name: '视频单价（元/秒）', valueType: 'number', selections: [] },
          ]).map((childParam) => childParam.code === 'pricePerSecond'
            ? { ...childParam, name: '视频单价（元/秒）' }
            : childParam),
        },
      }
    }
    return param
  })
}

function convertPriceSchemaLabels(params: JsonSchema['params'], endpoint?: string): JsonSchema['params'] {
  const converted = convertSchemaLabels(params)
  return isVideoEndpoint(endpoint) ? convertVideoPriceSchemaLabels(converted) : converted
}

function sortVideoPriceParams(params: JsonSchema['params']): JsonSchema['params'] {
  const order = new Map([
    ['billingMode', 0],
    ['input', 1],
    ['output', 2],
    ['textOutput', 3],
    ['videoOutput', 4],
    ['details', 5],
    ['inputImagePricePerImage', 6],
    ['inputAudioPricePerSecond', 7],
    ['freeInputImageCount', 8],
  ])
  return [...params].sort((a, b) => (order.get(a.code) ?? 99) - (order.get(b.code) ?? 99))
}

function isCapacityConfigured(capacityValues: CapacityFormData, capacityType: CapacityType): boolean {
  if (capacityType === 'llm') {
    return capacityValues.rpm.trim() !== '' || capacityValues.tpm.trim() !== ''
  }
  if (capacityType === 'realtime') {
    return capacityValues.parallelLimit.trim() !== ''
  }
  return capacityValues.qps.trim() !== ''
}

function mergeCapacityIntoChannelInfo(channelInfo: string, capacityValues: CapacityFormData, capacityType: CapacityType): string {
  const parsed = parseJsonObject(channelInfo)
  if (!isCapacityConfigured(capacityValues, capacityType)) {
    delete parsed.capacity
    return JSON.stringify(parsed)
  }

  if (capacityType === 'llm') {
    parsed.capacity = {
      type: capacityType,
      rpm: parseCapacityInteger(capacityValues.rpm, 'RPM'),
      tpm: parseCapacityInteger(capacityValues.tpm, 'TPM'),
    }
  } else if (capacityType === 'realtime') {
    parsed.capacity = {
      type: capacityType,
      parallelLimit: parseCapacityInteger(capacityValues.parallelLimit, '并行数量上限'),
    }
  } else {
    parsed.capacity = {
      type: capacityType,
      qps: parseCapacityInteger(capacityValues.qps, 'QPS'),
    }
  }
  return JSON.stringify(parsed)
}

// 队列模式选项
const queueModeOptions = [
  { value: '0', label: 'None无队列' },
  { value: '1', label: 'Pull模式' },
  { value: '2', label: 'Route模式' },
  { value: '3', label: 'Pull+Route模式' },
]
// 添加数据流向数据来源
const dataDestinationOptions = [
  { value: 'protected', label: '内部已备案' },
  { value: 'inner', label: '内部' },
  { value: 'mainland', label: '国内' },
  { value: 'overseas', label: '海外' },
]

/**
 * 渠道配置弹窗组件
 * 用于新增或编辑模型的渠道配置
 */
export function ChannelConfigDialog({
  open,
  onOpenChange,
  mode,
  initialData,
  modelName,
  endpoint,
  onSuccess,
  isPrivate = false,
}: ChannelConfigDialogProps) {
  // 表单数据状态
  const [formData, setFormData] = useState<ChannelFormData>({
    url: '',
    protocol: '',
    supplier: '',
    dataDestination: '',
    priceInfo: '',
    priority: '',
    channelInfo: '',
    trialEnabled: 1,
    billingEnabled: isPrivate ? 0 : 1,
    queueMode: 0,
    queueName: '',
    akCodes: [],
  })

  // 验证错误状态
  const [errors, setErrors] = useState<Partial<Record<keyof ChannelFormData, string>>>({})

  // 提交加载状态
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 协议列表相关状态
  const [protocols, setProtocols] = useState<Array<{ value: string; label: string }>>([])
  const [protocolsLoading, setProtocolsLoading] = useState(false)
  const [protocolsError, setProtocolsError] = useState<string | null>(null)

  // 价格信息 Schema 相关状态
  const [priceInfoSchema, setPriceInfoSchema] = useState<JsonSchema>({ params: [] })
  const [priceInfoLoading, setPriceInfoLoading] = useState(false)
  const [priceInfoError, setPriceInfoError] = useState<string | null>(null)

  // 价格信息动态字段值
  const [priceInfoValues, setPriceInfoValues] = useState<Record<string, any>>({})

  // 当前模型价格是否为 token 计费，控制 ×10/÷10 换算和警告横幅
  const [isPriceTokenBased, setIsPriceTokenBased] = useState(false)

  // 渠道信息 Schema 相关状态
  const [channelInfoSchema, setChannelInfoSchema] = useState<JsonSchema>({ params: [] })
  const [channelInfoLoading, setChannelInfoLoading] = useState(false)
  const [channelInfoError, setChannelInfoError] = useState<string | null>(null)
  const [validationDialogMessage, setValidationDialogMessage] = useState<string | null>(null)

  // 渠道信息动态字段值
  const [channelInfoValues, setChannelInfoValues] = useState<Record<string, any>>({})
  const channelInfoRequestIdRef = useRef(0)
  const [capacityValues, setCapacityValues] = useState<CapacityFormData>(emptyCapacity)

  /**
   * 重置表单
   */
  const resetForm = () => {
    setFormData({
      url: '',
      protocol: '',
      supplier: '',
      dataDestination: '',
      priceInfo: '',
      priority: '',
      channelInfo: '',
      trialEnabled: 1,
      billingEnabled: isPrivate ? 0 : 1,
      queueMode: 0,
      queueName: '',
      akCodes: [],
    })
    setChannelInfoValues({})
    setChannelInfoSchema({ params: [] })
    setChannelInfoError(null)
    setCapacityValues(emptyCapacity)
    setErrors({})
  }

  /**
   * 初始化表单数据和协议列表
   * 混合加载策略:
   * - 新增模式: 先加载协议列表,再允许填写表单
   * - 编辑模式: 立即回填表单数据,后台加载协议列表(仅用于显示友好名称)
   */
  useEffect(() => {
    const channelInfoRequestId = ++channelInfoRequestIdRef.current
    if (!open) return

    // 新增模式: 必须先加载协议列表
    if (mode === 'create') {
      resetForm()
      setProtocolsError(null)

      const fetchProtocols = async () => {
        if (!modelName) {
          setProtocolsError('缺少必要参数: modelName')
          return
        }

        setProtocolsLoading(true)
        setPriceInfoLoading(true)
        try {
          // 并行获取协议列表和价格信息 Schema
          const [protocolsData, priceSchema] = await Promise.all([
            listProtocols('model', modelName),
            getPriceInfoSchema('model', modelName)
          ])
          // 设置协议列表
          const protocolOptions = Object.entries(protocolsData).map(([value, label]) => ({
            value,
            label: label as string,
          }))
          setProtocols(protocolOptions)
          setProtocolsError(null)
          const tokenBased = isTokenBased(priceSchema)
          setIsPriceTokenBased(tokenBased)
          setPriceInfoSchema({ ...priceSchema, params: convertPriceSchemaLabels(priceSchema.params, endpoint) })
          setPriceInfoError(null)

          // 初始化价格信息字段默认值
          const initialPriceValues: Record<string, any> = {}
          priceSchema.params.forEach((param) => {
            if (param.code === 'supplierDiscount') {
              initialPriceValues[param.code] = 1
            } else if (param.valueType === 'number') {
              initialPriceValues[param.code] = 0
            } else if (param.valueType === 'string') {
              initialPriceValues[param.code] = param.code === 'unit' ? defaultPriceUnit(endpoint) : ''
            }
          })
          const displayPriceValues = isVideoEndpoint(endpoint)
            ? normalizeVideoPriceValues(initialPriceValues)
            : isWebSearchEndpoint(endpoint)
              ? normalizeWebSearchPriceValues(initialPriceValues)
              : initialPriceValues
          setPriceInfoValues(displayPriceValues)

          // 将初始值序列化为 JSON 字符串存入表单
          setFormData((prev) => ({
            ...prev,
            priceInfo: JSON.stringify(displayPriceValues, null, 2)
          }))
        } catch (error) {
          console.error('获取协议列表或价格信息失败:', error)
          const errorMessage = error instanceof Error ? error.message : '获取数据失败'
          setProtocolsError(errorMessage)
          setPriceInfoError(errorMessage)
          setProtocols([])
        } finally {
          setProtocolsLoading(false)
          setPriceInfoLoading(false)
        }
      }

      fetchProtocols()
    }
    // 编辑模式: 立即回填表单,后台加载协议列表
    else if (mode === 'edit' && initialData) {
      // 切换编辑对象时立即重建动态字段，避免渠道间状态污染
      setPriceInfoValues({})
      setChannelInfoValues(parseJsonObjectOrEmpty(initialData.channelInfo || ''))
      setChannelInfoSchema({ params: [] })
      setChannelInfoError(null)
      // 1. 立即回填表单数据
      setFormData({
        url: initialData.url || '',
        protocol: (initialData.protocol || '').trim(),
        supplier: initialData.supplier || '',
        dataDestination: initialData.dataDestination || '',
        priceInfo: initialData.priceInfo || '',
        priority: initialData.priority || '',
        channelInfo: initialData.channelInfo || '',
        trialEnabled: initialData.trialEnabled || 0,
        billingEnabled: initialData.billingEnabled ?? 1,
        queueMode: initialData.queueMode || 0,
        queueName: initialData.queueName || '',
        akCodes: initialData.akCodes || [],
      })
      setCapacityValues(capacityFromChannelInfo(initialData.channelInfo))

      // 2. 后台静默加载协议列表和价格信息 schema (用于显示友好名称和动态表单)
      if (modelName) {
        // 加载协议列表
        listProtocols('model', modelName)
          .then((data) => {
            const protocolOptions = Object.entries(data).map(([value, label]) => ({
              value,
              label: label as string,
            }))
            setProtocols(protocolOptions)
          })
          .catch((error) => {
            console.error('获取协议列表失败(编辑模式,非阻塞):', error)
            // 编辑模式下协议字段禁用,加载失败不影响功能
          })

        // 加载价格信息 schema
        getPriceInfoSchema('model', modelName)
          .then((schema) => {
            try {
              const parsedValues = initialData.priceInfo ? JSON.parse(initialData.priceInfo) : null
              const tokenBased = isTokenBased(schema, parsedValues?.unit)
              setIsPriceTokenBased(tokenBased)
              setPriceInfoSchema({ ...schema, params: convertPriceSchemaLabels(schema.params, endpoint) })
              if (parsedValues) {
                const displayValues = isVideoEndpoint(endpoint)
                  ? normalizeVideoPriceValues(convertVideoDurationPriceObj(parsedValues, 'load'))
                  : isWebSearchEndpoint(endpoint)
                    ? normalizeWebSearchPriceValues(parsedValues)
                  : isTtsEndpoint(endpoint)
                    ? convertTtsPriceObj(parsedValues, 'load')
                    : convertSpeakerHourlyPriceObj(parsedValues, 'load', endpoint)
                setPriceInfoValues(displayValues)
                setFormData((prev) => ({
                  ...prev,
                  priceInfo: JSON.stringify(displayValues, null, 2),
                }))
              }
            } catch (error) {
              console.error('解析价格信息失败:', error)
            }
          })
          .catch((error) => {
            console.error('获取价格信息 schema 失败(编辑模式,非阻塞):', error)
          })

        // 加载渠道信息 schema (编辑模式下也需要加载以显示动态表单)
        const protocol = (initialData.protocol || '').trim()
        if (protocol) {
          setChannelInfoLoading(true)
          getChannelInfoSchema('model', modelName, protocol)
            .then((schema) => {
              if (channelInfoRequestId !== channelInfoRequestIdRef.current) return
              setChannelInfoSchema({ ...schema, params: convertSchemaLabels(schema.params) })
            })
            .catch((error) => {
              if (channelInfoRequestId !== channelInfoRequestIdRef.current) return
              console.error('获取渠道信息 schema 失败(编辑模式,非阻塞):', error)
              setChannelInfoError(error instanceof Error ? error.message : '获取渠道信息配置失败')
            })
            .finally(() => {
              if (channelInfoRequestId === channelInfoRequestIdRef.current) {
                setChannelInfoLoading(false)
              }
            })
        } else {
          setChannelInfoLoading(false)
          setChannelInfoSchema({ params: [] })
          setChannelInfoError(null)
        }
      }
    }
  }, [open, mode, initialData, modelName, endpoint])

  /**
   * 重试加载协议列表
   */
  const retryLoadProtocols = async () => {
    if (!modelName) return

    setProtocolsError(null)
    setProtocolsLoading(true)
    try {
      const data = await listProtocols('model', modelName)
      const protocolOptions = Object.entries(data).map(([value, label]) => ({
        value,
        label: label as string,
      }))
      setProtocols(protocolOptions)
    } catch (error) {
      console.error('获取协议列表失败:', error)
      setProtocolsError(error instanceof Error ? error.message : '获取协议列表失败')
    } finally {
      setProtocolsLoading(false)
    }
  }

  /**
   * 监听协议变化,加载对应的 channelInfo Schema
   */
  useEffect(() => {
    // 只在新增模式且选择了协议后加载
    const protocol = formData.protocol.trim()
    if (mode !== 'create' || !protocol || !modelName) {
      return
    }

    const channelInfoRequestId = ++channelInfoRequestIdRef.current
    const fetchChannelInfoSchema = async () => {
      setChannelInfoLoading(true)
      setChannelInfoError(null)
      try {
        const schema = await getChannelInfoSchema('model', modelName, protocol)
        if (channelInfoRequestId !== channelInfoRequestIdRef.current) return
        setChannelInfoSchema({ ...schema, params: convertSchemaLabels(schema.params) })

        // 初始化渠道信息字段默认值
        const initialChannelValues: Record<string, any> = {}
        schema.params.forEach((param) => {
          if (param.valueType === 'number') {
            initialChannelValues[param.code] = 0
          } else if (param.valueType === 'string') {
            initialChannelValues[param.code] = ''
          }
        })
        setChannelInfoValues(initialChannelValues)

        // 将初始值序列化为 JSON 字符串存入表单
        setFormData((prev) => ({
          ...prev,
          channelInfo: JSON.stringify(initialChannelValues, null, 2)
        }))
      } catch (error) {
        if (channelInfoRequestId !== channelInfoRequestIdRef.current) return
        console.error('获取渠道信息 Schema 失败:', error)
        setChannelInfoError(error instanceof Error ? error.message : '获取渠道信息配置失败')
        setChannelInfoSchema({ params: [] })
      } finally {
        if (channelInfoRequestId === channelInfoRequestIdRef.current) {
          setChannelInfoLoading(false)
        }
      }
    }

    fetchChannelInfoSchema()
  }, [formData.protocol, modelName, mode])

  /**
   * 处理输入框变化
   */
  const handleInputChange = (field: keyof ChannelFormData) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const value = e.target.value
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }))
    // 清除该字段的错误
    if (errors[field]) {
      setErrors((prev) => ({
        ...prev,
        [field]: undefined,
      }))
    }
  }

  const addAkCodeInput = () => {
    setFormData((prev) => ({ ...prev, akCodes: [...prev.akCodes, ''] }))
  }

  const updateAkCode = (index: number, value: string) => {
    setFormData((prev) => ({
      ...prev,
      akCodes: prev.akCodes.map((akCode, currentIndex) =>
        currentIndex === index ? value : akCode
      ),
    }))
  }

  const removeAkCode = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      akCodes: prev.akCodes.filter((_, currentIndex) => currentIndex !== index),
    }))
  }

  /**
   * 处理下拉选择变化
   */
  const handleSelectChange = (field: keyof ChannelFormData) => (value: string) => {
    setFormData((prev) => ({
      ...prev,
      [field]: field === 'queueMode' ? Number(value) : value,
    }))
    // 清除该字段的错误
    if (errors[field]) {
      setErrors((prev) => ({
        ...prev,
        [field]: undefined,
      }))
    }
  }

  /**
   * 处理价格信息字段变化
   */
  const handlePriceInfoFieldChange = (fieldCode: string, value: any) => {
    const updatedValues: Record<string, any> = {
      ...priceInfoValues,
      [fieldCode]: value,
    }
    if (isWebSearchEndpoint(endpoint) && fieldCode === 'billingMode') {
      updatedValues.billingMode = value || 'request'
      if (updatedValues.billingMode === 'request') {
        delete updatedValues.basicSearchPrice
        delete updatedValues.advancedSearchPrice
      } else {
        delete updatedValues.requestPrice
      }
    }
    setPriceInfoValues(updatedValues)

    // 更新表单的 priceInfo 字段为 JSON 字符串
    setFormData((prev) => ({
      ...prev,
      priceInfo: JSON.stringify(updatedValues, null, 2)
    }))

    // 清除价格信息字段的错误
    if (errors.priceInfo) {
      setErrors((prev) => ({
        ...prev,
        priceInfo: undefined,
      }))
    }
  }

  /**
   * 处理渠道信息字段变化
   */
  const handleChannelInfoFieldChange = (fieldCode: string, value: any) => {
    const updatedValues = {
      ...channelInfoValues,
      [fieldCode]: value,
    }
    setChannelInfoValues(updatedValues)

    // 更新表单的 channelInfo 字段为 JSON 字符串
    setFormData((prev) => ({
      ...prev,
      channelInfo: JSON.stringify(updatedValues, null, 2)
    }))

    // 清除渠道信息字段的错误
    if (errors.channelInfo) {
      setErrors((prev) => ({
        ...prev,
        channelInfo: undefined,
      }))
    }
  }

  const handleInputReplacementEnabledChange = (enabled: boolean) => {
    const updatedValues: Record<string, any> = {
      ...channelInfoValues,
      inputReplacementEnabled: enabled,
    }
    if (!enabled) {
      delete updatedValues.inputReplacementTexts
    }
    setChannelInfoValues(updatedValues)
    setFormData((prev) => ({
      ...prev,
      channelInfo: JSON.stringify(updatedValues, null, 2),
    }))
    if (errors.channelInfo) {
      setErrors((prev) => ({
        ...prev,
        channelInfo: undefined,
      }))
    }
  }

  const handleCapacityFieldChange = (field: keyof CapacityFormData) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const value = e.target.value
    setCapacityValues((prev) => ({
      ...prev,
      [field]: value,
    }))
    if (errors.channelInfo) {
      setErrors((prev) => ({
        ...prev,
        channelInfo: undefined,
      }))
    }
  }

  /**
   * 验证 URL 格式
   */
  const isValidUrl = (url: string): boolean => {
    try {
      new URL(url)
      return true
    } catch {
      return false
    }
  }

  /**
   * 验证表单
   */
  const validateForm = (): boolean => {
    const newErrors: typeof errors = {}

    // URL验证 (仅新增时)
    if (mode === 'create') {
      if (!formData.url) {
        newErrors.url = '渠道转发URL不能为空'
      } else if (!isValidUrl(formData.url)) {
        newErrors.url = 'URL格式不正确'
      }

      // 协议验证 (仅新增时)
      if (!formData.protocol) {
        newErrors.protocol = '协议不能为空'
      }

      // 供应商验证 (仅新增时)
      if (!formData.supplier) {
        newErrors.supplier = '供应商不能为空'
      }
    }

    // 价格验证 (始终)
    if (!formData.priceInfo) {
      newErrors.priceInfo = '价格信息不能为空'
    } else {
      try {
        const priceData = JSON.parse(formData.priceInfo)
        if (isVideoEndpoint(endpoint)) {
          const priceError = validateVideoPriceValues(priceData)
          if (priceError) {
            newErrors.priceInfo = priceError
          }
        }
      } catch (error) {
        newErrors.priceInfo = '价格信息格式不正确'
      }
    }

    // 队列名称验证 (条件必填)
    if (formData.queueMode !== 0 && !formData.queueName) {
      newErrors.queueName = '队列名称不能为空'
    }

    try {
      parseJsonObject(formData.channelInfo || '{}')
    } catch (error) {
      newErrors.channelInfo = error instanceof Error ? error.message : '协议配置信息必须是 JSON 对象'
    }

    const inputReplacementTextsError = getInputReplacementTextsValidationMessage(channelInfoValues)
    if (inputReplacementTextsError) {
      newErrors.channelInfo = inputReplacementTextsError
    }

    try {
      const capacityType = capacityTypeForEndpoint(endpoint)
      if (capacityType === 'llm') {
        parseCapacityInteger(capacityValues.rpm, 'RPM')
        parseCapacityInteger(capacityValues.tpm, 'TPM')
      } else if (capacityType === 'realtime') {
        parseCapacityInteger(capacityValues.parallelLimit, '并行数量上限')
      } else {
        parseCapacityInteger(capacityValues.qps, 'QPS')
      }
    } catch (error) {
      if (!newErrors.channelInfo) {
        newErrors.channelInfo = error instanceof Error ? error.message : '容量配置格式不正确'
      }
    }

    setErrors(newErrors)
    setValidationDialogMessage(newErrors.priceInfo || newErrors.channelInfo || null)
    return Object.keys(newErrors).length === 0
  }

  /**
   * 处理提交操作
   * 职责: 验证表单、添加 unit 字段到 priceInfo、调用创建/更新接口
   */
  const handleSubmit = async () => {
    // 1. 验证表单
    if (!validateForm()) {
      return
    }

    // 2. 在提交前处理 priceInfo: 添加 unit 字段并删除所有 nanoid 生成的 id 字段
    let finalPriceInfo = formData.priceInfo
    try {
      const parsed = JSON.parse(formData.priceInfo)
      const priceData = isVideoEndpoint(endpoint)
        ? convertVideoDurationPriceObj(cleanVideoPriceValues(parsed), 'save')
        : isWebSearchEndpoint(endpoint)
          ? cleanWebSearchPriceValues(parsed)
        : isTtsEndpoint(endpoint)
          ? convertTtsPriceObj(parsed, 'save')
          : convertSpeakerHourlyPriceObj(parsed, 'save', endpoint)
      if (isPriceTokenBased && priceInfoSchema.params.some(p =>
        p.code === 'tiers' || shouldConvertTokenPrice(undefined, p.name)
      )) {
        priceData.unit = defaultPriceUnit(endpoint)
      }

      // 删除根对象的 id
      delete priceData.id

      // 删除 tiers 中的所有 id 字段
      if (Array.isArray(priceData.tiers)) {
        priceData.tiers = priceData.tiers.map((tier: any) => {
          const { id, ...tierWithoutId } = tier

          // 删除 inputRangePrice 的 id
          if (tierWithoutId.inputRangePrice) {
            const { id: inputId, ...inputRangePrice } = tierWithoutId.inputRangePrice
            tierWithoutId.inputRangePrice = inputRangePrice
          }

          // 处理 outputRangePrices: 删除每个元素的 id, 如果为空数组则删除该字段
          if (Array.isArray(tierWithoutId.outputRangePrices)) {
            if (tierWithoutId.outputRangePrices.length === 0) {
              // 如果是空数组,删除该字段
              delete tierWithoutId.outputRangePrices
            } else {
              // 如果不为空,删除每个元素的 id
              tierWithoutId.outputRangePrices = tierWithoutId.outputRangePrices.map((output: any) => {
                const { id: outputId, ...outputWithoutId } = output
                return outputWithoutId
              })
            }
          }

          return tierWithoutId
        })
      }

      finalPriceInfo = JSON.stringify(priceData)
    } catch (error) {
      console.error('解析价格信息失败:', error)
      // 如果解析失败，使用原始值（验证阶段应该已经捕获了这个错误）
    }

    // 3. 压缩 channelInfo 为单行 JSON，避免 pretty-print 换行存入数据库
    let finalChannelInfo = formData.channelInfo
    try {
      if (finalChannelInfo) {
        finalChannelInfo = removeEmptyChannelInfoFields(finalChannelInfo)
      }
      finalChannelInfo = mergeCapacityIntoChannelInfo(finalChannelInfo || '{}', capacityValues, capacityTypeForEndpoint(endpoint))
      if (!showInputReplacementTexts) {
        const parsed = parseJsonObject(finalChannelInfo)
        delete parsed.inputReplacementTexts
        finalChannelInfo = JSON.stringify(parsed)
      }
    } catch (error) {
      setErrors((prev) => ({
        ...prev,
        channelInfo: error instanceof Error ? error.message : '容量配置格式不正确',
      }))
      return
    }

    // 4. 设置加载状态
    setIsSubmitting(true)
    try {
      if (mode === 'create') {
        const channelData = {
          entityType: 'model',
          entityCode: modelName,
          url: formData.url,
          akCodes: formData.akCodes,
          protocol: formData.protocol,
          supplier: formData.supplier,
          dataDestination: formData.dataDestination,
          priceInfo: finalPriceInfo,
          priority: formData.priority,
          channelInfo: finalChannelInfo,
          trialEnabled: formData.trialEnabled,
          billingEnabled: formData.billingEnabled,
          queueMode: formData.queueMode,
          queueName: formData.queueName || undefined,
        }
        if (isPrivate) {
          await createPrivateChannel(channelData)
        } else {
          await createChannel(channelData)
        }
      } else {
        // 调用更新接口 - 只传递允许编辑的字段
        if (!initialData?.channelCode) {
          throw new Error('缺少渠道编码')
        }
        const updateData = {
          priceInfo: finalPriceInfo,
          priority: formData.priority,
          channelInfo: finalChannelInfo,
          trialEnabled: formData.trialEnabled,
          billingEnabled: formData.billingEnabled,
          queueMode: formData.queueMode,
          queueName: formData.queueName || undefined,
          akCodes: formData.akCodes,
        }
        if (isPrivate) {
          await updatePrivateChannel(initialData.channelCode, updateData)
        } else {
          await updateChannel(initialData.channelCode, updateData)
        }
      }

      // 4. 成功处理
      onSuccess?.()
      onOpenChange(false)
    } catch (error) {
      console.error('操作失败:', error)
      // TODO: 可以添加用户友好的错误提示
      alert(error instanceof Error ? error.message : '操作失败，请重试')
      // 保持弹窗打开,让用户修正
    } finally {
      // 5. 恢复按钮状态
      setIsSubmitting(false)
    }
  }

  /**
   * 处理取消操作
   */
  const handleCancel = () => {
    onOpenChange(false)
  }
  const supplierDiscountParam = priceInfoSchema.params.find((param) => param.code === 'supplierDiscount')
  const priceInfoParams = priceInfoSchema.params.filter((param) => {
    if (param.code === 'supplierDiscount') {
      return false
    }
    if (isVideoEndpoint(endpoint)) {
      return shouldShowVideoPriceParam(param.code, priceInfoValues.billingMode)
    }
    if (isWebSearchEndpoint(endpoint)) {
      return shouldShowWebSearchPriceParam(param.code, priceInfoValues.billingMode)
    }
    return true
  })
  const displayPriceInfoParams = isVideoEndpoint(endpoint)
    ? sortVideoPriceParams(priceInfoParams)
    : isWebSearchEndpoint(endpoint)
      ? sortWebSearchPriceParams(priceInfoParams)
      : priceInfoParams
  const capacityType = capacityTypeForEndpoint(endpoint)
  const showInputReplacementTexts = Boolean(channelInfoValues.inputReplacementEnabled)

  return (
    <>
    <Dialog
      open={open}
      onOpenChange={(isOpen) => {
        if (!isOpen) {
          onOpenChange(false)
        }
      }}
    >
      <DialogContent
        className="max-w-2xl max-h-[90vh] flex flex-col"
        onInteractOutside={(e) => {
          // 阻止点击遮罩层关闭弹窗
          e.preventDefault()
        }}
        onEscapeKeyDown={(e) => {
          // 阻止按 ESC 键关闭弹窗
          e.preventDefault()
        }}
      >
        <DialogHeader>
          <DialogTitle>{mode === 'create' ? '新增渠道' : '编辑渠道'}</DialogTitle>
          <DialogDescription>
            {mode === 'create' ? '填写渠道的基本信息以创建新的渠道配置' : '修改渠道的配置信息'}
          </DialogDescription>
        </DialogHeader>

        {/* 表单内容区域 */}
        <div className="flex-1 overflow-y-auto space-y-6 p-4">
          {/* 协议加载失败提示 - 仅新增模式显示 */}
          {mode === 'create' && protocolsError && (
            <div className="rounded-lg border border-red-500/20 bg-red-500/10 p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm text-red-500">{protocolsError}</p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={retryLoadProtocols}
                  disabled={protocolsLoading}
                  className="ml-4 flex-shrink-0"
                >
                  {protocolsLoading ? '加载中...' : '重试'}
                </Button>
              </div>
            </div>
          )}

          {/* 是否支持试用 */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="trialEnabled" className="text-sm font-normal">
                是否支持试用
              </Label>
              <p className="text-xs text-muted-foreground">
                开启后,该渠道将支持试用功能
              </p>
            </div>
            <Switch
              id="trialEnabled"
              checked={formData.trialEnabled === 1}
              onCheckedChange={(checked) =>
                setFormData((prev) => ({ ...prev, trialEnabled: checked ? 1 : 0 }))
              }
            />
          </div>

          {/* 是否计费 - 仅私有通道显示 */}
          {isPrivate && (
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label htmlFor="billingEnabled" className="text-sm font-normal">
                  是否计费
                </Label>
                <p className="text-xs text-muted-foreground">
                  开启后,该渠道的调用将产生计费
                </p>
              </div>
              <Switch
                id="billingEnabled"
                checked={formData.billingEnabled === 1}
                onCheckedChange={(checked) =>
                  setFormData((prev) => ({ ...prev, billingEnabled: checked ? 1 : 0 }))
                }
              />
            </div>
          )}
          {/* ========== 基础信息 ========== */}
          <div className="space-y-4">
            <div className="space-y-3 rounded-lg border bg-muted/30 p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1">
                  <Label>专用 AK 编码</Label>
                  <TooltipProvider delayDuration={200}>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Info className="h-3.5 w-3.5 text-muted-foreground" />
                      </TooltipTrigger>
                      <TooltipContent side="top" align="start" className="max-w-[320px] text-xs">
                        <p>不配置时所有 AK 均可访问；配置父 AK 时，对父 AK 及其所有子 AK 生效；配置子 AK 时，仅对该子 AK 生效</p>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </div>
                <Button type="button" variant="outline" size="sm" onClick={addAkCodeInput}>
                  添加
                </Button>
              </div>
              {formData.akCodes.map((akCode, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Input
                    value={akCode}
                    placeholder="输入 AK 编码"
                    onChange={(event) => updateAkCode(index, event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => removeAkCode(index)}
                  >
                    删除
                  </Button>
                </div>
              ))}
            </div>
            {/* 数据流向 */}
            <div className="space-y-2">
              <Label htmlFor="dataDestination">数据流向</Label>
              <Select
                value={formData.dataDestination}
                onValueChange={handleSelectChange('dataDestination')}
                disabled={mode === 'edit'}
              >
                <SelectTrigger id="dataDestination">
                  <SelectValue placeholder="选择数据流向" />
                </SelectTrigger>
                <SelectContent>
                  {dataDestinationOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                描述数据流向, 如: 海外表示数据流向海外
              </p>
            </div>
             {/* 优先级 */}
             <div className="space-y-2">
              <Label htmlFor="priority">优先级</Label>
              <Select
                value={formData.priority}
                onValueChange={handleSelectChange('priority')}
              >
                <SelectTrigger id="priority">
                  <SelectValue placeholder="选择优先级" />
                </SelectTrigger>
                <SelectContent>
                  {priorityOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
                     
            {/* 协议 */}
            <div className="space-y-2">
              <Label htmlFor="protocol">
                协议 <span className="text-red-600">*</span>
              </Label>
              <Select
                value={formData.protocol}
                onValueChange={handleSelectChange('protocol')}
                disabled={mode === 'edit' || protocolsLoading}
              >
                <SelectTrigger
                  id="protocol"
                  className={errors.protocol ? 'border-red-600' : ''}
                >
                  <SelectValue placeholder={
                    protocolsLoading ? '加载协议列表中...' :
                      protocolsError ? '加载失败' :
                        '选择协议'
                  } />
                </SelectTrigger>
                <SelectContent>
                  {protocols.length > 0 ? (
                    protocols.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))
                  ) : (
                    protocolsError && (
                      <div className="px-2 py-1.5 text-sm text-muted-foreground">
                        无可用协议
                      </div>
                    )
                  )}
                </SelectContent>
              </Select>
              {errors.protocol && (
                <p className="text-sm text-red-600">{errors.protocol}</p>
              )}
              {protocolsError && mode === 'create' && (
                <p className="text-sm text-red-600">{protocolsError}</p>
              )}
            </div>
            {/* 供应商 */}
            <div className="space-y-2">
              <Label htmlFor="supplier">
                供应商 <span className="text-red-600">*</span>
              </Label>
              <Input
                id="supplier"
                placeholder="OpenAI Official"
                value={formData.supplier}
                onChange={handleInputChange('supplier')}
                disabled={mode === 'edit'}
                className={errors.supplier ? 'border-red-600' : ''}
              />
              {errors.supplier && (
                <p className="text-sm text-red-600">{errors.supplier}</p>
              )}
            </div> 
            {/* 渠道转发URL */}
            <div className="space-y-2">
              <Label htmlFor="url">
                渠道转发URL <span className="text-red-600">*</span>
              </Label>
              <Input
                id="url"
                placeholder="https://api.example.com/v1/chat/completions"
                value={formData.url}
                onChange={handleInputChange('url')}
                disabled={mode === 'edit'}
                className={errors.url ? 'border-red-600' : ''}
              />
              {errors.url && (
                <p className="text-sm text-red-600">{errors.url}</p>
              )}
            </div>
                   
            {/* 队列模式 */}
            <div className="space-y-2">
              <Label htmlFor="queueMode">队列模式</Label>
              <Select
                value={String(formData.queueMode)}
                onValueChange={handleSelectChange('queueMode')}
              >
                <SelectTrigger id="queueMode">
                  <SelectValue placeholder="选择队列模式" />
                </SelectTrigger>
                <SelectContent>
                  {queueModeOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
   
            {/* 容量配置 */}
            <div className="space-y-4">
              <div className="space-y-2">
                <Label className="text-sm">容量配置</Label>
                <p className="text-xs text-muted-foreground">
                  LLM 使用 RPM/TPM，实时语音使用并行数量上限，其他能力使用 QPS
                </p>
              </div>
              <div className="grid gap-4 rounded-lg border bg-muted/30 p-4 sm:grid-cols-2">
                {capacityType === 'llm' && (
                  <>
                    <div className="space-y-2">
                      <Label htmlFor="capacity-rpm">RPM</Label>
                      <Input
                        id="capacity-rpm"
                        inputMode="numeric"
                        placeholder="0"
                        value={capacityValues.rpm}
                        onChange={handleCapacityFieldChange('rpm')}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="capacity-tpm">TPM</Label>
                      <Input
                        id="capacity-tpm"
                        inputMode="numeric"
                        placeholder="0"
                        value={capacityValues.tpm}
                        onChange={handleCapacityFieldChange('tpm')}
                      />
                    </div>
                  </>
                )}
                {capacityType === 'realtime' && (
                  <div className="space-y-2">
                    <Label htmlFor="capacity-parallel">并行上限</Label>
                    <Input
                      id="capacity-parallel"
                      inputMode="numeric"
                      placeholder="0"
                      value={capacityValues.parallelLimit}
                      onChange={handleCapacityFieldChange('parallelLimit')}
                    />
                  </div>
                )}
                {capacityType === 'qps' && (
                  <div className="space-y-2">
                    <Label htmlFor="capacity-qps">QPS</Label>
                    <Input
                      id="capacity-qps"
                      inputMode="numeric"
                      placeholder="0"
                      value={capacityValues.qps}
                      onChange={handleCapacityFieldChange('qps')}
                    />
                  </div>
                )}
              </div>
              {errors.channelInfo && (
                <p className="text-sm text-red-600">{errors.channelInfo}</p>
              )}
            </div>


            {/* 协议配置信息 - 动态表单 */}
            <div className="space-y-4">
              <div className="space-y-2">
                <Label className="text-sm">协议配置信息</Label>
                <p className="text-xs text-muted-foreground">
                  根据所选协议配置不同的参数
                </p>
              </div>

              {/* 协议配置加载状态 */}
              {channelInfoLoading && (
                <div className="text-sm text-muted-foreground">加载协议配置中...</div>
              )}

              {/* 协议配置加载失败 */}
              {channelInfoError && !channelInfoLoading && (
                <div className="text-sm text-red-600">加载协议配置失败: {channelInfoError}</div>
              )}

              {/* 动态渲染协议配置字段 */}
              {!channelInfoLoading && !channelInfoError && channelInfoSchema.params.length > 0 && (
                <div className="space-y-4 p-4 border rounded-lg bg-muted/30">
                  {channelInfoSchema.params.map((param) => {
                    if (isInputReplacementTextsField(param.code)) {
                      return null
                    }
                    if (isInputReplacementEnabledField(param.code)) {
                      return (
                        <div key={param.code} className="space-y-2">
                          <div className="space-y-0.5">
                            <Label htmlFor={`channel-${param.code}`}>{param.name}</Label>
                            {param.description && (
                              <p className="text-xs text-muted-foreground">{param.description}</p>
                            )}
                          </div>
                          <div className="pt-1">
                            <FieldRenderer
                              mode={mode}
                              schema={param}
                              value={channelInfoValues[param.code]}
                              onChange={handleInputReplacementEnabledChange}
                              hideLabel
                              endpoint={endpoint}
                            />
                          </div>
                          {showInputReplacementTexts && (
                            <div className="pl-0">
                              {channelInfoSchema.params
                                .filter((item) => isInputReplacementTextsField(item.code))
                                .map((item) => (
                                  <FieldRenderer
                                    key={item.code}
                                    mode={mode}
                                    schema={item}
                                    value={channelInfoValues[item.code]}
                                    onChange={(value) => handleChannelInfoFieldChange(item.code, value)}
                                    hideLabel
                                    endpoint={endpoint}
                                  />
                                ))}
                            </div>
                          )}
                        </div>
                      )
                    }
                    return (
                      <div key={param.code} className="space-y-2">
                        <Label htmlFor={`channel-${param.code}`}>
                          {param.name}
                        </Label>
                        {param.description && (
                          <p className="text-xs text-muted-foreground">{param.description}</p>
                        )}
                        <FieldRenderer
                          mode={mode}
                          schema={param}
                          value={channelInfoValues[param.code]}
                          onChange={(value) => handleChannelInfoFieldChange(param.code, value)}
                          hideLabel
                          endpoint={endpoint}
                        />
                      </div>
                    )
                  })}
                </div>
              )}

              {/* 无协议选择时的提示 (仅新增模式) */}
              {!formData.protocol && mode === 'create' && (
                <div className="text-sm text-muted-foreground p-4 border rounded-lg bg-muted/30">
                  请先选择协议以加载配置选项
                </div>
              )}

              {/* 编辑模式下无 Schema 数据的降级显示 */}
              {mode === 'edit' && !channelInfoLoading && channelInfoSchema.params.length === 0 && formData.channelInfo && (
                <div className="space-y-2">
                  <Label htmlFor="channelInfo-fallback">协议配置信息 (JSON)</Label>
                  <Input
                    id="channelInfo-fallback"
                    value={formData.channelInfo}
                    onChange={handleInputChange('channelInfo')}
                    placeholder="{}"
                  />
                  <p className="text-xs text-muted-foreground">
                    无法加载动态表单,请直接编辑 JSON 格式配置
                  </p>
                </div>
              )}
            </div>


            {/* 队列名称 - 条件显示 */}
            {formData.queueMode !== 0 && (
              <div className="space-y-2">
                <Label htmlFor="queueName">
                  队列名称 <span className="text-red-600">*</span>
                </Label>
                <Input
                  id="queueName"
                  placeholder="输入队列名称"
                  value={formData.queueName}
                  onChange={handleInputChange('queueName')}
                  className={errors.queueName ? 'border-red-600' : ''}
                />
                {errors.queueName && (
                  <p className="text-sm text-red-600">{errors.queueName}</p>
                )}
              </div>
            )}
          </div>

          {/* ========== 价格信息配置 ========== */}
          <div className="space-y-4">
            <div className="space-y-2">
              <Label className="text-sm font-semibold">
                价格信息配置 <span className="text-red-600">*</span>
              </Label>
              <p className="text-xs text-muted-foreground">
                根据模型配置不同的价格参数
              </p>
            </div>

            {/* 价格信息加载状态 */}
            {priceInfoLoading && (
              <div className="text-sm text-muted-foreground">加载价格配置中...</div>
            )}

            {/* 价格信息加载失败 */}
            {priceInfoError && !priceInfoLoading && (
              <div className="text-sm text-red-600">加载价格配置失败: {priceInfoError}</div>
            )}

            {supplierDiscountParam && (
              <div className="space-y-2">
                <FieldRenderer
                  mode={mode}
                  schema={supplierDiscountParam}
                  value={priceInfoValues[supplierDiscountParam.code]}
                  onChange={(value) => handlePriceInfoFieldChange(supplierDiscountParam.code, value)}
                  endpoint={endpoint}
                />
              </div>
            )}

            {/* 动态渲染价格信息字段 */}
            {!priceInfoLoading && !priceInfoError && priceInfoParams.length > 0 && (
              <div className="space-y-4 p-4 border rounded-lg bg-muted/30">
                {displayPriceInfoParams.map((param) => (
                  <div key={param.code} className="space-y-2">
                    <FieldRenderer
                      mode={mode}
                      schema={param}
                      value={priceInfoValues[param.code]}
                      onChange={(value) => handlePriceInfoFieldChange(param.code, value)}
                      endpoint={endpoint}
                    />
                  </div>
                ))}
              </div>
            )}

            {/* 整体价格信息验证错误 */}
            {errors.priceInfo && (
              <p className="text-sm text-red-600">{errors.priceInfo}</p>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button className="cursor-pointer" variant="outline" onClick={handleCancel} disabled={isSubmitting}>
            取消
          </Button>
          <Button className="cursor-pointer" onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? '保存中...' : mode === 'create' ? '创建渠道' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <Dialog
      open={!!validationDialogMessage}
      onOpenChange={(isOpen) => {
        if (!isOpen) {
          setValidationDialogMessage(null)
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{getValidationDialogTitle(validationDialogMessage)}</DialogTitle>
          <DialogDescription>{validationDialogMessage}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={() => setValidationDialogMessage(null)}>知道了</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  )
}
