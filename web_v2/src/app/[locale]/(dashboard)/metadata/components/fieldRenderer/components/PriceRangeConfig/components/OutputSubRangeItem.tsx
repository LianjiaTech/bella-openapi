/**
 * 职责: 渲染单个输出子区间的配置表单
 *
 * 功能:
 * - 显示输出区间序号和删除按钮
 * - 配置 minToken / maxToken 范围
 * - 配置价格字段 (输入/输出/图片/缓存)
 * - 处理价格输入校验错误提示
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 边界处理: 第一个区间的 minToken 禁用,最后一个区间的 maxToken 显示 +∞
 * - 价格配置使用 PriceInput 组件复用
 * - 使用 sonner toast 显示校验错误
 *
 * 业务规则:
 * - 输入/输出价格默认值为 null
 * - 输入价格 blur 时,如果输出价格为空,输出价格自动同步为输入价格
 * - 输入价格 blur 时,如果输入价格 > 输出价格,输出价格自动提升为输入价格
 * - 所有价格字段不能输入 0 或负数
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹所有回调函数
 */

import React, { useCallback } from "react"
import { Trash2 } from "lucide-react"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
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
    // 校验错误处理
    const handleValidationError = useCallback((message: string) => {
      toast.error(message)
    }, [])

    // 📌 输入价格变更处理 (blur 时触发)
    const handleInputChange = useCallback(
      (v: number | null | undefined) => {
        const newInput = v ?? null
        const currentOutput = outputRange.output

        // 业务规则3: 输出价格无值时,输入价格 blur 时同步到输出价格
        if (newInput !== null && currentOutput === null) {
          onUpdate({ input: newInput, output: newInput })
          return
        }

        // 业务规则2.1: 输入价格大于输出价格时,输出价格变成输入价格
        if (newInput !== null && currentOutput !== null && newInput > currentOutput) {
          onUpdate({ input: newInput, output: newInput })
          toast.info("输入价格大于输出价格，输出价格已自动调整")
        } else {
          onUpdate({ input: newInput })
        }
      },
      [outputRange.output, onUpdate]
    )

    // 📌 输出价格变更处理 (blur 时触发)
    const handleOutputChange = useCallback(
      (v: number | null | undefined) => {
        const newOutput = v ?? null
        onUpdate({ output: newOutput })
      },
      [onUpdate]
    )

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
            {/* 📌 输入价格 - 支持任意非负正数 */}
            <PriceInput
              label="输入价格"
              required
              value={outputRange.input}
              onChange={handleInputChange}
              onValidationError={handleValidationError}
            />
            {/* 📌 输出价格 - 支持任意非负正数 */}
            <PriceInput
              label="输出价格"
              required
              value={outputRange.output}
              onChange={handleOutputChange}
              onValidationError={handleValidationError}
            />
            <PriceInput
              label="图片输入"
              value={outputRange.imageInput ?? null}
              onChange={(v) => onUpdate({ imageInput: v ?? undefined })}
              onValidationError={handleValidationError}
            />
            <PriceInput
              label="图片输出"
              value={outputRange.imageOutput ?? null}
              onChange={(v) => onUpdate({ imageOutput: v ?? undefined })}
              onValidationError={handleValidationError}
            />
            <PriceInput
              label="缓存读取"
              value={outputRange.cachedRead ?? null}
              onChange={(v) => onUpdate({ cachedRead: v ?? undefined })}
              onValidationError={handleValidationError}
            />
            <PriceInput
              label="缓存创建"
              value={outputRange.cachedCreation ?? null}
              onChange={(v) => onUpdate({ cachedCreation: v ?? undefined })}
              onValidationError={handleValidationError}
            />
          </div>
          <p className="text-xs text-muted-foreground text-right">
            单位：元/百万token
            <span className="ml-2 text-red-500">注：单位已由 分/千token 转变，请使用 元/百万token</span>
          </p>
        </div>
      </div>
    )
  }
)
