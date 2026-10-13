import { Input } from '@/components/common/input'
import { Label } from '@/components/common/label'
import { Switch } from '@/components/common/switch'
import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react'

const getInitialValues = (mode: 'create' | 'edit', value: number) => {
  if (mode === 'create') {
    return {
      enableDiscount: false,
      supplierDiscount: 1
    }
  }
  return {
    enableDiscount: value < 1,
    supplierDiscount: value
  }
}

export const SupplierDiscount = ({ mode, value, onChange }: { mode: 'create' | 'edit', value: number, onChange: (value: number) => void }) => {
  const initialValues = getInitialValues(mode, value)
  const [enableDiscount, setEnableDiscount] = useState(initialValues.enableDiscount)
  const lastDiscountRef = useRef<number>(value < 1 ? value : 0.5)

  const displayValue = useMemo(() => value, [value])

  const handleDiscountChange = useCallback((newValue: number) => {
    onChange(newValue)
  }, [onChange])

  useEffect(() => {
    if (mode === 'create') {
      onChange(initialValues.supplierDiscount)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // edit 模式下，当 value 从外部变化（切换编辑对象）时同步 enableDiscount 和 lastDiscountRef
  useEffect(() => {
    if (mode === 'edit') {
      const hasDiscount = typeof value === 'number' && value < 1
      setEnableDiscount(hasDiscount)
      if (hasDiscount) {
        lastDiscountRef.current = value
      }
    }
  }, [value, mode])

  return (
    <div className="rounded-lg border border-border/60 bg-muted/30 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Label
            htmlFor="enable-cost-discount"
            className="text-sm font-medium cursor-pointer"
          >
            供应商折扣
          </Label>
        </div>
        <Switch
          id="enable-cost-discount"
          checked={enableDiscount}
          onCheckedChange={(checked) => {
            setEnableDiscount(checked)
            if (checked) {
              handleDiscountChange(value < 1 ? value : lastDiscountRef.current)
            } else {
              if (value < 1) lastDiscountRef.current = value
              handleDiscountChange(1)
            }
          }}
        />
      </div>

      {enableDiscount && (
        <div className="pt-2 flex items-center gap-2">
          <Input
            id="cost-discount"
            type="number"
            min={0.1}
            max={1}
            step={0.1}
            value={displayValue ?? ''}
            onChange={(e) => handleDiscountChange(Number(e.target.value))}
            className="max-w-[200px]"
            placeholder="请输入折扣比例"
          />
          <span className="text-xs text-muted-foreground mt-1.5">
            {typeof displayValue === 'number' && !isNaN(displayValue)
              ? `${parseFloat((displayValue * 10).toFixed(2))}折`
              : ''}
          </span>
        </div>
      )}
    </div>
  )
}
