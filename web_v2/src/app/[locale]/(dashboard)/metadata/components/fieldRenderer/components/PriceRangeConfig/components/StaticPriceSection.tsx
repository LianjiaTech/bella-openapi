/**
 * 职责: 渲染静态价格配置表单 (未启用输出区间时显示)
 *
 * 功能:
 * - 显示价格配置标题和单位提示
 * - 渲染 6 个价格输入字段 (输入/输出/图片/缓存)
 * - 处理价格字段更新和联动逻辑
 * - 处理价格输入校验错误提示
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 使用 PriceInput 组件复用输入逻辑
 * - 2×3 网格布局,响应式设计
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
 * - 父组件应使用 useCallback 包裹回调函数
 */

import React, { useCallback } from "react"
import { Label } from "@/components/common/label"
import { toast } from "sonner"
import type { InputRangePrice } from "../types"
import { PriceInput } from "./PriceInput"

interface StaticPriceSectionProps {
  range: InputRangePrice
  onUpdateRange: (id: string, updates: Partial<InputRangePrice>) => void
}

export const StaticPriceSection = React.memo<StaticPriceSectionProps>(
  function StaticPriceSection({ range, onUpdateRange }) {
    // 校验错误处理
    const handleValidationError = useCallback((message: string) => {
      toast.error(message)
    }, [])

    // 📌 输入价格变更处理 (blur 时触发)
    const handleInputChange = useCallback(
      (v: number | null | undefined) => {
        const newInput = v ?? null
        const currentOutput = range.output

        // 业务规则3: 输出价格无值时,输入价格 blur 时同步到输出价格
        if (newInput !== null && currentOutput === null) {
          onUpdateRange(range.id, { input: newInput, output: newInput })
          return
        }

        // 业务规则2.1: 输入价格大于输出价格时,输出价格变成输入价格
        if (newInput !== null && currentOutput !== null && newInput > currentOutput) {
          onUpdateRange(range.id, { input: newInput, output: newInput })
          toast.info("输入价格大于输出价格，输出价格已自动调整")
        } else {
          onUpdateRange(range.id, { input: newInput })
        }
      },
      [range.id, range.output, onUpdateRange]
    )

    // 📌 输出价格变更处理 (blur 时触发)
    const handleOutputChange = useCallback(
      (v: number | null | undefined) => {
        const newOutput = v ?? null

        // 业务规则: 输出价格无约束,允许小于输入价格
        // (在输入价格 blur 时会自动处理)
        onUpdateRange(range.id, { output: newOutput })
      },
      [range.id, onUpdateRange]
    )

    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium text-foreground">价格配置</Label>
          <span className="text-xs text-muted-foreground">
            单位：元/百万token
            <span className="ml-2 text-red-500">注：单位已由 分/千token 转变，请使用 元/百万token</span>
          </span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {/* 📌 输入价格 - 支持任意非负正数 */}
          <PriceInput
            label="输入价格"
            required
            value={range.input}
            onChange={handleInputChange}
            onValidationError={handleValidationError}
          />
          {/* 📌 输出价格 - 支持任意非负正数 */}
          <PriceInput
            label="输出价格"
            required
            value={range.output}
            onChange={handleOutputChange}
            onValidationError={handleValidationError}
          />
          <PriceInput
            label="图片输入"
            value={range.imageInput ?? null}
            onChange={(v) => onUpdateRange(range.id, { imageInput: v })}
            onValidationError={handleValidationError}
          />
          <PriceInput
            label="图片输出"
            value={range.imageOutput ?? null}
            onChange={(v) => onUpdateRange(range.id, { imageOutput: v })}
            onValidationError={handleValidationError}
          />
          <PriceInput
            label="缓存读取"
            value={range.cachedRead ?? null}
            onChange={(v) => onUpdateRange(range.id, { cachedRead: v })}
            onValidationError={handleValidationError}
          />
          <PriceInput
            label="缓存创建"
            value={range.cachedCreation ?? null}
            onChange={(v) => onUpdateRange(range.id, { cachedCreation: v })}
            onValidationError={handleValidationError}
          />
        </div>
      </div>
    )
  }
)
