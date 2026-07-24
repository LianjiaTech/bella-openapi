import { Label } from '@/components/common/label'
import { FieldRenderer } from '../../fieldRenderer/FieldRenderer'
import type { JsonSchema } from '@/lib/types/metadata'
import type { FormErrors } from '../types'

interface PriceInfoFieldsProps {
  mode: 'create' | 'edit'  // 操作模式
  schema: JsonSchema
  values: Record<string, any>
  errors: FormErrors
  loading: boolean
  error: string | null
  onFieldChange: (fieldCode: string, value: any) => void
}

/**
 * 价格信息配置字段组件
 * 根据 Schema 动态渲染价格配置表单
 */
export function PriceInfoFields({
  mode,
  schema,
  values,
  errors,
  loading,
  error,
  onFieldChange,
}: PriceInfoFieldsProps) {
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label className="text-sm font-semibold">
          价格信息配置 <span className="text-red-600">*</span>
        </Label>
        <p className="text-xs text-muted-foreground">
          根据模型配置不同的价格参数
        </p>
      </div>

      {/* 价格信息加载状态 */}
      {loading && (
        <div className="text-sm text-muted-foreground">加载价格配置中...</div>
      )}

      {/* 价格信息加载失败 */}
      {error && !loading && (
        <div className="text-sm text-red-600">加载价格配置失败: {error}</div>
      )}

      {/* 动态渲染价格信息字段 */}
      {!loading && !error && schema.params.length > 0 && (
        <div className="space-y-4 p-4 border rounded-lg bg-muted/30">
          {schema.params.map((param) => (
            <div key={param.code} className="space-y-2">
              {/* <Label htmlFor={`price-${param.code}`}>
                {param.name}
                {param.code === 'input' || param.code === 'output' ? (
                  <span className="text-red-600"> *</span>
                ) : null}
              </Label> */}
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

      {/* 整体价格信息验证错误 */}
      {errors.priceInfo && (
        <p className="text-sm text-red-600">{errors.priceInfo}</p>
      )}
    </div>
  )
}
