/**
 * 职责: 渲染价格区间的头部 (标题、序号、删除按钮、展开图标)
 *
 * 功能:
 * - 显示区间序号和标题
 * - 显示删除按钮 (可选)
 * - 显示展开/收起图标
 * - 处理点击展开/收起事件
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 头部可点击,触发折叠切换
 * - 删除按钮阻止事件冒泡
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹回调函数
 */

import React from "react"
import { Trash2, ChevronDown, ChevronUp } from "lucide-react"
import { Button } from "@/components/common/button"

interface RangeHeaderProps {
  index: number
  rangeId: string
  isExpanded: boolean
  canDelete: boolean
  onToggleExpand: () => void
  onRemove: () => void
}

export const RangeHeader = React.memo<RangeHeaderProps>(function RangeHeader({
  index,
  isExpanded,
  canDelete,
  onToggleExpand,
  onRemove,
}) {
  return (
    <div
      className="flex items-center justify-between p-4 cursor-pointer hover:bg-muted/30 transition-colors rounded-t-lg"
      onClick={onToggleExpand}
    >
      <div className="flex items-center gap-3">
        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs font-medium text-primary">
          {index + 1}
        </div>
        <span className="font-medium text-sm">区间 {index + 1}</span>
      </div>
      <div className="flex items-center gap-2">
        {canDelete && (
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-destructive"
            onClick={(e) => {
              e.stopPropagation()
              onRemove()
            }}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        )}
        {isExpanded ? (
          <ChevronUp className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        )}
      </div>
    </div>
  )
})
