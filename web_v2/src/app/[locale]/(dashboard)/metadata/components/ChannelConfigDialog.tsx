'use client'

import { useState, useEffect } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/common/dialog'
import { Button } from '@/components/common/button'
import { Switch } from '@/components/common/switch'
import { Label } from '@/components/common/label'
import { Input } from '@/components/common/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import type { ChannelDetails } from '@/lib/types/metadata'
import type { JsonSchema } from '@/lib/types/metadata'
import { listProtocols, getPriceInfoSchema, getChannelInfoSchema, createChannel, updateChannel, createPrivateChannel, updatePrivateChannel } from '@/lib/api/metadata'
import { FieldRenderer } from './fieldRenderer/FieldRenderer'
import { priorityOptions } from '@/lib/constants/metadata'
import { TOKEN_PRICE_FIELDS, shouldConvertTokenPrice, convertSchemaLabels, convertPriceObj } from '@/lib/utils/price'

function isTokenBased(schema: JsonSchema, existingUnit?: string): boolean {
  if (shouldConvertTokenPrice(existingUnit)) return true
  return schema.params.some(p =>
    p.code === 'tiers' ||
    shouldConvertTokenPrice(undefined, p.name) ||
    (p.child != null && isTokenBased(p.child))
  )
}


interface ChannelConfigDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mode: 'create' | 'edit'
  initialData?: Partial<ChannelDetails>
  modelName?: string
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
  queueMode: number
  queueName: string
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
    queueMode: 0,
    queueName: '',
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

  // 渠道信息动态字段值
  const [channelInfoValues, setChannelInfoValues] = useState<Record<string, any>>({})

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
      queueMode: 0,
      queueName: '',
    })
    setErrors({})
  }

  /**
   * 初始化表单数据和协议列表
   * 混合加载策略:
   * - 新增模式: 先加载协议列表,再允许填写表单
   * - 编辑模式: 立即回填表单数据,后台加载协议列表(仅用于显示友好名称)
   */
  useEffect(() => {
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
          setPriceInfoSchema({ ...priceSchema, params: convertSchemaLabels(priceSchema.params) })
          setPriceInfoError(null)

          // 初始化价格信息字段默认值
          const initialPriceValues: Record<string, any> = {}
          priceSchema.params.forEach((param) => {
            if (param.valueType === 'number') {
              initialPriceValues[param.code] = 0
            } else if (param.valueType === 'string') {
              initialPriceValues[param.code] = param.code === 'unit' ? '分/千token' : ''
            }
          })
          setPriceInfoValues(initialPriceValues)

          // 将初始值序列化为 JSON 字符串存入表单
          setFormData((prev) => ({
            ...prev,
            priceInfo: JSON.stringify(initialPriceValues, null, 2)
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
      console.log('ChannelConfigDialog edit', initialData)
      // 1. 立即回填表单数据
      setFormData({
        url: initialData.url || '',
        protocol: initialData.protocol || '',
        supplier: initialData.supplier || '',
        dataDestination: initialData.dataDestination || '',
        priceInfo: initialData.priceInfo || '',
        priority: initialData.priority || '',
        channelInfo: initialData.channelInfo || '',
        trialEnabled: initialData.trialEnabled || 0,
        queueMode: initialData.queueMode || 0,
        queueName: initialData.queueName || '',
      })

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
              setPriceInfoSchema({ ...schema, params: convertSchemaLabels(schema.params) })
              if (parsedValues) {
                const displayValues = tokenBased ? convertPriceObj(parsedValues, 'load') : parsedValues
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
        if (initialData.protocol) {
          getChannelInfoSchema('model', modelName, initialData.protocol)
            .then((schema) => {
              setChannelInfoSchema({ ...schema, params: convertSchemaLabels(schema.params) })
              // 尝试解析现有的渠道信息 JSON
              if (initialData.channelInfo) {
                try {
                  const parsedValues = JSON.parse(initialData.channelInfo)
                  setChannelInfoValues(parsedValues)
                } catch (error) {
                  console.error('解析渠道信息失败:', error)
                }
              }
            })
            .catch((error) => {
              console.error('获取渠道信息 schema 失败(编辑模式,非阻塞):', error)
            })
        }
      }
    }
  }, [open, mode, initialData, modelName])

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
    if (mode !== 'create' || !formData.protocol || !modelName) {
      return
    }

    const fetchChannelInfoSchema = async () => {
      setChannelInfoLoading(true)
      setChannelInfoError(null)
      try {
        const schema = await getChannelInfoSchema('model', modelName, formData.protocol)
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
        console.error('获取渠道信息 Schema 失败:', error)
        setChannelInfoError(error instanceof Error ? error.message : '获取渠道信息配置失败')
        setChannelInfoSchema({ params: [] })
      } finally {
        setChannelInfoLoading(false)
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
    const updatedValues = {
      ...priceInfoValues,
      [fieldCode]: value,
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

    if (!formData.url) {
      newErrors.url = '渠道转发URL不能为空'
    } else if (!isValidUrl(formData.url)) {
      newErrors.url = 'URL格式不正确'
    }

    // 协议验证 
    if (!formData.protocol) {
      newErrors.protocol = '协议不能为空'
    }

    // 供应商验证 
    if (!formData.supplier) {
      newErrors.supplier = '供应商不能为空'
    }
    // 价格验证 (
    if (!formData.priceInfo) {
      newErrors.priceInfo = '价格信息不能为空'
    } 
    // else {
    //   // 验证 JSON 格式
    //   try {
    //     const priceData = JSON.parse(formData.priceInfo)
    //   } catch (error) {
    //     newErrors.priceInfo = '价格信息格式不正确'
    //   }
    // }

    // 队列名称验证 (条件必填)
    if (formData.queueMode !== 0 && !formData.queueName) {
      newErrors.queueName = '队列名称不能为空'
    }

    setErrors(newErrors)
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
      const priceData = isPriceTokenBased
        ? convertPriceObj(parsed, 'save')
        : { ...parsed }
      if (isPriceTokenBased && priceInfoSchema.params.some(p =>
        p.code === 'tiers' || shouldConvertTokenPrice(undefined, p.name)
      )) {
        priceData.unit = '分/千token'
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

    // 3. 压缩并清洗 channelInfo，避免将空字符串和 defaultMaxToken=0 存入数据库
    let finalChannelInfo = formData.channelInfo
    if (finalChannelInfo) {
      try {
        const parsedChannelInfo = JSON.parse(finalChannelInfo)
        const cleanedChannelInfo = Object.fromEntries(
          Object.entries(parsedChannelInfo).filter(([key, value]) => {
            if (value === '') return false
            if (key === 'defaultMaxToken' && value === 0) return false
            return true
          })
        )
        finalChannelInfo = JSON.stringify(cleanedChannelInfo)
      } catch {
        // 解析失败则使用原始值
      }
    }

    // 4. 设置加载状态
    setIsSubmitting(true)
    try {
      if (mode === 'create') {
        const channelData = {
          entityType: 'model',
          entityCode: modelName,
          url: formData.url,
          protocol: formData.protocol,
          supplier: formData.supplier,
          dataDestination: formData.dataDestination,
          priceInfo: finalPriceInfo,
          priority: formData.priority,
          channelInfo: finalChannelInfo,
          trialEnabled: formData.trialEnabled,
          queueMode: formData.queueMode,
          queueName: formData.queueName || undefined,
        }
        if (isPrivate) {
          await createPrivateChannel(channelData)
        } else {
          await createChannel(channelData)
        }
      } else {
        if (!initialData?.channelCode) {
          throw new Error('缺少渠道编码')
        }
        const updateData = {
          url: formData.url,
          protocol: formData.protocol,
          supplier: formData.supplier,
          dataDestination: formData.dataDestination,
          priceInfo: finalPriceInfo,
          priority: formData.priority,
          channelInfo: finalChannelInfo,
          trialEnabled: formData.trialEnabled,
          queueMode: formData.queueMode,
          queueName: formData.queueName || undefined,
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
  console.log(priceInfoSchema,'ChannelConfigDialog', priceInfoValues)
  return (
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
          {/* ========== 基础信息 ========== */}
          <div className="space-y-4">
            {/* 数据流向 */}
            <div className="space-y-2">
              <Label htmlFor="dataDestination">数据流向</Label>
              <Select
                value={formData.dataDestination}
                onValueChange={handleSelectChange('dataDestination')}
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
                disabled={protocolsLoading}
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
                  {channelInfoSchema.params.map((param) => (
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
                      />
                    </div>
                  ))}
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

            {/* 动态渲染价格信息字段 */}
            {!priceInfoLoading && !priceInfoError && priceInfoSchema.params.length > 0 && (
              <div className="space-y-4 p-4 border rounded-lg bg-muted/30">
                {priceInfoSchema.params.map((param) => (
                  <div key={param.code} className="space-y-2">
                    <FieldRenderer
                      mode={mode}
                      schema={param}
                      value={priceInfoValues[param.code]}
                      onChange={(value) => handlePriceInfoFieldChange(param.code, value)}
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
  )
}
