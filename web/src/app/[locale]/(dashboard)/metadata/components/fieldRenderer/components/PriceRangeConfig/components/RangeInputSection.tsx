/**
 * 职责: 渲染输入 Token 范围配置区块
 *
 * 功能:
 * - 配置 minToken / maxToken 范围
 * - 显示格式化后的区间提示
 * - 处理区间联动逻辑 (修改当前区间的 maxToken 时,自动更新下一区间的 minToken)
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 边界处理: 第一个区间的 minToken 禁用,最后一个区间的 maxToken 显示 +∞
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹回调函数
 */

import React from "react"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import type { PriceRange } from "../types"
import { formatTokenValue } from "../constants"

interface RangeInputSectionProps {
  range: PriceRange
  isLast: boolean
  isSingle: boolean
  nextRangeId?: string
  onUpdateRange: (id: string, updates: Partial<PriceRange>) => void
  onUpdateMaxTokenWithCascade: (currentRangeId: string, nextRangeId: string | undefined, newMaxToken: number) => void
}

export const RangeInputSection = React.memo<RangeInputSectionProps>(
  function RangeInputSection({
    range,
    isLast,
    isSingle,
    nextRangeId,
    onUpdateRange,
    onUpdateMaxTokenWithCascade,
  }) {
    return (
      <div className="space-y-3">
        <Label className="text-sm font-medium text-foreground">输入Token范围</Label>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">最小值 (minToken)</Label>
            <Input
              type="number"
              min={0}
              value={range.inputRangePrice?.minToken}
              onChange={(e) => onUpdateRange(range.id, { inputRangePrice: { ...range.inputRangePrice, minToken: Number(e.target.value) } })}
              disabled={true}
              className="bg-muted cursor-not-allowed"
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">最大值 (maxToken)</Label>
            {isSingle || isLast ? (
              <Input value="+∞" disabled className="bg-muted cursor-not-allowed" />
            ) : (
              <Input
                type="number"
                min={0}
                value={range.inputRangePrice.maxToken}
                onChange={(e) => {
                  const newMaxToken = Number(e.target.value)
                  // 📌 修复: 使用专用方法一次性更新当前区间的 maxToken 和下一个区间的 minToken，避免连续调用导致状态覆盖
                  onUpdateMaxTokenWithCascade(range.id, nextRangeId, newMaxToken)
                }}
              />
            )}
          </div>
        </div>
        <p className="text-xs text-muted-foreground">
          区间: ({formatTokenValue(range.inputRangePrice.minToken)}, {formatTokenValue(range.inputRangePrice.maxToken)})
        </p>
      </div>
    )
  }
)
