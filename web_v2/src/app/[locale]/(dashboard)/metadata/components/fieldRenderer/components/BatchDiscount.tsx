import { Input } from '@/components/common/input'
import { Label } from '@/components/common/label'
import { Switch } from '@/components/common/switch'
import React, { useState, useMemo, useCallback, useEffect } from 'react'

/**
 * 职责：根据 mode 获取初始化状态
 * 设计说明：
 * - create 模式：不启用折扣（enableDiscount = false），原价（batchDiscount = 1）
 * - edit 模式：根据 value 判断是否启用折扣（value < 1 表示有折扣），使用传入的 value
 */
const getInitialValues = (mode: 'create' | 'edit', value: number) => {
  if (mode === 'create') {
    return {
      enableDiscount: false,
      batchDiscount: 1
    }
  }
  return {
    enableDiscount: value < 1,
    batchDiscount: value
  }
}

/**
 * 职责：批量折扣配置组件，支持 0.5-1.0 范围的折扣设置
 * 设计说明：
 * - 集中式初始化逻辑，通过 getInitialValues 函数处理不同模式
 * - 使用 useCallback 缓存事件处理函数，避免子组件 re-render
 */
export const BatchDiscount = ({ mode, value, onChange }: { mode: 'create' | 'edit', value: number, onChange: (value: number) => void }) => {
  console.log('BatchDiscount', mode, value)
  const initialValues = getInitialValues(mode, value)
  const [enableDiscount, setEnableDiscount] = useState(initialValues.enableDiscount)

  // 职责：缓存显示值（value 必定 >= 0.5，无需纠正）
  const displayValue = useMemo(() => value, [value])

  // 职责：处理折扣值变化，使用 useCallback 避免 re-render
  const handleDiscountChange = useCallback((newValue: number) => {
    onChange(newValue)
  }, [onChange])

  // 职责：create 模式下初始化父组件状态
  // 设计说明：仅在组件挂载时执行一次，将初始值 1 同步到父组件
  useEffect(() => {
    if (mode === 'create') {
      onChange(initialValues.batchDiscount)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="rounded-lg border border-border/60 bg-muted/30 p-4 space-y-3">
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <Label
          htmlFor="enable-discount"
          className="text-sm font-medium cursor-pointer"
        >
          批量折扣
        </Label>
      </div>
      <Switch
        id="enable-discount"
        checked={enableDiscount}
        onCheckedChange={(checked) => {
          setEnableDiscount(checked)
          if (checked) {
            // 开启折扣：设置为默认值 0.5（5折）
            handleDiscountChange(0.5)
          } else {
            // 关闭折扣：重置为 1（原价）
            handleDiscountChange(1)
          }
        }}
      />
    </div>
    
    {enableDiscount && (
      <div className="pt-2 flex items-center gap-2">
        <Input
          id="batch-discount"
          type="number"
          min={0.5}
          max={1}
          step={0.1}
          value={displayValue}
          onChange={(e) =>
            handleDiscountChange(Number(e.target.value))
          }
          className="max-w-[200px]"
          placeholder="请输入折扣比例"
        />
        <span className="text-xs text-muted-foreground mt-1.5">
          {displayValue * 10}折
        </span>
      </div>
    )}
  </div>
  )
}