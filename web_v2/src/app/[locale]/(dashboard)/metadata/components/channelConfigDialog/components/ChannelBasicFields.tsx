import { Label } from '@/components/common/label'
import { Input } from '@/components/common/input'
import { Switch } from '@/components/common/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import { PRIORITY_OPTIONS, DATA_DESTINATION_OPTIONS } from '../constants'
import type { ChannelFormData, FormErrors, DialogMode, ProtocolOption } from '../types'

interface ChannelBasicFieldsProps {
  formData: ChannelFormData
  errors: FormErrors
  mode: DialogMode
  protocols: ProtocolOption[]
  protocolsLoading: boolean
  protocolsError: string | null
  onFieldChange: (field: keyof ChannelFormData, value: any) => void
  onProtocolRetry?: () => void
}

/**
 * 渠道基础字段组件
 * 包含：试用开关、数据流向、优先级、协议、供应商、URL 等基础信息
 */
export function ChannelBasicFields({
  formData,
  errors,
  mode,
  protocols,
  protocolsLoading,
  protocolsError,
  onFieldChange,
  onProtocolRetry,
}: ChannelBasicFieldsProps) {
  return (
    <div className="space-y-4">
      {/* 是否支持试用 */}
      <div className="flex items-center justify-between">
        <div className="space-y-0.5">
          <Label htmlFor="trialEnabled" className="text-sm font-normal">
            是否支持试用
          </Label>
          <p className="text-xs text-muted-foreground">
            开启后,该渠道将支持试用功能
          </p>
        </div>
        <Switch
          id="trialEnabled"
          checked={formData.trialEnabled === 1}
          onCheckedChange={(checked) =>
            onFieldChange('trialEnabled', checked ? 1 : 0)
          }
        />
      </div>

      {/* 数据流向 */}
      <div className="space-y-2">
        <Label htmlFor="dataDestination">数据流向</Label>
        <Select
          value={formData.dataDestination}
          onValueChange={(value) => onFieldChange('dataDestination', value)}
          disabled={mode === 'edit'}
        >
          <SelectTrigger id="dataDestination">
            <SelectValue placeholder="选择数据流向" />
          </SelectTrigger>
          <SelectContent>
            {DATA_DESTINATION_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">
          描述数据流向, 如: 海外表示数据流向海外
        </p>
      </div>

      {/* 优先级 */}
      <div className="space-y-2">
        <Label htmlFor="priority">优先级</Label>
        <Select
          value={formData.priority}
          onValueChange={(value) => onFieldChange('priority', value)}
        >
          <SelectTrigger id="priority">
            <SelectValue placeholder="选择优先级" />
          </SelectTrigger>
          <SelectContent>
            {PRIORITY_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* 协议 */}
      <div className="space-y-2">
        <Label htmlFor="protocol">
          协议 <span className="text-red-600">*</span>
        </Label>
        <Select
          value={formData.protocol}
          onValueChange={(value) => onFieldChange('protocol', value)}
          disabled={mode === 'edit' || protocolsLoading}
        >
          <SelectTrigger
            id="protocol"
            className={errors.protocol ? 'border-red-600' : ''}
          >
            <SelectValue
              placeholder={
                protocolsLoading
                  ? '加载协议列表中...'
                  : protocolsError
                  ? '加载失败'
                  : '选择协议'
              }
            />
          </SelectTrigger>
          <SelectContent>
            {protocols.length > 0 ? (
              protocols.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))
            ) : (
              protocolsError && (
                <div className="px-2 py-1.5 text-sm text-muted-foreground">
                  无可用协议
                </div>
              )
            )}
          </SelectContent>
        </Select>
        {errors.protocol && (
          <p className="text-sm text-red-600">{errors.protocol}</p>
        )}
        {protocolsError && mode === 'create' && (
          <p className="text-sm text-red-600">{protocolsError}</p>
        )}
      </div>

      {/* 供应商 */}
      <div className="space-y-2">
        <Label htmlFor="supplier">
          供应商 <span className="text-red-600">*</span>
        </Label>
        <Input
          id="supplier"
          placeholder="OpenAI Official"
          value={formData.supplier}
          onChange={(e) => onFieldChange('supplier', e.target.value)}
          disabled={mode === 'edit'}
          className={errors.supplier ? 'border-red-600' : ''}
        />
        {errors.supplier && (
          <p className="text-sm text-red-600">{errors.supplier}</p>
        )}
      </div>

      {/* 渠道转发URL */}
      <div className="space-y-2">
        <Label htmlFor="url">
          渠道转发URL <span className="text-red-600">*</span>
        </Label>
        <Input
          id="url"
          placeholder="https://api.example.com/v1/chat/completions"
          value={formData.url}
          onChange={(e) => onFieldChange('url', e.target.value)}
          disabled={mode === 'edit'}
          className={errors.url ? 'border-red-600' : ''}
        />
        {errors.url && <p className="text-sm text-red-600">{errors.url}</p>}
      </div>
    </div>
  )
}
