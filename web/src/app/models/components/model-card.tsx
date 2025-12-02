import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { MessageSquare, ExternalLink, Plus } from "lucide-react"
import Link from "next/link"
import { Model } from "@/lib/types/openapi"

interface ModelCardProps {
  model: Model
  onAddChannel?: (model: Model) => void
  tagColors?: string[]
}

const defaultTagColors = [
  "bg-green-500/10 text-green-500 border-green-500/20",
  "bg-blue-500/10 text-blue-500 border-blue-500/20",
  "bg-purple-500/10 text-purple-500 border-purple-500/20",
  "bg-orange-500/10 text-orange-500 border-orange-500/20",
  "bg-pink-500/10 text-pink-500 border-pink-500/20",
  "bg-indigo-500/10 text-indigo-500 border-indigo-500/20",
  "bg-cyan-500/10 text-cyan-500 border-cyan-500/20",
  "bg-red-500/10 text-red-500 border-red-500/20",
]

export function ModelCard({ model, onAddChannel, tagColors = defaultTagColors }: ModelCardProps) {
  const features = typeof model.features === 'string'
    ? model.features.split(',').filter(f => f.trim())
    : []

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
        <div className="mb-3 min-h-[40px] space-y-1 text-sm">
          {model.properties && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">输入/输出长度:</span>
              <span className="font-medium">
                {model.properties?.max_input_context ?? "?"} / {model.properties?.max_output_context ?? "?"}
              </span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">输入/输出定价（{model.priceDetails?.unit ?? "?"}）:</span>
            <span className="font-medium">
              ¥{model.priceDetails?.priceInfo?.input ?? "?"} / ¥{model.priceDetails?.priceInfo?.output ?? "?"}
            </span>
          </div>
          {model.priceDetails?.priceInfo?.cachedRead !== undefined && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">命中缓存定价（{model.priceDetails?.unit ?? "?"}）:</span>
              <span className="font-medium">
                ¥{model.priceDetails?.priceInfo?.cachedRead ?? "?"}
              </span>
            </div>
          )}
        </div>

        {/* Features */}
        <div className="mb-4 flex flex-wrap gap-1">
          {features.map((feature: string, index) => (
            <Badge
              key={feature}
              variant="secondary"
              className={`text-sm ${tagColors[index % tagColors.length]}`}
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
