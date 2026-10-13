/**
 * 职责: 渲染价格输入表单项
 *
 * 功能:
 * - 支持必填/可选字段配置
 * - 处理数值输入与 null 值转换
 * - 显示字段标签和必填标记
 * - blur 时校验非负正数规则并触发回调
 *
 * 设计:
 * - 使用 React.memo 避免父组件更新时的无效 re-render
 * - 通过 value === null 判断是否显示 placeholder
 * - 使用 useState 管理临时输入状态,blur 时提交
 *
 * 避免 re-render:
 * - 使用 memo 进行浅比较,仅当 props 变化时才重新渲染
 * - onChange/onBlur 回调应由父组件使用 useCallback 包裹
 */

import React, { useState, useEffect } from "react"
import { Input } from "@/components/common/input"
import { Label } from "@/components/common/label"
import { cn } from "@/lib/utils"

interface PriceInputProps {
  label: string
  required?: boolean
  value: number | null | undefined
  onChange: (value: number | null | undefined) => void
  onValidationError?: (message: string) => void
}

export const PriceInput = React.memo<PriceInputProps>(
  function PriceInput({ label, required, value, onChange, onValidationError }) {
    // 临时输入状态,允许用户在 focus 时输入任意值
    const [inputValue, setInputValue] = useState<string>("")

    // 同步外部 value 到内部 inputValue
    useEffect(() => {
      setInputValue(value !== null && value !== undefined ? String(value) : "")
    }, [value])

    const handleBlur = () => {
      const val = inputValue.trim()
      const numVal = Number(val)

      // 空值处理
      if (val === "") {
        onChange(null)
        return
      }

      // 📌 业务规则: 不能输入0或负数
      if (numVal <= 0 || isNaN(numVal)) {
        onChange(null)
        setInputValue("")
        onValidationError?.("不能输入0或负数")
        return
      }

      // 正常更新
      onChange(numVal)
    }

    return (
      <div className="space-y-1.5">
        <Label className="text-xs text-muted-foreground">
          {label}
          {required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        <Input
          type="number"
          min={0}
          step="any"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onBlur={handleBlur}
          placeholder={required ? undefined : "可选"}
          className={cn(!required && value === null && "text-muted-foreground")}
        />
      </div>
    )
  }
)
