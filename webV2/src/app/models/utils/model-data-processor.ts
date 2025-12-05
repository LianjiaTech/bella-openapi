import { Model } from "@/lib/types/openapi"

/**
 * 解析 features 字段
 * 将 JSON 字符串解析为对象,提取值为 true 的键组成逗号分隔的字符串
 */
const parseFeatures = (features: string | object): string => {
  try {
    if (typeof features === 'string') {
      const parsed = JSON.parse(features)
      return Object.keys(parsed)
        .filter(key => parsed[key] === true)
        .join(',')
    }
    // 如果已经是对象,直接处理
    if (typeof features === 'object' && features !== null) {
      return Object.keys(features)
        .filter(key => features[key as keyof typeof features] === true)
        .join(',')
    }
  } catch (e) {
    console.error('Failed to parse features:', e)
  }
  return ''
}

/**
 * 格式化数字为千分位格式
 */
const formatNumber = (value: number | string): string => {
  if (typeof value === 'number') {
    return value.toLocaleString()
  }
  return value
}

/**
 * 解析 properties 字段
 * 将 JSON 字符串解析为对象,并对特定数字字段应用千分位格式化
 */
const parseProperties = (properties: string | object): object => {
  try {
    let parsed: any

    if (typeof properties === 'string') {
      parsed = JSON.parse(properties)
    } else if (typeof properties === 'object' && properties !== null) {
      parsed = { ...properties }
    } else {
      return {}
    }

    // 对特定字段应用千分位格式化
    if (parsed.max_input_context !== undefined && typeof parsed.max_input_context === 'number') {
      parsed.max_input_context = formatNumber(parsed.max_input_context)
    }
    if (parsed.max_output_context !== undefined && typeof parsed.max_output_context === 'number') {
      parsed.max_output_context = formatNumber(parsed.max_output_context)
    }

    return parsed
  } catch (e) {
    console.error('Failed to parse properties:', e)
    return {}
  }
}

/**
 * 处理单个模型数据
 * 解析 features 和 properties 字段
 */
export const processModelData = (model: Model): Model => {
  const processedModel = { ...model }

  // 处理 features 字段
  if (processedModel.features) {
    processedModel.features = parseFeatures(processedModel.features)
  }

  // 处理 properties 字段
  if (processedModel.properties) {
    processedModel.properties = parseProperties(processedModel.properties)
  }

  return processedModel
}

/**
 * 批量处理模型数据数组
 */
export const processModelsArray = (models: Model[]): Model[] => {
  if (!models || !Array.isArray(models)) {
    return []
  }
  return models.map(processModelData)
}
