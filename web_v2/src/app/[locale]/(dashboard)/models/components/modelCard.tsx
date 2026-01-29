import { Button } from "@/components/common/button"
import { ExternalLink, Plus } from "lucide-react"
import Link from "next/link"
import { Model, ModelProperties } from "@/lib/types/openapi"
import { formatPriceInfo } from "@/lib/utils/price"
import { BaseModelCard, InfoRow } from "@/components/ui/baseModelCard"
import { useLanguage } from "@/components/providers/language-provider"

interface ModelCardProps {
  model: Model
  onAddChannel?: (model: Model) => void
}

export function ModelCard({ model, onAddChannel }: ModelCardProps) {
  const { t } = useLanguage()
  // 使用工具函数格式化价格信息
  const priceInfo = formatPriceInfo(model.priceDetails)

  // 解析 properties（是 JSON 字符串）
  const properties: ModelProperties = (() => {
    try {
      return typeof model.properties === 'string'
        ? JSON.parse(model.properties)
        : model.properties
    } catch {
      return {}
    }
  })()

  return (
    <BaseModelCard
      model={model}
      showOwnerName={false}
      headerAction={
        <Button
          size="sm"
          variant="ghost"
          className="h-8 w-8 p-0 hover:bg-primary/10"
          title={t("addPrivateChannel")}
          onClick={() => onAddChannel?.(model)}
        >
          <Plus className="h-4 w-4" />
        </Button>
      }
      infoSection={
        <>
          {properties && (
            <InfoRow
              label={t("inputOutputLength")}
              value={`${String(properties?.max_input_context ?? "?")} / ${String(properties?.max_output_context ?? "?")}`}
            />
          )}
          <InfoRow
            label={`${t("inputOutputPricing")}（${priceInfo.unit}）:`}
            value={`¥${priceInfo.input} / ¥${priceInfo.output}`}
          />
          {priceInfo.cachedRead !== null && (
            <InfoRow
              label={`${t("cachedReadPricing")}（${priceInfo.unit}）:`}
              value={`¥${priceInfo.cachedRead}`}
            />
          )}
        </>
      }
      actionSection={
        <>
          <Button size="sm" className="flex-1" asChild>
            <Link href={`/playground/chat?model=${model.modelName}`}>{t("tryNow")}</Link>
          </Button>
          <Button size="sm" variant="outline" asChild>
            <Link href={`/docs/api/${model.modelName}`}>
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
        </>
      }
    />
  )
}