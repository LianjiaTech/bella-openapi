/**
 * 职责: 渲染价格输入表单项
 *
 * 功能:
 * - 支持必填/可选字段配置
 * - 处理数值输入与 null 值转换
 * - 显示字段标签和必填标记
 *
 * 设计:
 * - 使用 React.memo 避免父组件更新时的无效 re-render
 * - 通过 value === null 判断是否显示 placeholder
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较,仅当 props 变化时才重新渲染
 * - onChange 回调应由父组件使用 useCallback 包裹
 */

import React from "react"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import { cn } from "@/lib/utils"

interface PriceInputProps {
  label: string
  required?: boolean
  value: number | null | undefined
  onChange: (value: number | null | undefined) => void
}

export const PriceInput = React.memo<PriceInputProps>(
  function PriceInput({ label, required, value, onChange }) {
    return (
      <div className="space-y-1.5">
        <Label className="text-xs text-muted-foreground">
          {label}
          {required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        <Input
          type="number"
          min={1}
          value={value ?? ""}
          onChange={(e) => {
            const val = e.target.value
            const numVal = Number(val)
            // 📌 最小值约束: 必填字段不能输入0或负数,可选字段允许为空
            if (val === "") {
              onChange(null)
            } else if (required && numVal < 1) {
              // 静默拒绝: 必填字段最小值为1
              return
            } else {
              onChange(numVal)
            }
          }}
          placeholder={required ? undefined : "可选"}
          className={cn(!required && value === null && "text-muted-foreground")}
        />
      </div>
    )
  }
)
