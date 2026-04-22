/**
 * PriceCard 组件
 *
 * 职责：
 * - 通用的价格展示卡片组件
 * - 支持默认和高亮两种样式变体
 * - 高亮模式下显示"推荐"标签
 * - 展示标签、价格和单位信息
 *
 * 设计说明：
 * - 使用 React.memo 优化性能，避免不必要的 re-render
 * - 仅在 props (label, price, unit, variant) 变化时才重新渲染
 * - 使用简单的基础类型 props，避免引用类型导致的性能问题
 * - 样式基于 variant 属性动态切换，支持主题色
 *
 * 如何避免 re-render：
 * - 使用 React.memo 包裹组件
 * - Props 均为基础类型 (string, number)
 * - 不依赖外部状态或 context
 * - 父组件应避免传递内联对象或函数
 */

import React from "react"

interface PriceCardProps {
  /** 价格标签（如：低清、中清、高清） */
  label: string
  /** 价格数值 */
  price: number
  /** 价格单位（如：每张、千tokens） */
  unit: string
  /** 样式变体：default 为默认样式，highlight 为高亮推荐样式 */
  variant?: "default" | "highlight"
}

export const PriceCard = React.memo<PriceCardProps>(({
  label,
  price,
  unit,
  variant = "default"
}) => {
  const isHighlight = variant === "highlight"

  return (
    <div className={`
      relative rounded-xl p-4 text-center transition-all
      ${isHighlight
        ? "bg-primary text-primary-foreground shadow-lg ring-2 ring-primary/20"
        : "bg-muted/50 hover:bg-muted"
      }
    `}>
      {isHighlight && (
        <div className="absolute -top-2 left-1/2 -translate-x-1/2">
          <span className="bg-primary text-primary-foreground text-[10px] font-medium px-2 py-0.5 rounded-full">
            推荐
          </span>
        </div>
      )}
      <div className={`text-xs font-medium mb-2 ${isHighlight ? "text-primary-foreground/80" : "text-muted-foreground"}`}>
        {label}
      </div>
      <div className={`text-xs font-bold ${isHighlight ? "text-primary-foreground" : "text-foreground"}`}>
        ¥{price}
      </div>
      <div className={`text-xs mt-1 ${isHighlight ? "text-primary-foreground/60" : "text-muted-foreground"}`}>
        {unit}
      </div>
    </div>
  )
})

PriceCard.displayName = "PriceCard"
  