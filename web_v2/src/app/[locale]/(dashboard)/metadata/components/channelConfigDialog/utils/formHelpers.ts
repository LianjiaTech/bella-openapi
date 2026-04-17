import type { ChannelDetails, JsonSchema } from '@/lib/types/metadata'
import type { ChannelFormData } from '../types'
import { INITIAL_FORM_DATA } from '../constants'

/**
 * 从初始数据构建表单数据
 * @param initialData 初始渠道详情数据
 * @returns 表单数据对象
 */
export function buildFormDataFromInitial(
  initialData?: Partial<ChannelDetails>
): ChannelFormData {
  if (!initialData) {
    return { ...INITIAL_FORM_DATA }
  }

  return {
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
  }
}

/**
 * 从 Schema 初始化字段默认值
 * @param schema JSON Schema 对象
 * @param existingJson 已有的 JSON 字符串 (编辑模式)
 * @returns 字段值对象
 */
export function initializeValuesFromSchema(
  schema: JsonSchema,
  existingJson?: string
): Record<string, any> {
  // 如果有已有数据，优先解析
  if (existingJson) {
    try {
      return JSON.parse(existingJson)
    } catch (error) {
      console.error('解析已有 JSON 失败:', error)
    }
  }

  // 否则根据 Schema 初始化默认值
  const initialValues: Record<string, any> = {}

  schema.params.forEach((param) => {
    if (param.valueType === 'number') {
      initialValues[param.code] = 0
    } else if (param.valueType === 'string') {
      // 特殊处理 unit 字段的默认值
      initialValues[param.code] = param.code === 'unit' ? '分/千token' : ''
    } else if (param.valueType === 'boolean') {
      initialValues[param.code] = false
    } else {
      initialValues[param.code] = ''
    }
  })

  return initialValues
}

/**
 * 将字段值对象序列化为格式化的 JSON 字符串
 * @param values 字段值对象
 * @returns 格式化的 JSON 字符串
 */
export function serializeToJson(values: Record<string, any>): string {
  return JSON.stringify(values, null, 2)
}

/**
 * 解析 JSON 字符串为对象
 * @param jsonString JSON 字符串
 * @returns 解析后的对象，失败返回空对象
 */
export function parseJsonSafely(jsonString: string): Record<string, any> {
  try {
    return JSON.parse(jsonString)
  } catch (error) {
    console.error('JSON 解析失败:', error)
    return {}
  }
}

/**
 * 转换协议列表为选项数组
 * @param protocolsData 协议数据对象 {value: label}
 * @returns 选项数组 [{value, label}]
 */
export function transformProtocolsToOptions(
  protocolsData: Record<string, string>
): Array<{ value: string; label: string }> {
  return Object.entries(protocolsData).map(([value, label]) => ({
    value,
    label: label as string,
  }))
}

/**
 * 构建提交数据对象
 * @param formData 表单数据
 * @param mode 模式 (create/edit)
 * @param modelName 模型名称
 * @param channelCode 渠道编码 (编辑模式必需)
 * @returns 提交数据对象
 */
export function buildSubmitData(
  formData: ChannelFormData,
  mode: 'create' | 'edit',
  modelName?: string,
  channelCode?: string
) {
  if (mode === 'create') {
    return {
      entityType: 'model' as const,
      entityCode: modelName,
      url: formData.url,
      protocol: formData.protocol,
      supplier: formData.supplier,
      dataDestination: formData.dataDestination,
      priceInfo: formData.priceInfo,
      priority: formData.priority,
      channelInfo: formData.channelInfo,
      trialEnabled: formData.trialEnabled,
      queueMode: formData.queueMode,
      queueName: formData.queueName || undefined,
    }
  } else {
    // 编辑模式只传递允许编辑的字段
    return {
      priceInfo: formData.priceInfo,
      priority: formData.priority,
      channelInfo: formData.channelInfo,
      trialEnabled: formData.trialEnabled,
      queueMode: formData.queueMode,
      queueName: formData.queueName || undefined,
    }
  }
}
