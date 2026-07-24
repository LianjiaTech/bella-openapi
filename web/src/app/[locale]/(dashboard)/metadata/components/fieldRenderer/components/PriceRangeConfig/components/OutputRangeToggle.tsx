/**
 * 职责: 渲染"启用输出Token区间"开关
 *
 * 功能:
 * - 显示开关标签和 Switch 组件
 * - 处理开关切换事件
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 简单的展示组件,无复杂逻辑
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹回调函数
 */

import React from "react"
import { Switch } from "@/components/common/switch"
import { Label } from "@/components/common/label"

interface OutputRangeToggleProps {
  rangeId: string
  enabled: boolean
  onToggle: (rangeId: string, enabled: boolean) => void
}

export const OutputRangeToggle = React.memo<OutputRangeToggleProps>(
  function OutputRangeToggle({ rangeId, enabled, onToggle }) {
    return (
      <div className="flex items-center justify-between rounded-lg border border-dashed border-border/60 p-3 bg-muted/20">
        <Label className="text-sm font-medium cursor-pointer">启用输出Token区间</Label>
        <Switch checked={enabled} onCheckedChange={(checked) => onToggle(rangeId, checked)} />
      </div>
    )
  }
)
