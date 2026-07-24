/**
 * 职责: 渲染价格区间列表容器
 *
 * 功能:
 * - 显示卡片头部 (标题、描述、添加按钮)
 * - 遍历渲染所有价格区间
 * - 传递所有必要的 props 和回调给子组件
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 使用 Card 组件作为容器
 * - 遍历 normalizedRanges 渲染 PriceRangeItem
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹所有回调函数
 */

import React from "react"
import { Plus } from "lucide-react"
import { Button } from "@/components/common/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/common/card"
import type { PriceRange, OutputRangePrice } from "../types"
import { PriceRangeItem } from "./PriceRangeItem"

interface PriceRangeListProps {
  normalizedRanges: PriceRange[]
  expandedRanges: Set<string>
  onAddRange: () => void
  onToggleExpand: (id: string) => void
  onRemoveRange: (id: string) => void
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

export const PriceRangeList = React.memo<PriceRangeListProps>(
  function PriceRangeList({
    normalizedRanges,
    expandedRanges,
    onAddRange,
    onToggleExpand,
    onRemoveRange,
    onUpdateRange,
    onUpdateMaxTokenWithCascade,
    onEnableOutputRange,
    onAddOutputSubRange,
    onRemoveOutputSubRange,
    onUpdateOutputSubRange,
  }) {
    return (
      <Card className="border-border/60">
        <CardHeader className="pb-4">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <CardTitle className="text-sm font-medium">区间价格列表</CardTitle>
            </div>
            <Button variant="outline" size="sm" onClick={onAddRange} className="gap-1.5">
              <Plus className="h-4 w-4" />
              添加区间
            </Button>
          </div>
        </CardHeader>

        <CardContent className="space-y-4">
          {normalizedRanges.map((range, index) => (
            <PriceRangeItem
              key={`${range.id}-${index}`}
              range={range}
              index={index}
              isExpanded={expandedRanges.has(range.id)}
              canDelete={normalizedRanges.length > 1}
              isFirst={index === 0}
              isLast={index === normalizedRanges.length - 1}
              isSingle={normalizedRanges.length === 1}
              nextRangeId={index < normalizedRanges.length - 1 ? normalizedRanges[index + 1].id : undefined}
              onToggleExpand={onToggleExpand}
              onRemove={onRemoveRange}
              onUpdateRange={onUpdateRange}
              onUpdateMaxTokenWithCascade={onUpdateMaxTokenWithCascade}
              onEnableOutputRange={onEnableOutputRange}
              onAddOutputSubRange={onAddOutputSubRange}
              onRemoveOutputSubRange={onRemoveOutputSubRange}
              onUpdateOutputSubRange={onUpdateOutputSubRange}
            />
          ))}
        </CardContent>
      </Card>
    )
  }
)
