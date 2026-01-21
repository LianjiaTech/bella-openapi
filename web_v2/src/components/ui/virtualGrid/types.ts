import { ReactNode } from 'react'

/**
 * VirtualGrid 组件属性接口
 * 支持泛型，可适配任意数据类型的列表渲染
 */
export interface VirtualGridProps<T> {
  /** 数据源数组 */
  items: T[]

  /** 渲染单项的函数 */
  renderItem: (item: T) => ReactNode

  /** 获取唯一 key 的函数 */
  getItemKey: (item: T) => string

  /**
   * 智能预估单项高度的函数（可选）
   * 根据数据项的内容特征动态计算预估高度，提高预估精度
   * @param item 当前数据项
   * @returns 预估高度（单位：px）
   * @example
   * estimateItemSize={(model) => {
   *   const baseHeight = 200
   *   const featureHeight = model.features.length * 10
   *   return baseHeight + featureHeight
   * }}
   */
  estimateItemSize?: (item: T) => number

  /**
   * 固定预估单项高度（用于初始渲染，实际高度会动态测量，单位：px）
   * 如果提供了 estimateItemSize，此参数作为降级默认值
   * @default 258
   */
  estimatedItemHeight?: number

  /** 网格间距（单位：px） */
  gap?: number

  /** 预渲染缓冲区行数（上下各预渲染 N 行，避免快速滚动时出现白屏） */
  overscan?: number

  /** 空状态占位元素（当 items 为空时显示） */
  emptyElement?: ReactNode

  /** 容器额外样式类 */
  className?: string
}
