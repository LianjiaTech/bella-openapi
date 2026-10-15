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
import { InputReplacementTextsField } from './components/InputReplacementTextsField'
import { ArrayField } from './components/ArrayField'
import { BatchDiscount } from './components/BatchDiscount'
import { SupplierDiscount } from './components/SupplierDiscount'
import { PriceRangeConfig } from './components/PriceRangeConfig/PriceRangeConfig'
import { ToolPriceConfig } from './components/toolPriceConfig'

const BILLING_MODE_LEGACY_VALUE = '__legacy__'
const BILLING_MODE_OPTIONS = [
  { value: BILLING_MODE_LEGACY_VALUE, label: '历史逻辑（空）' },
  { value: 'per_image', label: 'per_image（按张）' },
  { value: 'token', label: 'token（按 token）' },
  { value: 'mixed', label: 'mixed（按张 + token）' },
]

const VIDEO_BILLING_MODE_OPTIONS = [
  { value: 'token', label: '按token' },
  { value: 'duration', label: '按second' },
  { value: 'minimax', label: 'MiniMax' },
  { value: 'gemini', label: 'Gemini' },
]

const WEB_SEARCH_BILLING_MODE_OPTIONS = [
  { value: 'request', label: 'request（按请求）' },
  { value: 'search_type', label: 'search_type（按搜索类型）' },
]

function getFieldDisplayName(schema: TypeSchema, endpoint?: string): string {
  if (endpoint === '/v1/videos' && schema.code === 'pricePerSecond') {
    return '视频单价（元/秒）'
  }
  return schema.name
}

function getFieldPlaceholder(schema: TypeSchema, displayName: string): string {
  if (schema.code === 'sessionId') {
    return '如：X-Prompt-Cache-Id'
  }
  return `输入 ${displayName}`
}

interface FieldRendererProps {
  schema: TypeSchema
  value: any
  onChange: (value: any) => void
  error?: string
  mode: 'create' | 'edit'
  hideLabel?: boolean
  endpoint?: string
}

export const FieldRenderer = ({
  mode,
  schema,
  value,
  hideLabel = false,
  endpoint,
  onChange,
  error,
}: FieldRendererProps): React.ReactElement => {
  const commonProps = {
    className: `w-full ${error ? 'border-red-600' : ''}`,
  }
  const displayName = getFieldDisplayName(schema, endpoint)

  if (schema.code === 'toolPrices') {
    return <ToolPriceConfig toolPrices={value} onToolPricesChange={onChange} />
  }

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
      if (schema.code === 'billingMode') {
        const isWebSearch = endpoint === '/v1/web/search'
        const options = endpoint === '/v1/videos'
          ? VIDEO_BILLING_MODE_OPTIONS
          : isWebSearch
            ? WEB_SEARCH_BILLING_MODE_OPTIONS
            : BILLING_MODE_OPTIONS
        const selectedValue = value || (endpoint === '/v1/videos'
          ? 'token'
          : isWebSearch
            ? 'request'
            : BILLING_MODE_LEGACY_VALUE)
        return (
          <div className="text-left">
            {hideLabel ? null : <Label className="text-sm font-medium text-gray-700 block mb-1.5">{displayName}</Label>}
            <Select
              value={selectedValue}
              onValueChange={(selected) => onChange(
                endpoint === '/v1/videos' || isWebSearch
                  ? selected
                  : selected === BILLING_MODE_LEGACY_VALUE ? '' : selected
              )}
            >
              <SelectTrigger className={commonProps.className}>
                <SelectValue placeholder={`选择 ${displayName}`} />
              </SelectTrigger>
              <SelectContent>
                {options.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
          </div>
        )
      }
      return (
        <div className="text-left">
          {hideLabel ? null : <Label className="text-sm font-medium text-gray-700 block mb-1.5">{displayName}</Label>}
          <Input
            {...commonProps}
            value={value || ''}
            onChange={(e) => onChange(e.target.value)}
            placeholder={getFieldPlaceholder(schema, displayName)}
          />
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
      )

    case 'number':
      // 职责：处理数字类型字段，为 batchDiscount 提供 0-1 范围限制和默认值 0.5
      if (schema.code === 'batchDiscount') {
        return <BatchDiscount mode={mode} value={value} onChange={(value) => onChange(value)} />
      }
      if (schema.code === 'supplierDiscount') {
        return <SupplierDiscount mode={mode} value={value} onChange={(value) => onChange(value)} />
      }
      return (
        <div className="text-left space-y-1.5">
          {hideLabel ? null : <Label className="text-sm font-medium text-gray-700 block">{displayName}</Label>}
          <Input
            {...commonProps}
            type="number"
            value={value ?? ''}
            onChange={(e) => onChange(e.target.value === '' ? undefined : parseFloat(e.target.value))}
            placeholder={`输入 ${displayName}`}
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
      return <ArrayField mode={mode} schema={schema} value={value} onChange={onChange} error={error} endpoint={endpoint} />

    case 'object':
      // 职责:处理对象类型字段,为 tiers 定价信息提供专用的 PriceInfo 组件
      if (schema.code === 'tiers') {
        return  <PriceRangeConfig mode={mode} ranges={value} onRangesChange={onChange} />
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
                      hideLabel: true,
                      endpoint,
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
      if (schema.code === 'inputReplacementTexts') {
        return <InputReplacementTextsField schema={schema} value={value} onChange={onChange} error={error} />
      }
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
