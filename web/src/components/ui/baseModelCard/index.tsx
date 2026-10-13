import { Card, CardContent } from "@/components/common/card"
import { Badge } from "@/components/common/badge"
import { MessageSquare } from "lucide-react"
import { Model } from "@/lib/types/openapi"
import { getTagColor } from "@/lib/constants/theme"
import { ReactNode } from "react"

/**
 * BaseModelCard 配置项
 */
export interface BaseModelCardProps {
  /** 模型数据 */
  model: Model
  /** 顶部右侧操作区域 */
  headerAction?: ReactNode
  /** 信息展示区域（自定义渲染） */
  infoSection: ReactNode
  /** 底部操作按钮区域 */
  actionSection: ReactNode
  /** 是否显示拥有者名称 */
  showOwnerName?: boolean
  /** 显示的特性标签数量限制（默认不限制） */
  maxFeatures?: number
  /** 卡片点击事件 */
  onClick?: () => void
}

/**
 * BaseModelCard 基础模型卡片组件
 *
 * 提供统一的卡片布局和样式，通过插槽支持不同的信息展示和操作按钮
 *
 * @example
 * ```tsx
 * <BaseModelCard
 *   model={model}
 *   headerAction={<Button>添加</Button>}
 *   infoSection={
 *     <div>
 *       <div>输入/输出: 128K / 8K</div>
 *       <div>定价: ¥0.01 / ¥0.02</div>
 *     </div>
 *   }
 *   actionSection={
 *     <Button>试用</Button>
 *   }
 * />
 * ```
 */
export function BaseModelCard({
  model,
  headerAction,
  infoSection,
  actionSection,
  showOwnerName = false,
  maxFeatures,
  onClick,
}: BaseModelCardProps) {
  // 解析特性标签
  const features = typeof model.features === 'string'
    ? model.features.split(',').filter(f => f.trim())
    : []

  // 限制显示的特性数量
  const displayFeatures = maxFeatures !== undefined
    ? features.slice(0, maxFeatures)
    : features

  return (
    <Card
      className="h-full border-border/50 transition-all hover:border-primary/50 hover:shadow-md"
      onClick={onClick}
    >
      <CardContent className="p-5 h-full flex flex-col">
        {/* Header - 模型名称和图标 */}
        <div className="mb-3 flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <MessageSquare className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h3 className="font-semibold text-base">{model.modelName}</h3>
              {showOwnerName && (
                <p className="text-xs text-muted-foreground mt-0.5">{model.ownerName}</p>
              )}
            </div>
          </div>
          {/* 右侧操作区域 */}
          {headerAction && <div>{headerAction}</div>}
        </div>

        {/* Feature Tags - 特性标签 */}
        <div className="mb-4 flex flex-wrap gap-2">
          {displayFeatures.map((feature: string) => (
            <Badge
              key={feature}
              variant="outline"
              className={`text-xs font-normal ${getTagColor(feature)}`}
            >
              {feature}
            </Badge>
          ))}
        </div>

        {/* Info Section - 信息展示区域（自定义内容） */}
        <div className="mb-4 space-y-2 text-xs flex-1">
          {infoSection}
        </div>

        {/* Action Section - 操作按钮区域（自定义内容） */}
        <div className="flex gap-2">
          {actionSection}
        </div>
      </CardContent>
    </Card>
  )
}

/**
 * InfoRow - 信息行组件（可选工具组件）
 * 用于统一信息行的样式
 */
export function InfoRow({
  label,
  value,
}: {
  label: string
  value: ReactNode
}) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  )
}