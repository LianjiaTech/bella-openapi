/**
 * 价格格式化工具函数
 * 用于统一处理模型价格显示逻辑
 */
export function shouldConvertTokenPrice(unit?: string, label?: string): boolean {
  if (unit?.includes('分/千token')) return true
  return !!label?.includes('千token');
}

export function shouldConvertImagePrice(unit?: string, label?: string): boolean {
  if (unit === '分/张') return true
  return !!label?.includes('分/张');
}

/**
 * 将后端价格（分/千token）转换为前端展示价格（元/百万token）
 */
export function convertTokenPrice(v: number): number {
  return parseFloat((v * 10).toFixed(10))
}

/**
 * 将后端图片按张价格（分/张）转换为前端展示价格（元/张）
 */
export function convertImagePrice(v: number): number {
  return parseFloat((v / 100).toFixed(10))
}

/**
 * 将前端图片按张价格（元/张）转换为后端保存价格（分/张）
 */
function restoreImagePrice(v: number): number {
  return parseFloat((v * 100).toFixed(10))
}

/**
 * 替换 label/unit 中的 token 单位文本，统一转换为 元/百万token
 */
export function convertTokenPriceLabel(text: string): string {
  return text
    .replace("分/千token", "元/百万token")
    .replace("/千token", "元/百万token")
}

/**
 * 替换 label/unit 中的图片按张单位文本，统一转换为 元/张
 */
export function convertImagePriceLabel(text: string): string {
  return text.replace("分/张", "元/张")
}

function convertVideoPriceLabel(text: string): string {
  return text.replace("分/秒", "元/秒")
}

/**
 * 替换单位文本：分/千token → 元/百万token；分/张 → 元/张
 */
function convertUnit(unit: string | undefined): string {
  if (!unit) return "暂无"
  return convertVideoPriceLabel(convertImagePriceLabel(convertTokenPriceLabel(unit)))
}

/**
 * 需要进行 分/千token → 元/百万token 换算的价格字段名集合
 */
export const TOKEN_PRICE_FIELDS = new Set([
  'input', 'output', 'imageInput', 'imageOutput',
  'cachedRead', 'cachedCreation', 'textTokenPrice', 'imageTokenPrice',
  'imageInputTokenPrice', 'imageOutputTokenPrice',
])

export const IMAGE_PRICE_PER_IMAGE_FIELDS = new Set([
  'ldPricePerImage', 'mdPricePerImage', 'hdPricePerImage', 'pricePerEdit',
])

function convertVideoPricePerSecond(v: number): number {
  return parseFloat((v / 100).toFixed(10))
}

function restoreVideoPricePerSecond(v: number): number {
  return parseFloat((v * 100).toFixed(10))
}

/**
 * 递归遍历价格对象，对需要前端展示换算的数值字段进行单位换算
 * load：后端 分/千token × 10 → 前端 元/百万token
 * save：前端 元/百万token ÷ 10 → 后端 分/千token
 * load：后端 分/张 ÷ 100 → 前端 元/张
 * save：前端 元/张 × 100 → 后端 分/张
 */
export function convertPriceObj(obj: any, direction: 'load' | 'save'): any {
  if (obj === null || obj === undefined) return obj
  if (Array.isArray(obj)) return obj.map((item) => convertPriceObj(item, direction))
  if (typeof obj === 'object') {
    const result: Record<string, any> = {}
    for (const [key, value] of Object.entries(obj)) {
      if (TOKEN_PRICE_FIELDS.has(key) && typeof value === 'number') {
        result[key] = direction === 'load'
          ? parseFloat((value * 10).toFixed(10))
          : parseFloat((value / 10).toFixed(10))
      } else if (IMAGE_PRICE_PER_IMAGE_FIELDS.has(key) && typeof value === 'number') {
        result[key] = direction === 'load' ? convertImagePrice(value) : restoreImagePrice(value)
      } else {
        result[key] = convertPriceObj(value, direction)
      }
    }
    return result
  }
  return obj
}

export function convertVideoDurationPriceObj(obj: any, direction: 'load' | 'save'): any {
  if (obj === null || obj === undefined) return obj
  if (Array.isArray(obj)) return obj.map((item) => convertVideoDurationPriceObj(item, direction))
  if (typeof obj === 'object') {
    const result: Record<string, any> = {}
    for (const [key, value] of Object.entries(obj)) {
      if (key === 'pricePerSecond' && typeof value === 'number') {
        result[key] = direction === 'load' ? convertVideoPricePerSecond(value) : restoreVideoPricePerSecond(value)
      } else {
        result[key] = convertVideoDurationPriceObj(value, direction)
      }
    }
    return result
  }
  return obj
}

/**
 * 深度转换 tiers 中所有价格字段（×10）
 */
