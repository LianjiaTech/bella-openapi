import type { ChannelFormData, FormErrors, DialogMode } from '../types'

/**
 * 验证 URL 格式
 */
export function isValidUrl(url: string): boolean {
  try {
    new URL(url)
    return true
  } catch {
    return false
  }
}

/**
 * 验证 JSON 格式
 */
export function isValidJson(jsonString: string): boolean {
  try {
    JSON.parse(jsonString)
    return true
  } catch {
    return false
  }
}

/**
 * 验证表单数据
 * @param formData 表单数据
 * @param mode 对话框模式 (create/edit)
 * @returns 验证错误对象，无错误则返回空对象
 */
export function validateChannelForm(
  formData: ChannelFormData,
  mode: DialogMode
): FormErrors {
  const errors: FormErrors = {}

  // ========== 新增模式特有验证 ==========
  if (mode === 'create') {
    // URL 验证
    if (!formData.url) {
      errors.url = '渠道转发URL不能为空'
    } else if (!isValidUrl(formData.url)) {
      errors.url = 'URL格式不正确'
    }

    // 协议验证
    if (!formData.protocol) {
      errors.protocol = '协议不能为空'
    }

    // 供应商验证
    if (!formData.supplier) {
      errors.supplier = '供应商不能为空'
    }
  }

  // ========== 通用验证 (新增和编辑都需要) ==========

  // 价格信息验证
  if (!formData.priceInfo) {
    errors.priceInfo = '价格信息不能为空'
  } else if (!isValidJson(formData.priceInfo)) {
    errors.priceInfo = '价格信息格式不正确'
  }

  // 队列名称验证 (条件必填)
  if (formData.queueMode !== 0 && !formData.queueName) {
    errors.queueName = '队列名称不能为空'
  }

  return errors
}

/**
 * 验证价格信息必填字段
 * @param priceInfoValues 价格信息值对象
 * @returns 是否通过验证
 */
export function validatePriceInfoRequired(
  priceInfoValues: Record<string, any>
): boolean {
  // 检查 input 和 output 字段是否存在且有效
  const hasInput =
    priceInfoValues.input !== undefined &&
    priceInfoValues.input !== null &&
    priceInfoValues.input !== ''
  const hasOutput =
    priceInfoValues.output !== undefined &&
    priceInfoValues.output !== null &&
    priceInfoValues.output !== ''

  return hasInput && hasOutput
}
