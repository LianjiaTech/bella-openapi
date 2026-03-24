/**
 * 职责: 定义价格区间配置相关的常量和工具函数
 *
 * 包含:
 * - MAX_TOKEN_VALUE: Token 最大值常量
 * - defaultRange: 默认价格区间配置
 * - createDefaultOutputSubRange: 创建默认输出子区间工厂函数
 * - formatTokenValue: Token 值格式化工具函数
 */

import { nanoid } from "nanoid"
import type { OutputRangePrice, PriceRange } from "./types"

/**
 * Token 最大值 (Java Integer.MAX_VALUE)
 * 用于表示无限大的区间上界
 */
export const MAX_TOKEN_VALUE = 2147483647

/**
 * 默认输出区间价格工厂函数
 * 职责: 创建带有默认值的输出区间价格对象
 *
 * @param minToken - 最小Token值，默认 0
 * @param maxToken - 最大Token值，默认 MAX_TOKEN_VALUE
 * @param defaultOutput - 默认输出价格，默认 1 (通常从父区间的 inputRangePrice.output 继承)
 * @param mode - 操作模式：'create' 时图片/缓存字段为 null，'edit' 时为 0（用于接口返回值场景）
 * @returns 输出区间价格对象
 *
 * 设计说明:
 * - 用于启用输出区间时初始化第一个子区间
 * - defaultOutput 应从父区间的 output 字段继承，保持价格连续性
 * - mode='create': imageInput/imageOutput/cachedRead/cachedCreation 初始化为 null
 * - mode='edit': 保持原有逻辑，初始化为 0（实际值由接口返回数据覆盖）
 */
export const createDefaultOutputRangePrice = (
  minToken = 0,
  maxToken = MAX_TOKEN_VALUE,
  defaultOutput = 1,
  mode: 'create' | 'edit' = 'edit'
): OutputRangePrice => ({
  id: nanoid(),
  minToken,
  maxToken,
  input: 1,
  output: defaultOutput,
  imageInput: mode === 'create' ? null : 0,
  imageOutput: mode === 'create' ? null : 0,
  cachedRead: mode === 'create' ? null : 0,
  cachedCreation: mode === 'create' ? null : 0,
})

/**
 * 默认价格区间配置 (不包含 id)
 * 用于创建新区间时的初始值
 *
 * 设计说明:
 * - 图片/缓存字段初始化为 null，让用户自行决定是否填写
 * - edit 模式下会由接口返回数据覆盖
 */
export const defaultRange: Omit<PriceRange, "id"> = {
  inputRangePrice: {
    id: nanoid(),
    minToken: 0,
    maxToken: MAX_TOKEN_VALUE,
    input: 1,
    output: 1,
    imageInput: null,      // create 模式下无默认值
    imageOutput: null,     // create 模式下无默认值
    cachedRead: null,      // create 模式下无默认值
    cachedCreation: null,  // create 模式下无默认值
  },
  outputRangePrices: [],
}

/**
 * Token 值格式化工具函数
 * 职责: 将 Token 数值转换为可读字符串
 *
 * @param value - Token 数值
 * @returns 格式化后的字符串 (MAX_TOKEN_VALUE 显示为 "+∞")
 */
export const formatTokenValue = (value: number): string => {
  if (value === MAX_TOKEN_VALUE) return "+∞"
  return value.toLocaleString()
}
