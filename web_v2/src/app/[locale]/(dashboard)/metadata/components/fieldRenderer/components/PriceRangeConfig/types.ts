/**
 * 职责: 定义价格区间配置相关的 TypeScript 类型
 *
 * 包含:
 * - OutputSubRange: 输出Token子区间类型
 * - PriceRange: 价格区间类型
 * - PriceRangeListProps: 组件对外 API props 类型
 */

/**
 * 输出Token子区间
 * 用于在启用输出区间时，进一步细分输出Token的价格梯度
 */
export interface OutputSubRange {
  id: string
  minToken: number
  maxToken: number
  inputPrice: number | null
  outputPrice: number | null
  imageInput: number | null
  imageOutput: number | null
  cacheRead: number | null
  cacheCreate: number | null
}

/**
 * 价格区间
 * 定义基于输入Token范围的价格配置
 */
export interface PriceRange {
  id: string
  inputRangePrice: InputRangePrice
  outputRangePrices?: OutputRangePrice[]
}
/**
 * 输入区间价格类型定义
 * 可用于对接通用的区间价格描述，如表单初始值、服务端协议等
 */
export interface InputRangePrice {
  id: string
  minToken: number
  maxToken: number
  input: number | null              // 输入价格 (create模式初始化为null，edit模式为number)
  output: number | null             // 输出价格 (create模式初始化为null，edit模式为number)
  imageInput?: number | null        // 可选，图片输入价格 (create模式为null，edit模式为number或undefined)
  imageOutput?: number | null       // 可选，图片输出价格 (create模式为null，edit模式为number或undefined)
  cachedRead?: number | null        // 可选，缓存读取价格 (create模式为null，edit模式为number或undefined)
  cachedCreation?: number | null    // 可选，缓存创建价格 (默认可为 input * 1.25，create模式为null)
}
/**
 * 输出token分段定价
 */
export interface OutputRangePrice {
  id: string
  minToken: number
  maxToken: number
  input: number | null              // 输入价格 (create模式初始化为null，edit模式为number)
  output: number | null             // 输出价格 (create模式初始化为null，edit模式为number)
  imageInput?: number | null        // 可选，图片输入价格 (create模式为null，edit模式为number或undefined)
  imageOutput?: number | null       // 可选，图片输出价格 (create模式为null，edit模式为number或undefined)
  cachedRead?: number | null        // 可选，缓存读取价格 (create模式为null，edit模式为number或undefined)
  cachedCreation?: number | null    // 可选，缓存创建价格 (默认可为 input * 1.25，create模式为null)
}
/**
 * PriceRangeConfig 组件对外 API
 */
export interface PriceRangeListProps {
  mode: 'create' | 'edit'  // 操作模式：create 时图片/缓存字段初始化为 null，edit 时使用接口返回值
  ranges: PriceRange[]
  onRangesChange: (ranges: PriceRange[]) => void
  unit?: '分/千token' | undefined
}
