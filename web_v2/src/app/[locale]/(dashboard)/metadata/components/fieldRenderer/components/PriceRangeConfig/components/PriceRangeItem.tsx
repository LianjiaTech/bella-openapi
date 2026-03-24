/**
 * 职责: 渲染单个价格区间的完整配置
 *
 * 功能:
 * - 组装区间头部、输入配置、开关、价格配置等子组件
 * - 处理展开/收起状态
 * - 根据 enableOutputRange 切换显示静态价格或输出区间列表
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 组装所有子组件,形成完整的区间配置 UI
 * - 条件渲染: enableOutputRange 控制显示 StaticPriceSection 或 OutputSubRangeList
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹所有回调函数
 */

import React from "react"
import { cn } from "@/lib/utils"
import type { PriceRange, OutputRangePrice } from "../types"
import { RangeHeader } from "./RangeHeader"
import { RangeInputSection } from "./RangeInputSection"
import { OutputRangeToggle } from "./OutputRangeToggle"
import { StaticPriceSection } from "./StaticPriceSection"
import { OutputSubRangeList } from "./OutputSubRangeList"

interface PriceRangeItemProps {
  range: PriceRange
  index: number
  isExpanded: boolean
  canDelete: boolean
  isFirst: boolean
  isLast: boolean
  isSingle: boolean
  nextRangeId?: string
  onToggleExpand: (id: string) => void
  onRemove: (id: string) => void
  onUpdateRange: (id: string, updates: Partial<PriceRange>) => void
  onUpdateMaxTokenWithCascade: (currentRangeId: string, nextRangeId: string | undefined, newMaxToken: number) => void
  onEnableOutputRange: (rangeId: string, enabled: boolean) => void
  onAddOutputSubRange: (rangeId: string) => void
  onRemoveOutputSubRange: (rangeId: string, outputRangeId: string) => void
  onUpdateOutputSubRange: (
    rangeId: string,
    outputRangeId: string,
    updates: Partial<OutputRangePrice>
  ) => void
}

export const PriceRangeItem = React.memo<PriceRangeItemProps>(
  function PriceRangeItem({
    range,
    index,
    isExpanded,
    canDelete,
    isFirst,
    isLast,
    isSingle,
    nextRangeId,
    onToggleExpand,
    onRemove,
    onUpdateRange,
    onUpdateMaxTokenWithCascade,
    onEnableOutputRange,
    onAddOutputSubRange,
    onRemoveOutputSubRange,
    onUpdateOutputSubRange,
  }) {
    return (
      <div
        className={cn(
          "rounded-lg border border-border/60 transition-all",
          isExpanded && "bg-background shadow-sm"
        )}
      >
        {/* 区间头部 */}
        <RangeHeader
          index={index}
          rangeId={range.id}
          isExpanded={isExpanded}
          canDelete={canDelete}
          onToggleExpand={() => onToggleExpand(range.id)}
          onRemove={() => onRemove(range.id)}
        />

        {/* 展开内容 */}
        {isExpanded && (
          <div className="border-t border-border/60 p-4 space-y-5">
            {/* 输入Token范围配置 */}
            <RangeInputSection
              range={range}
              isLast={isLast}
              isSingle={isSingle}
              nextRangeId={nextRangeId}
              onUpdateRange={onUpdateRange}
              onUpdateMaxTokenWithCascade={onUpdateMaxTokenWithCascade}
            />
            <StaticPriceSection range={range.inputRangePrice} onUpdateRange={(id, updates) => onUpdateRange(range.id, { inputRangePrice: { ...range.inputRangePrice, ...updates } })} />

            {/* token输出区间开关 */}
            <OutputRangeToggle
              rangeId={range.id}
              enabled={(range.outputRangePrices?.length && range.outputRangePrices?.length > 0) || false}
              onToggle={onEnableOutputRange}
            />
            {(range.outputRangePrices?.length ?? 0) > 0 ? (
              <OutputSubRangeList
                rangeId={range.id}
                outputRanges={range.outputRangePrices ?? []}
                onAddOutputSubRange={onAddOutputSubRange}
                onRemoveOutputSubRange={onRemoveOutputSubRange}
                onUpdateOutputSubRange={onUpdateOutputSubRange}
              />
            ) : null}
          </div>
        )}
      </div>
    )
  }
)
