/**
 * 职责: 渲染静态价格配置表单 (未启用输出区间时显示)
 *
 * 功能:
 * - 显示价格配置标题和单位提示
 * - 渲染 6 个价格输入字段 (输入/输出/图片/缓存)
 * - 处理价格字段更新
 *
 * 设计:
 * - 使用 React.memo 优化,仅在 props 变化时重新渲染
 * - 使用 PriceInput 组件复用输入逻辑
 * - 2×3 网格布局,响应式设计
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较
 * - 父组件应使用 useCallback 包裹回调函数
 */

import React from "react"
import { Label } from "@/components/common/label"
import type { InputRangePrice } from "../types"
import { PriceInput } from "./PriceInput"

interface StaticPriceSectionProps {
  range: InputRangePrice
  onUpdateRange: (id: string, updates: Partial<InputRangePrice>) => void
}

export const StaticPriceSection = React.memo<StaticPriceSectionProps>(
  function StaticPriceSection({ range, onUpdateRange }) {
    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium text-foreground">价格配置</Label>
          <span className="text-xs text-muted-foreground">单位：分/千token</span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {/* 📌 修改点1: 输入价格 - 添加联动逻辑 */}
          <PriceInput
            label="输入价格"
            required
            value={range.input}
            onChange={(v) => {
              const newInput = v ?? 1
              const currentOutput = range.output

              // 业务规则1: 如果输出价格为空,输入价格无限制,且输出价格同步为输入价格
              if (currentOutput === null || currentOutput === undefined) {
                onUpdateRange(range.id, { input: newInput, output: newInput })
                return
              }

              // 业务规则3: 如果输出价格有值,输入价格不能大于输出价格
              if (newInput > currentOutput) {
                // 静默拒绝: 不允许设置,保持原值
                return
              }

              // 正常更新
              onUpdateRange(range.id, { input: newInput })
            }}
          />
          {/* 📌 修改点2: 输出价格 - 添加约束逻辑 */}
          <PriceInput
            label="输出价格"
            required
            value={range.output}
            onChange={(v) => {
              const newOutput = v ?? 1
              const currentInput = range.input

              // 业务规则2: 如果输入价格有值,输出价格不能小于输入价格
              if (currentInput !== null && currentInput !== undefined && newOutput < currentInput) {
                // 静默拒绝: 不允许设置,保持原值
                return
              }

              // 正常更新
              onUpdateRange(range.id, { output: newOutput })
            }}
          />
          <PriceInput
            label="图片输入"
            value={range.imageInput ?? null}
            onChange={(v) => onUpdateRange(range.id, { imageInput: v })}
          />
          <PriceInput
            label="图片输出"
            value={range.imageOutput ?? null}
            onChange={(v) => onUpdateRange(range.id, { imageOutput: v })}
          />
          <PriceInput
            label="缓存读取"
            value={range.cachedRead ?? null}
            onChange={(v) => onUpdateRange(range.id, { cachedRead: v })}
          />
          <PriceInput
            label="缓存创建"
            value={range.cachedCreation ?? null}
            onChange={(v) => onUpdateRange(range.id, { cachedCreation: v })}
          />
        </div>
      </div>
    )
  }
)
