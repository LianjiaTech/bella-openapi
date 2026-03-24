/**
 * 职责: 数据规范化 - 确保 ranges 至少有一个默认区间
 *
 * 功能:
 * - 当传入空数组时,自动创建默认区间
 * - 副作用: 自动通知父组件更新状态 (仅在初始化时触发一次)
 *
 * 设计:
 * - useMemo 缓存规范化结果,避免每次渲染重新计算
 * - useEffect 监听初始化时机,确保父组件状态同步
 *
 * 避免 re-render:
 * - useMemo 依赖 ranges 引用,仅在 ranges 变化时重新计算
 * - useEffect 依赖精确,避免不必要的副作用触发
 */

import { useMemo, useEffect } from "react"
import { nanoid } from "nanoid"
import type { PriceRange } from "../types"
import { defaultRange } from "../constants"

interface UseRangeNormalizationParams {
  ranges: PriceRange[]
  onRangesChange: (ranges: PriceRange[]) => void
}

interface UseRangeNormalizationReturn {
  normalizedRanges: PriceRange[]
}

/**
 * 数据规范化 Hook
 *
 * @param ranges - 原始区间数组
 * @param onRangesChange - 区间变化回调
 * @returns normalizedRanges - 规范化后的区间数组 (至少包含一个默认区间)
 */
export function useRangeNormalization({
  ranges,
  onRangesChange,
}: UseRangeNormalizationParams): UseRangeNormalizationReturn {
  // 职责: 确保 ranges 至少有一个默认区间,避免空状态渲染错误
  // 如果传入空数组或未初始化,自动创建一个默认区间
  const normalizedRanges = useMemo(() => {
    if (ranges.length === 0) {
      return [
        {
          ...defaultRange,
          id: nanoid(),
        },
      ]
    }
    return ranges
  }, [ranges])

  // 职责: 当 normalizedRanges 由空数组初始化为默认值时,通知父组件更新状态
  useEffect(() => {
    if (ranges.length === 0 && normalizedRanges.length > 0) {
      onRangesChange(normalizedRanges)
    }
  }, [ranges.length, normalizedRanges, onRangesChange])

  return { normalizedRanges }
}
