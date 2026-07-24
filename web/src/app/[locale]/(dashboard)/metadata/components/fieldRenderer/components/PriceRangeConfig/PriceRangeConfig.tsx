/**
 * 职责: 价格区间配置容器组件
 *
 * 功能:
 * - 组装所有 hooks (数据规范化、折叠状态、CRUD 操作)
 * - 传递状态和方法给 PriceRangeList 渲染 UI
 * - 对外暴露统一的 props 接口
 *
 * 设计:
 * - Container/Presentation 分离模式
 * - hooks 层负责状态和逻辑
 * - UI 层负责渲染
 *
 * 避免 re-render:
 * - hooks 中的方法使用 useCallback 确保引用稳定
 * - UI 组件使用 memo 优化
 */

"use client"

import React from "react"
import type { PriceRangeListProps } from "./types"
import { useRangeNormalization } from "./hooks/useRangeNormalization"
import { useExpandState } from "./hooks/useExpandState"
import { useRangeOperations } from "./hooks/useRangeOperations"
import { PriceRangeList } from "./components/PriceRangeList"

export function PriceRangeConfig({ mode, ranges = [], onRangesChange }: PriceRangeListProps) {
  // 数据规范化: 确保至少有一个默认区间
  const { normalizedRanges } = useRangeNormalization({
    ranges,
    onRangesChange,
  })

  // 折叠状态管理
  const { expandedRanges, toggleExpand, setExpandedRanges } = useExpandState({
    normalizedRanges,
  })

  // CRUD 操作
  const {
    addRange,
    removeRange,
    updateRange,
    updateMaxTokenWithCascade,
    handleEnableOutputRange,
    addOutputSubRange,
    removeOutputSubRange,
    updateOutputSubRange,
  } = useRangeOperations({
    mode,
    normalizedRanges,
    onRangesChange,
    setExpandedRanges,
  })

  return (
    <PriceRangeList
      normalizedRanges={normalizedRanges}
      expandedRanges={expandedRanges}
      onAddRange={addRange}
      onToggleExpand={toggleExpand}
      onRemoveRange={removeRange}
      onUpdateRange={updateRange}
      onUpdateMaxTokenWithCascade={updateMaxTokenWithCascade}
      onEnableOutputRange={handleEnableOutputRange}
      onAddOutputSubRange={addOutputSubRange}
      onRemoveOutputSubRange={removeOutputSubRange}
      onUpdateOutputSubRange={updateOutputSubRange}
    />
  )
}

// 导出类型供外部使用
export type { PriceRange, OutputRangePrice, PriceRangeListProps } from "./types"
export { MAX_TOKEN_VALUE, createDefaultOutputRangePrice, defaultRange } from "./constants"