function convertTiers(tiers: any[]): any[] {
  if (!Array.isArray(tiers)) return tiers
  return tiers.map((tier) => {
    const convertRangePrice = (rp: any): any => {
      if (!rp) return rp
      const result = { ...rp }
      TOKEN_PRICE_FIELDS.forEach((f) => {
        if (result[f] != null) result[f] = convertTokenPrice(result[f])
      })
      return result
    }
    return {
      ...tier,
      inputRangePrice: convertRangePrice(tier.inputRangePrice),
      outputRangePrices: Array.isArray(tier.outputRangePrices)
        ? tier.outputRangePrices.map(convertRangePrice)
        : tier.outputRangePrices,
    }
  })
}

/**
 * 格式化批量折扣显示文本
 * @param discount - 折扣值（1=不打折不显示，0=免费，其他=x折）
 * @returns 显示文本，discount===1 时返回 null（调用方不渲染）
 */
export function formatBatchDiscount(discount: number | undefined | null): string | null {
  if (discount === undefined || discount === null || discount === 1) return null;
  if (discount === 0) return "免费";
  return `${discount * 10} 折`;
}
interface TextToImagePriceInfo {
  size?: string;
  ldPricePerImage?: number;
  mdPricePerImage?: number;
  hdPricePerImage?: number;
  textTokenPrice?: number;
  imageTokenPrice?: number;
  imageInputTokenPrice?: number;
  imageOutputTokenPrice?: number;
  unit?: string;
}

interface VideoDurationPriceDetail {
  resolution?: string;
  audio?: boolean;
  pricePerSecond?: number;
}

/**
 * 将 displayPrice（Record<string,string>）转换为可渲染数组
 * 通过 shouldConvertTokenPrice(unit, label) 判断每个字段是否需要 ×10 换算：
 * - 顶层 unit 为 分/千token 时全部转换
 * - label 含 千token 时单字段转换
 * - 其他单位（分/万字、分/次等）不转换
 */
function transformDisplayPrice(priceDetails: any) {
  if (!priceDetails || !priceDetails.displayPrice) return
  const { displayPrice, unit } = priceDetails;

  return Object.entries(displayPrice).map(([label, value]) => {
    const shouldConvert = shouldConvertTokenPrice(unit, label)
    const shouldConvertImage = !shouldConvert && shouldConvertImagePrice(unit, label)
    return {
      label: shouldConvert
        ? convertTokenPriceLabel(label)
        : shouldConvertImage
          ? (() => {
              const imageLabel = convertImagePriceLabel(label)
              return imageLabel.includes("元/张") ? imageLabel : `${imageLabel}（元/张）`
            })()
          : label,
      value: shouldConvert
        ? convertTokenPrice(Number(value))
        : shouldConvertImage
          ? convertImagePrice(Number(value))
          : Number(value),
    }
  });
}
/**
 * 格式化单个价格值
 * @param price - 价格值（可能是数字、字符串或 undefined）
 * @param defaultValue - 当价格无效时的默认显示值
 * @returns 格式化后的价格字符串
 */
