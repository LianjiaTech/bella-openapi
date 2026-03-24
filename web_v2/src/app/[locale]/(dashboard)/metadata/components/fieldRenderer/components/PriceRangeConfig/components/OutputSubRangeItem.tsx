/**
 * 职责: 渲染单个输出子区间的配置表单
 *
 * 功能:
 * - 显示输出区间序号和删除按钮
 * - 配置 minToken / maxToken 范围
 * - 配置价格字段 (输入/输出/图片/缓存)
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 边界处理: 第一个区间的 minToken 禁用,最后一个区间的 maxToken 显示 +∞
 * - 价格配置使用 PriceInput 组件复用
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹所有回调函数
 */

import React from "react"
import { Trash2 } from "lucide-react"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import { cn } from "@/lib/utils"
import type { OutputRangePrice } from "../types"
import { formatTokenValue, MAX_TOKEN_VALUE } from "../constants"
import { PriceInput } from "./PriceInput"

interface OutputSubRangeItemProps {
  outputRange: OutputRangePrice
  outputIndex: number
  isFirst: boolean
  isLast: boolean
  isSingle: boolean
  onUpdate: (updates: Partial<OutputRangePrice>) => void
  onRemove: () => void
}

export const OutputSubRangeItem = React.memo<OutputSubRangeItemProps>(
  function OutputSubRangeItem({
    outputRange,
    outputIndex,
    isFirst,
    isLast,
    isSingle,
    onUpdate,
    onRemove,
  }) {
    return (
      <div className="rounded-lg border border-border/40 bg-muted/10 p-4 space-y-4">
        {/* 头部: 标题 + 删除按钮 */}
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium">输出区间 {outputIndex + 1}</span>
          {!isSingle && (
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-muted-foreground hover:text-destructive"
              onClick={onRemove}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          )}
        </div>

        {/* Token 范围配置 */}
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">最小值</Label>
            <Input
              type="number"
              min={0}
              value={outputRange.minToken}
              onChange={(e) =>
                onUpdate({
                  minToken: Number(e.target.value),
                })
              }
              disabled={isSingle || !isFirst}
              className={cn(
                (isSingle || !isFirst) && "bg-muted cursor-not-allowed"
              )}
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">最大值</Label>
            {isSingle || isLast ? (
              <Input value="+∞" disabled className="bg-muted cursor-not-allowed" />
            ) : (
              <Input
                type="number"
                min={0}
                value={outputRange.maxToken}
                onChange={(e) =>
                  onUpdate({
                    maxToken: Number(e.target.value),
                  })
                }
              />
            )}
          </div>
        </div>

        {/* Token 范围显示 */}
        <p className="text-xs text-muted-foreground">
          ({formatTokenValue(outputRange.minToken)},{" "}
          {formatTokenValue(outputRange.maxToken)})
        </p>

        {/* 价格配置 */}
        <div className="space-y-2">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {/* 📌 修改点1: 输入价格 - 添加联动逻辑 */}
            <PriceInput
              label="输入价格"
              required
              value={outputRange.input}
              onChange={(v) => {
                const newInput = (v ?? 1) as number
                const currentOutput = outputRange.output

                // 业务规则1: 如果输出价格为空,输入价格无限制,且输出价格同步为输入价格
                if (currentOutput === null || currentOutput === undefined) {
                  onUpdate({ input: newInput, output: newInput })
                  return
                }

                // 业务规则3: 如果输出价格有值,输入价格不能大于输出价格
                if (newInput > currentOutput) {
                  // 静默拒绝: 不允许设置,保持原值
                  return
                }

                // 正常更新
                onUpdate({ input: newInput })
              }}
            />
            {/* 📌 修改点2: 输出价格 - 添加约束逻辑 */}
            <PriceInput
              label="输出价格"
              required
              value={outputRange.output}
              onChange={(v) => {
                const newOutput = (v ?? 1) as number
                const currentInput = outputRange.input

                // 业务规则2: 如果输入价格有值,输出价格不能小于输入价格
                if (currentInput !== null && currentInput !== undefined && newOutput < currentInput) {
                  // 静默拒绝: 不允许设置,保持原值
                  return
                }

                // 正常更新
                onUpdate({ output: newOutput })
              }}
            />
            <PriceInput
              label="图片输入"
              value={outputRange.imageInput}
              onChange={(v) => onUpdate({ imageInput: v === null ? undefined : (v as number) })}
            />
            <PriceInput
              label="图片输出"
              value={outputRange.imageOutput}
              onChange={(v) => onUpdate({ imageOutput: v === null ? undefined : (v as number) })}
            />
            <PriceInput
              label="缓存读取"
              value={outputRange.cachedRead}
              onChange={(v) => onUpdate({ cachedRead: v === null ? undefined : (v as number) })}
            />
            <PriceInput
              label="缓存创建"
              value={outputRange.cachedCreation}
              onChange={(v) => onUpdate({ cachedCreation: v === null ? undefined : (v as number) })}
            />
          </div>
          <p className="text-xs text-muted-foreground text-right">单位：分/千token</p>
        </div>
      </div>
    )
  }
)
