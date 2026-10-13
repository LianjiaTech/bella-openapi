/**
 * 职责: 管理价格区间的展开/收起状态
 *
 * 功能:
 * - 维护展开区间的 id 集合
 * - 提供切换展开状态的方法
 * - 默认展开所有区间
 *
 * 设计:
 * - 使用 Set 数据结构存储展开状态,O(1) 查询效率
 * - toggleExpand 使用 useCallback 确保引用稳定
 *
 * 避免 re-render:
 * - useCallback 依赖 expandedRanges,仅在状态变化时更新引用
 * - 子组件可安全地将 toggleExpand 放入依赖数组
 */

import { useState, useCallback, useEffect } from "react"
import type { PriceRange } from "../types"

interface UseExpandStateParams {
  normalizedRanges: PriceRange[]
}

interface UseExpandStateReturn {
  expandedRanges: Set<string>
  toggleExpand: (id: string) => void
  setExpandedRanges: React.Dispatch<React.SetStateAction<Set<string>>>
}

/**
 * 折叠状态管理 Hook
 *
 * @param normalizedRanges - 规范化后的区间数组
 * @returns expandedRanges - 展开的区间 id 集合
 * @returns toggleExpand - 切换展开状态的方法
 * @returns setExpandedRanges - 直接设置展开状态的 setter (用于 addRange 等操作)
 */
export function useExpandState({
  normalizedRanges,
}: UseExpandStateParams): UseExpandStateReturn {
  // 职责: 初始化展开状态,默认展开所有区间
  const [expandedRanges, setExpandedRanges] = useState<Set<string>>(
    new Set(normalizedRanges.map((r) => r.id))
  )

  // 职责: 监听 normalizedRanges 变化,自动展开新增的区间
  // 当用户添加新区间时,确保新区间默认展开
  useEffect(() => {
    setExpandedRanges((prev) => {
      const newExpanded = new Set(prev)
      let hasChanges = false

      // 遍历所有区间,将新增的区间 id 添加到展开集合
      normalizedRanges.forEach((range) => {
        if (!newExpanded.has(range.id)) {
          newExpanded.add(range.id)
          hasChanges = true
        }
      })

      // 仅在有变化时返回新的 Set,避免无效渲染
      return hasChanges ? newExpanded : prev
    })
  }, [normalizedRanges])

  // 职责: 切换指定区间的展开/收起状态
  // 使用 useCallback 确保引用稳定,避免子组件无效 re-render
  const toggleExpand = useCallback((id: string) => {
    setExpandedRanges((prev) => {
      const newExpanded = new Set(prev)
      if (newExpanded.has(id)) {
        newExpanded.delete(id)
      } else {
        newExpanded.add(id)
      }
      return newExpanded
    })
  }, [])

  return {
    expandedRanges,
    toggleExpand,
    setExpandedRanges,
  }
}
