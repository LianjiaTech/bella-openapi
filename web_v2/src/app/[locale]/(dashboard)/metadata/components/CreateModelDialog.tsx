'use client'

import { useState, useRef, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/common/dialog'
import { Button } from '@/components/common/button'
import { Input } from '@/components/common/input'
import { Label } from '@/components/common/label'
import { Combobox } from '@/components/ui/combobox'
import { MultiSelect } from '@/components/ui/multiselect'
import { useEndpoints } from '../hooks'
import { getModelFeatureSchema, getModelPropertySchema, createModel } from '@/lib/api/metadata'
import type { Model } from '@/lib/types/openapi'
import type { JsonSchema } from '@/lib/types/metadata'
import axios from 'axios'
import { FieldRenderer } from './fieldRenderer/FieldRenderer'

interface CreateModelDialogProps {
  mode: 'create'
  open: boolean
  onClose: () => void
  onSuccess?: () => void // 成功后的回调，用于刷新列表
}

const ownerTypeOptions = [
  { value: 'system', label: 'System(系统用户)' },
  { value: 'person', label: 'Person(个人用户)' },
  { value: 'org', label: 'Org(组织用户)' },
]

export function CreateModelDialog({ mode, open, onClose, onSuccess }: CreateModelDialogProps) {
  // 获取能力点数据
  const { endpoints, loading: endpointsLoading, error: endpointsError, refetch } = useEndpoints()

  // 创建状态 - 控制按钮禁用
  const [isCreating, setIsCreating] = useState(false)

  // 表单状态 - 双向绑定
  const [formData, setFormData] = useState({
    capability: [] as string[],
    modelName: '',
    documentUrl: '',
    ownerType: '',
    ownerCode: '',
    ownerName: '',
  })

  // featureSchema 状态
  const [featureSchema, setFeatureSchema] = useState<JsonSchema>({ params: [] })

  // featureValues 状态 - 存储特性字段的值
  const [featureValues, setFeatureValues] = useState<Record<string, any>>({})

  // AbortController ref 用于取消请求
  const abortControllerRef = useRef<AbortController | null>(null)

  // propertySchema 状态
  const [propertySchema, setPropertySchema] = useState<JsonSchema>({ params: [] })

  // propertyValues 状态 - 存储属性字段的值
  const [propertyValues, setPropertyValues] = useState<Record<string, any>>({})

  // property AbortController ref 用于取消请求
  const propertyAbortControllerRef = useRef<AbortController | null>(null)

  // 重置函数 - 统一的重置逻辑
  const resetForm = () => {
    // 取消未完成的接口请求
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }

    // 取消未完成的 property 请求
    if (propertyAbortControllerRef.current) {
      propertyAbortControllerRef.current.abort()
      propertyAbortControllerRef.current = null
    }

    // 重置表单
    setFormData({
      capability: [],
      modelName: '',
      documentUrl: '',
      ownerType: '',
      ownerCode: '',
      ownerName: '',
    })

    // 清除错误
    setErrors({})

    // 重置 featureSchema
    setFeatureSchema({ params: [] })

    // 重置 featureValues
    setFeatureValues({})

    // 重置 propertySchema
    setPropertySchema({ params: [] })

    // 重置 propertyValues
    setPropertyValues({})
  }

  // 当弹窗关闭时重置表单
  useEffect(() => {
    if (!open) {
      resetForm()
    }
  }, [open])

  // 组件卸载时取消未完成的请求并重置状态
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }
      if (propertyAbortControllerRef.current) {
        propertyAbortControllerRef.current.abort()
        propertyAbortControllerRef.current = null
      }
    }
  }, [])

  // 验证错误状态
  const [errors, setErrors] = useState<{
    capability?: string
    modelName?: string
    ownerType?: string
    ownerCode?: string
    ownerName?: string
  }>({})

  // 处理输入框变化
  const handleInputChange = (field: keyof typeof formData) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const value = e.target.value
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }))
    // 清除该字段的错误
    if (errors[field as keyof typeof errors]) {
      setErrors((prev) => ({
        ...prev,
        [field]: undefined,
      }))
    }
  }

  // 处理下拉选择变化（多选）
  const handleSelectChange = async (value: string[]) => {
    // 更新表单数据
    setFormData((prev) => ({
      ...prev,
      capability: value,
    }))

    // 清除能力点的错误
    if (errors.capability) {
      setErrors((prev) => ({
        ...prev,
        capability: undefined,
      }))
    }

    // 取消上一次未完成的请求
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }

    // 如果 capability 为空，重置 schema 为默认值
    if (value.length === 0) {
      setFeatureSchema({ params: [] })
      setPropertySchema({ params: [] })
      return
    }

    // 创建新的 AbortController
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    try {
      // 调用接口，传入 signal
      const schema = await getModelFeatureSchema(value, abortController.signal)
      // 如果请求未被取消，更新 schema
      if (!abortController.signal.aborted) {
        setFeatureSchema(schema || { params: [] })
      }
    } catch (error: any) {
      // 如果是主动取消的请求，不处理
      if (axios.isCancel(error) || abortController.signal.aborted) {
        return
      }
      // 接口出错，静默设置为默认值
      setFeatureSchema({ params: [] })
    } finally {
      // 清理 ref
      if (abortControllerRef.current === abortController) {
        abortControllerRef.current = null
      }
    }

    // ========== Property 请求处理 ==========
    // 取消上一次未完成的 property 请求
    if (propertyAbortControllerRef.current) {
      propertyAbortControllerRef.current.abort()
      propertyAbortControllerRef.current = null
    }

    // 创建新的 AbortController
    const propertyAbortController = new AbortController()
    propertyAbortControllerRef.current = propertyAbortController

    try {
      // 调用接口，传入 signal
      const schema = await getModelPropertySchema(value, propertyAbortController.signal)
      // 如果请求未被取消，更新 schema
      if (!propertyAbortController.signal.aborted) {
        setPropertySchema(schema || { params: [] })
      }
    } catch (error: any) {
      // 如果是主动取消的请求，不处理
      if (axios.isCancel(error) || propertyAbortController.signal.aborted) {
        return
      }
      // 接口出错，静默设置为默认值
      setPropertySchema({ params: [] })
    } finally {
      // 清理 ref
      if (propertyAbortControllerRef.current === propertyAbortController) {
        propertyAbortControllerRef.current = null
      }
    }
  }

  // 验证模型名称
  const validateModelName = (name: string): string | undefined => {
    if (!name || name.trim() === '') {
      return '模型名称不能为空'
    }
    if (name.length > 60) {
      return '模型名称长度不能超过 60 个字符'
    }
    // 检查是否以数字、特殊字符或空格开头
    const firstChar = name[0]
    if (/[\d\s]/.test(firstChar) || !/[a-zA-Z\u4e00-\u9fa5]/.test(firstChar)) {
      return '模型名称不能以数字、特殊字符或空格开头'
    }
    return undefined
  }

  // 验证必填字段
  const validateRequired = (value: string, fieldName: string): string | undefined => {
    if (!value || value.trim() === '') {
      return `${fieldName}不能为空`
    }
    return undefined
  }

  // 验证整个表单
  const validateForm = (): boolean => {
    const newErrors: typeof errors = {}

    // 验证能力点（多选，检查数组是否为空）
    const capabilityError = formData.capability.length === 0 ? '能力点不能为空' : undefined
    if (capabilityError) newErrors.capability = capabilityError

    // 验证模型名称
    const modelNameError = validateModelName(formData.modelName)
    if (modelNameError) newErrors.modelName = modelNameError

    // 验证所有者类型
    const ownerTypeError = validateRequired(formData.ownerType, '所有者类型')
    if (ownerTypeError) newErrors.ownerType = ownerTypeError

    // 验证所有者编码
    const ownerCodeError = validateRequired(formData.ownerCode, '所有者编码')
    if (ownerCodeError) newErrors.ownerCode = ownerCodeError

    // 验证所有者名称
    const ownerNameError = validateRequired(formData.ownerName, '所有者名称')
    if (ownerNameError) newErrors.ownerName = ownerNameError

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  // 处理创建操作
  const handleCreate = async () => {
    // 1. 验证表单
    if (!validateForm()) {
      return
    }

    // 2. 设置加载状态，禁用按钮
    setIsCreating(true)

    try {
      // 3. 构造符合 Model 类型的请求数据
      const modelData: Model = {
        modelName: formData.modelName,
        documentUrl: formData.documentUrl,
        ownerType: formData.ownerType,
        ownerCode: formData.ownerCode,
        ownerName: formData.ownerName,
        endpoints: formData.capability,
        features: JSON.stringify(featureValues),
        properties: JSON.stringify(propertyValues),
        // 使用默认值
        visibility: 'private',
        status: 'active',
        linkedTo: ''
      }

      // 4. 调用接口创建模型
      await createModel(modelData as Model)

      // 5. 成功处理：关闭弹窗并刷新列表
      onClose()
      onSuccess?.() // 调用父组件的回调函数刷新列表
    } catch (error: any) {
      // 6. 失败处理：仅恢复按钮状态，不关闭弹窗
      console.error('创建模型失败:', error)
      // 移除 toast 提示，静默失败
    } finally {
      // 7. 恢复按钮状态
      setIsCreating(false)
    }
  }

  // 处理取消操作
  const handleCancel = () => {
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => {
      // 只在明确关闭时调用 onClose，禁止通过遮罩层关闭
      if (!isOpen) {
        onClose()
      }
    }}>
      <DialogContent 
        className="max-w-lg"
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
          <DialogTitle>新增模型</DialogTitle>
          <DialogDescription>
            填写模型的基本信息以创建新的模型配置
          </DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-1">
          {/* 能力点 */}
          <div>
            <Label htmlFor="capability" className="mb-4 block">能力点 <span className="text-red-600">*</span></Label>
            <MultiSelect
              id="capability"
              options={endpoints.map((endpoint) => ({
                value: endpoint.value,
                label: endpoint.label,
              }))}
              value={formData.capability}
              onValueChange={handleSelectChange}
              disabled={endpointsLoading || !!endpointsError}
              placeholder={
                endpointsLoading
                  ? "加载中..."
                  : endpointsError
                  ? "加载失败"
                  : endpoints.length === 0
                  ? "暂无可用能力点"
                  : "选择能力点（可多选）"
              }
              searchPlaceholder="搜索能力点..."
              emptyText="未找到匹配的能力点"
              className={errors.capability ? 'border-red-600' : ''}
            />
            {/* 验证错误提示 */}
            {errors.capability && (
              <p className="mt-1 text-sm text-red-600">{errors.capability}</p>
            )}
            {/* 错误提示和重试按钮 */}
            {endpointsError && (
              <div className="mt-2 flex items-center justify-between text-sm text-red-600">
                <span>加载失败: {endpointsError.message}</span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={refetch}
                  className="ml-2"
                >
                  重试
                </Button>
              </div>
            )}
          </div>

          {/* 模型名称 */}
          <div className="space-y-2">
            <Label htmlFor="modelName" className="mb-4 block">模型名称 <span className="text-red-600">*</span></Label>
            <Input
              id="modelName"
              placeholder="例如: GPT-5 Mini"
              value={formData.modelName}
              onChange={handleInputChange('modelName')}
              className={errors.modelName ? 'border-red-600' : ''}
            />
            {errors.modelName && (
              <p className="text-sm text-red-600">{errors.modelName}</p>
            )}
          </div>

          {/* 文档 URL */}
          <div className="space-y-2">
            <Label htmlFor="documentUrl" className="mb-4 block">文档 URL</Label>
            <Input
              id="documentUrl"
              placeholder="https://docs.example.com/model"
              value={formData.documentUrl}
              onChange={handleInputChange('documentUrl')}
            />
          </div>

          {/* 模型特性 - 动态渲染 */}
          {featureSchema && featureSchema.params.length > 0 && (
            <div className="space-y-2">
              <Label className="mb-4 block">模型特性</Label>
              <div className="space-y-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
                {featureSchema.params.map((param) => (
                  <div key={param.code} className="flex items-center gap-4">
                    <Label htmlFor={param.code} className="w-32 flex-shrink-0 text-sm font-medium text-gray-700">
                      {param.name}
                    </Label>
                    <div className="flex-1">
                      {FieldRenderer({
                        mode,
                        schema: param,
                        value: featureValues[param.code],
                        onChange: (value) => {
                          setFeatureValues((prev) => ({
                            ...prev,
                            [param.code]: value,
                          }))
                        },
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 模型属性 - 动态渲染 */}
          {propertySchema && propertySchema.params.length > 0 && (
            <div className="space-y-2">
              <Label className="mb-4 block">模型属性</Label>
              <div className="space-y-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
                {propertySchema.params.map((param) => (
                  <div key={param.code} className="flex items-center gap-4">
                    <Label htmlFor={param.code} className="w-32 flex-shrink-0 text-sm font-medium text-gray-700">
                      {param.name}
                    </Label>
                    <div className="flex-1">
                      {FieldRenderer({
                        mode: 'create',
                        schema: param,
                        value: propertyValues[param.code],
                        onChange: (value) => {
                          setPropertyValues((prev) => ({
                            ...prev,
                            [param.code]: value,
                          }))
                        },
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 所有者类型 */}
          <div className="space-y-2">
            <Label htmlFor="ownerType" className="mb-4 block">所有者类型 <span className="text-red-600">*</span></Label>
            <Combobox
              options={ownerTypeOptions}
              value={formData.ownerType}
              onValueChange={(value) => {
                setFormData((prev) => ({
                  ...prev,
                  ownerType: value,
                }))
                // 清除所有者类型的错误
                if (errors.ownerType) {
                  setErrors((prev) => ({
                    ...prev,
                    ownerType: undefined,
                  }))
                }
              }}
              placeholder="选择所有者类型, 如: System(系统用户)"
              searchPlaceholder="搜索或输入所有者类型..."
              emptyText="未找到匹配项，将使用自定义值"
              id="ownerType"
              className={errors.ownerType ? 'border-red-600' : ''}
            />
            {errors.ownerType && (
              <p className="text-sm text-red-600">{errors.ownerType}</p>
            )}
          </div>

          {/* 所有者编码 */}
          <div className="space-y-2">
            <Label htmlFor="ownerCode" className="mb-4 block">所有者编码 <span className="text-red-600">*</span></Label>
            <Input
              id="ownerCode"
              placeholder="例如: openai"
              value={formData.ownerCode}
              onChange={handleInputChange('ownerCode')}
              className={errors.ownerCode ? 'border-red-600' : ''}
            />
            {errors.ownerCode && (
              <p className="text-sm text-red-600">{errors.ownerCode}</p>
            )}
          </div>

          {/* 所有者名称 */}
          <div className="space-y-2">
            <Label htmlFor="ownerName" className="mb-4 block">所有者名称 <span className="text-red-600">*</span></Label>
            <Input
              id="ownerName"
              placeholder="例如: OpenAI"
              value={formData.ownerName}
              onChange={handleInputChange('ownerName')}
              className={errors.ownerName ? 'border-red-600' : ''}
            />
            {errors.ownerName && (
              <p className="text-sm text-red-600">{errors.ownerName}</p>
            )}
          </div>
        </div>

        <DialogFooter >
          <Button variant="outline" onClick={handleCancel} disabled={isCreating}>
            取消
          </Button>
          <Button
            onClick={handleCreate}
            disabled={endpointsLoading || !!endpointsError || endpoints.length === 0 || isCreating}
          >
            {isCreating ? '创建中...' : '创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
