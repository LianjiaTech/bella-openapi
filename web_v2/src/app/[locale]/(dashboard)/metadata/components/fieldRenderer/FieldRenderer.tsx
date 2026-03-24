import React from 'react'
import { Input } from '@/components/common/input'
import { Switch } from '@/components/common/switch'
import { Label } from '@/components/common/label'
import { Card, CardContent } from '@/components/common/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/common/select'
import type { TypeSchema } from '@/lib/types/metadata'
import { MapField } from './components/MapField'
import { ArrayField } from './components/ArrayField'
import { BatchDiscount } from './components/BatchDiscount'
import { PriceRangeConfig } from './components/PriceRangeConfig/PriceRangeConfig'
import { ToolPriceConfig } from './components/toolPriceConfig'

interface FieldRendererProps {
  schema: TypeSchema
  value: any
  onChange: (value: any) => void
  error?: string
  mode: 'create' | 'edit'
}

export const FieldRenderer = ({
  mode,
  schema,
  value,
  onChange,
  error,
}: FieldRendererProps): React.ReactElement => {
  const commonProps = {
    className: `w-full ${error ? 'border-red-600' : ''}`,
  }
  console.log('FieldRenderer', value)
  switch (schema.valueType) {
    case 'enum':
      return (
        <div className="text-left">
          <Select value={value || ''} onValueChange={(selectedValue) => onChange(selectedValue)}>
            <SelectTrigger className={commonProps.className}>
              <SelectValue placeholder={`选择 ${schema.name}`} />
            </SelectTrigger>
            <SelectContent>
              {schema.selections?.map((option) => (
                <SelectItem key={option} value={option}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'string':
      return (
        <div className="text-left">
          <Input
            {...commonProps}
            value={value || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={`输入 ${schema.name}`}
          />
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'number':
      // 职责：处理数字类型字段，为 batchDiscount 提供 0-1 范围限制和默认值 0.5
      if (schema.code === 'batchDiscount') {
        return <BatchDiscount mode={mode} value={value} onChange={(value) => onChange(value)} />
      }
      return (
        <div className="text-left">
          <Input
            {...commonProps}
            type="number"
            value={value || ''}
            onChange={(e) => onChange(parseFloat(e.target.value))}
            placeholder={`输入 ${schema.name}`}
          />
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'bool':
      return (
        <div className="text-left">
          <div className="flex items-center justify-start">
            <Switch
              checked={value || false}
              onCheckedChange={(checked) => onChange(checked)}
              className="data-[state=checked]:bg-blue-500 data-[state=unchecked]:bg-gray-200"
            />
          </div>
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'array':
      return <ArrayField mode={mode} schema={schema} value={value} onChange={onChange} error={error} />

    case 'object':
      // 职责:处理对象类型字段,为 tiers 定价信息提供专用的 PriceInfo 组件
      if (schema.code === 'tiers') {
        return  <PriceRangeConfig mode={mode} ranges={value} onRangesChange={onChange} />
      }
      if(schema.code === 'toolPrices') {
        return <ToolPriceConfig toolPrices={value} onToolPricesChange={onChange} />
      }
      return (
        <div className="text-left">
          <Card className="border border-gray-200 bg-gray-50 shadow-sm rounded-md">
            <CardContent className="p-4">
              <div className="space-y-4">
                {schema.child?.params.map((param) => (
                  <div key={param.code} className="space-y-2">
                    <Label className="text-sm font-medium text-gray-700 block">{param.name}</Label>
                    {FieldRenderer({
                      mode,
                      schema: param,
                      value: value?.[param.code],
                      onChange: (nestedValue) => {
                        const newValue = { ...value, [param.code]: nestedValue }
                        onChange(newValue)
                      },
                    })}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'map':
      return <MapField schema={schema} value={value} onChange={onChange} error={error} />

    default:
      return (
        <div className="text-left">
          <Input
            {...commonProps}
            value={value || ''}
            onChange={(e) => onChange(e.target.value)}
          />
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )
  }
}
