/**
 * 价格格式化工具函数
 * 用于统一处理模型价格显示逻辑
 */

/**
 * 格式化单个价格值
 * @param price - 价格值（可能是数字、字符串或 undefined）
 * @param defaultValue - 当价格无效时的默认显示值
 * @returns 格式化后的价格字符串
 */
export function formatPrice(
  price: number | string | undefined,
  defaultValue = "?"
): string {
  if (price === undefined || price === null) return defaultValue
  if (typeof price === "string") return price
  return price.toFixed(2) // 保留两位小数
}

/**
 * 格式化价格信息对象
 * @param priceDetails - 包含价格详情的对象
 * @returns 格式化后的价格信息对象
 */
export function formatPriceInfo(priceDetails?: {
  priceInfo?: {
    input?: number
    output?: number
    cachedRead?: number
  }
  unit?: string
}) {
  return {
    unit: priceDetails?.unit ?? "?",
    input: formatPrice(priceDetails?.priceInfo?.input),
    output: formatPrice(priceDetails?.priceInfo?.output),
    cachedRead:
      priceDetails?.priceInfo?.cachedRead !== undefined
        ? formatPrice(priceDetails?.priceInfo?.cachedRead)
        : null,
  }
}
