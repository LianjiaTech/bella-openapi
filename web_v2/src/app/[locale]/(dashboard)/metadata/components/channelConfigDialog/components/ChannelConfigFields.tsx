import { Label } from '@/components/common/label'
import { Input } from '@/components/common/input'
import { FieldRenderer } from '../../fieldRenderer/FieldRenderer'
import type { JsonSchema } from '@/lib/types/metadata'
import type { DialogMode, ChannelFormData } from '../types'

interface ChannelConfigFieldsProps {
  mode: DialogMode
  protocol: string
  channelInfo: string
  schema: JsonSchema
  values: Record<string, any>
  loading: boolean
  error: string | null
  onFieldChange: (fieldCode: string, value: any) => void
  onChannelInfoChange: (e: React.ChangeEvent<HTMLInputElement>) => void
}

/**
 * 协议配置信息字段组件
 * 根据选择的协议动态渲染配置表单
 */
export function ChannelConfigFields({
  mode,
  protocol,
  channelInfo,
  schema,
  values,
  loading,
  error,
  onFieldChange,
  onChannelInfoChange,
}: ChannelConfigFieldsProps) {
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label className="text-sm">协议配置信息</Label>
        <p className="text-xs text-muted-foreground">
          根据所选协议配置不同的参数
        </p>
      </div>

      {/* 协议配置加载状态 */}
      {loading && (
        <div className="text-sm text-muted-foreground">加载协议配置中...</div>
      )}

      {/* 协议配置加载失败 */}
      {error && !loading && (
        <div className="text-sm text-red-600">加载协议配置失败: {error}</div>
      )}

      {/* 动态渲染协议配置字段 */}
      {!loading && !error && schema.params.length > 0 && (
        <div className="space-y-4 p-4 border rounded-lg bg-muted/30">
          {schema.params.map((param) => (
            <div key={param.code} className="space-y-2">
              <Label htmlFor={`channel-${param.code}`}>{param.name}</Label>
              {param.description && (
                <p className="text-xs text-muted-foreground">
                  {param.description}
                </p>
              )}
              <FieldRenderer
                mode={mode}
                schema={param}
                value={values[param.code]}
                onChange={(value) => onFieldChange(param.code, value)}
              />
            </div>
          ))}
        </div>
      )}

      {/* 无协议选择时的提示 (仅新增模式) */}
      {!protocol && mode === 'create' && (
        <div className="text-sm text-muted-foreground p-4 border rounded-lg bg-muted/30">
          请先选择协议以加载配置选项
        </div>
      )}

      {/* 编辑模式下无 Schema 数据的降级显示 */}
      {mode === 'edit' &&
        !loading &&
        schema.params.length === 0 &&
        channelInfo && (
          <div className="space-y-2">
            <Label htmlFor="channelInfo-fallback">协议配置信息 (JSON)</Label>
            <Input
              id="channelInfo-fallback"
              value={channelInfo}
              onChange={onChannelInfoChange}
              placeholder="{}"
            />
            <p className="text-xs text-muted-foreground">
              无法加载动态表单,请直接编辑 JSON 格式配置
            </p>
          </div>
        )}
    </div>
  )
}