export function formatPrice(
    price: number | string | undefined,
    defaultValue = "暂无"
  ): string {
    if (price === undefined || price === null) return defaultValue
    if (typeof price === "string") return price
    return price.toFixed(2) // 保留两位小数
  }
  
  /**
   * 格式化价格信息对象
   * @param priceDetails - 包含价格详情的对象
   * @returns 格式化后的价格信息对象
   *
   * 设计说明：
   * token 价格展示为 元/百万token，图片按张价格展示为 元/张。
   * 其他非 token、非图片按张计费模型（TTS/ASR/OCR 等）的价格原样展示。
   */
  export function formatPriceInfo(priceDetails?: {
    priceInfo?: {
      batchDiscount?: number
      supplierDiscount?: number
      tiers?: any[]
      input?: number
      output?: number
      cachedRead?: number
      price?: number
      billingMode?: string
      details?: Array<TextToImagePriceInfo | VideoDurationPriceDetail>
      basicSearchPrice?: number
      advancedSearchPrice?: number
    }
    unit?: string
  }) {
    const tiers = priceDetails?.priceInfo?.tiers ?? []
    const priceInfo = priceDetails?.priceInfo
    const rawUnit = priceDetails?.unit
    const isTokenBased = shouldConvertTokenPrice(rawUnit)
    const isVideoTokenMode = (!priceInfo?.billingMode || priceInfo.billingMode === 'token') && rawUnit?.includes('分/千token')
    const unit = isVideoTokenMode
      ? '元/百万token'
      : convertUnit(rawUnit)
    // 统一换算入口：token 计费时 ×10，否则原样传给 formatPrice
    const fmt = (v?: number) => formatPrice(isTokenBased && v != null ? convertTokenPrice(v) : v)

    if(tiers.length > 0) {
      return {
        tag: 'tiers',
        data: {
          // tiers 仅在 分/千token 单位时才进行价格字段换算
          tiers: isTokenBased ? convertTiers(tiers) : tiers,
          batchDiscount: priceDetails?.priceInfo?.batchDiscount,
          supplierDiscount: priceDetails?.priceInfo?.supplierDiscount,
          unit,
        }
      }
    }
    if(priceInfo?.price) {
      return {
        tag: 'price',
        unit,
        price: fmt(priceInfo.price),
        batchDiscount: priceInfo?.batchDiscount,
        supplierDiscount: priceInfo?.supplierDiscount,
      }
    }
    if(priceInfo?.basicSearchPrice && priceInfo?.advancedSearchPrice) {
      return {
        tag: 'webSearch',
        unit,
        basicSearchPrice: fmt(priceInfo.basicSearchPrice),
        advancedSearchPrice: fmt(priceInfo.advancedSearchPrice),
        batchDiscount: priceInfo?.batchDiscount,
        supplierDiscount: priceInfo?.supplierDiscount,
      }
    }

    if(priceInfo?.billingMode === 'duration' && Array.isArray(priceInfo?.details)) {
      return {
        tag: 'displayPrice',
        data: priceInfo.details.map((detail: any) => ({
          label: [
            detail.resolution ? `${detail.resolution}P` : undefined,
            detail.audio === true ? '有声' : detail.audio === false ? '无声' : undefined,
          ].filter(Boolean).join(' / ') || '按秒',
          value: detail.pricePerSecond != null ? convertVideoPricePerSecond(Number(detail.pricePerSecond)) : null,
        })),
        unit: '元/秒',
        batchDiscount: priceDetails?.priceInfo?.batchDiscount,
        supplierDiscount: priceDetails?.priceInfo?.supplierDiscount,
      }
    }

    if(isVideoTokenMode) {
      return {
        tag: 'displayPrice',
        data: [
          { label: '输入token单价（元/百万token）', value: fmt(priceInfo?.input) },
          { label: '输出token单价（元/百万token）', value: fmt(priceInfo?.output) },
        ],
        unit: '元/百万token',
        batchDiscount: priceDetails?.priceInfo?.batchDiscount,
        supplierDiscount: priceDetails?.priceInfo?.supplierDiscount,
      }
    }

    if(Array.isArray(priceInfo?.details) && priceInfo.details.length > 0){
      const convertedDetails = priceInfo.details.map(detail => {
        const result: any = { ...detail }
        TOKEN_PRICE_FIELDS.forEach((f) => {
          if (result[f] != null) result[f] = convertTokenPrice(result[f])
        })
        IMAGE_PRICE_PER_IMAGE_FIELDS.forEach((f) => {
          if (result[f] != null) result[f] = convertImagePrice(result[f])
        })
        result.tokenUnit = "元/百万token"
        result.unit = convertUnit(result.unit ?? rawUnit)
        result.textTokenPriceStr = result.textTokenPrice != null ? result.textTokenPrice.toFixed(2) : null
        result.imageTokenPriceStr = result.imageTokenPrice != null ? result.imageTokenPrice.toFixed(2) : null
        result.imageInputTokenPriceStr = result.imageInputTokenPrice != null ? result.imageInputTokenPrice.toFixed(2) : null
        result.imageOutputTokenPriceStr = result.imageOutputTokenPrice != null ? result.imageOutputTokenPrice.toFixed(2) : null
        return result
      })
      return {
        tag: 'textToImage',
        data: convertedDetails.length > 0 ? [convertedDetails[0]] : [],
        batchDiscount: priceDetails?.priceInfo?.batchDiscount,
        supplierDiscount: priceDetails?.priceInfo?.supplierDiscount,
        unit,
      }
    }
    const transformedPrice = transformDisplayPrice(priceDetails)
    if(transformedPrice && transformedPrice.length > 0) {
      return {
        tag: 'displayPrice',
        data: transformedPrice,
        unit,
        batchDiscount: priceDetails?.priceInfo?.batchDiscount,
        supplierDiscount: priceDetails?.priceInfo?.supplierDiscount,
      }
    }
    return {
      unit,
      input: fmt(priceInfo?.input),
      output: fmt(priceInfo?.output),
      cachedRead: priceInfo?.cachedRead !== undefined ? fmt(priceInfo.cachedRead) : null,
    }
  }

/**
 * 递归转换 schema 树中所有节点的 name / description 展示文本
 */
export function convertSchemaLabels<T extends { name: string; description?: string; child?: { params: T[] } | null }>(
  params: T[]
): T[] {
  return params.map((param) => ({
    ...param,
    name: convertImagePriceLabel(convertTokenPriceLabel(param.name)),
    description: param.description ? convertImagePriceLabel(convertTokenPriceLabel(param.description)) : param.description,
    child: param.child
      ? { ...param.child, params: convertSchemaLabels(param.child.params) }
      : param.child,
  }))
}
