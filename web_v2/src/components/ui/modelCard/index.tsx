import { Card, CardContent } from "@/components/common/card"
import { Button } from "@/components/common/button"
import { Badge } from "@/components/common/badge"
import { MessageSquare,ImageIcon, Mic,Volume2,Layers,FileText,Waves,ExternalLink, Plus } from "lucide-react"
import Link from "next/link"
import { Model } from "@/lib/types/openapi"
import { formatPriceInfo } from "@/lib/utils/price"
import { getTagColor } from "@/lib/constants/theme"

interface ModelCardProps {
  model: Model
  onAddChannel?: (model: Model) => void
}

export function ModelCard({ model, onAddChannel }: ModelCardProps) {
  const features = typeof model.features === 'string'
    ? model.features.split(',').filter(f => f.trim())
    : []

  // 使用工具函数格式化价格信息
  const priceInfo = formatPriceInfo(model.priceDetails)

  return (
    <Card
      className="border-border/50 transition-all hover:border-primary/50 hover:shadow-md"
    >
      <CardContent className="p-5 h-full flex flex-col justify-between">
        {/* Header */}
        <div className="mb-4 flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <MessageSquare className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h3 className="font-semibold">{model.modelName}</h3>
            </div>
          </div>
          <Button
            size="sm"
            variant="ghost"
            className="h-8 w-8 p-0 hover:bg-primary/10"
            title="添加私有渠道"
            onClick={() => onAddChannel?.(model)}
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>

        {/* Properties */}
        <div className="mb-3 min-h-[40px] space-y-1 text-xs">
          {model.properties && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">输入/输出长度:</span>
              <span className="font-medium">
                {String(model.properties?.max_input_context ?? "?")} / {String(model.properties?.max_output_context ?? "?")}
              </span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">输入/输出定价（{priceInfo.unit}）:</span>
            <span className="font-medium">
              ¥{priceInfo.input} / ¥{priceInfo.output}
            </span>
          </div>
          {priceInfo.cachedRead !== null && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">命中缓存定价（{priceInfo.unit}）:</span>
              <span className="font-medium">
                ¥{priceInfo.cachedRead}
              </span>
            </div>
          )}
        </div>

        {/* Features */}
        <div className="mb-4 flex flex-wrap gap-1">
          {features.map((feature: string) => (
            <Badge
              key={feature}
              variant="secondary"
              className={`text-xs ${getTagColor(feature)}`}
            >
              {feature}
            </Badge>
          ))}
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          <Button size="sm" className="flex-1" asChild>
            <Link href={`/playground/chat?model=${model.modelName}`}>试用</Link>
          </Button>
          <Button size="sm" variant="outline" asChild>
            <Link href={`/docs/api/${model.modelName}`}>
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
