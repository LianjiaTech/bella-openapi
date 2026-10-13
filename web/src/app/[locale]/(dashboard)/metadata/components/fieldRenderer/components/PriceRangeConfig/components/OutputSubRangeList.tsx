/**
 * 职责: 渲染输出子区间列表容器
 *
 * 功能:
 * - 显示标题和"添加输出区间"按钮
 * - 遍历渲染所有输出子区间
 * - 协调子区间的更新和删除操作
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 将列表操作代理给父组件的 hook 方法
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹所有回调函数
 */

import React from "react"
import { Plus } from "lucide-react"
import { Button } from "@/components/common/button"
import { Label } from "@/components/common/label"
import type { OutputRangePrice } from "../types"
import { OutputSubRangeItem } from "./OutputSubRangeItem"

interface OutputSubRangeListProps {
  rangeId: string
  outputRanges: OutputRangePrice[]
  onAddOutputSubRange: (rangeId: string) => void
  onRemoveOutputSubRange: (rangeId: string, outputRangeId: string) => void
  onUpdateOutputSubRange: (
    rangeId: string,
    outputRangeId: string,
    updates: Partial<OutputRangePrice>
  ) => void
}

export const OutputSubRangeList = React.memo<OutputSubRangeListProps>(
  function OutputSubRangeList({
    rangeId,
    outputRanges,
    onAddOutputSubRange,
    onRemoveOutputSubRange,
    onUpdateOutputSubRange,
  }) {
    return (
      <div className="space-y-4">
        {/* 头部: 标题 + 添加按钮 */}
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium text-foreground">输出Token区间</Label>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onAddOutputSubRange(rangeId)}
            className="gap-1.5 h-8"
          >
            <Plus className="h-3.5 w-3.5" />
            添加输出区间
          </Button>
        </div>

        {/* 输出子区间列表 */}
        <div className="space-y-4">
          {outputRanges.map((outputRange, outputIndex) => (
            <OutputSubRangeItem
              key={outputRange.id}
              outputRange={outputRange}
              outputIndex={outputIndex}
              isFirst={outputIndex === 0}
              isLast={outputIndex === outputRanges.length - 1}
              isSingle={outputRanges.length === 1}
              onUpdate={(updates) =>
                onUpdateOutputSubRange(rangeId, outputRange.id, updates)
              }
              onRemove={() => onRemoveOutputSubRange(rangeId, outputRange.id)}
            />
          ))}
        </div>
      </div>
    )
  }
)
