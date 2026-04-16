import { Button } from "@/components/common/button"
import { ExternalLink, Settings } from "lucide-react"
import Link from "next/link"
import { Model, ModelProperties } from "@/lib/types/openapi"
import { formatPriceInfo, formatBatchDiscount } from "@/lib/utils/price"
import { BaseModelCard, InfoRow } from "@/components/ui/baseModelCard"
import { useLanguage } from "@/components/providers/language-provider"
import { getPlaygroundPath } from "@/lib/utils"
import { useMemo } from "react"
import { TiersTable } from "./tiersTable"
import { TextToImageCard } from "./textToImageCard"
import { DisplayPriceCard } from "./displayPriceCard"

interface ModelCardProps {
  model: Model
  onAddChannel?: (model: Model) => void
  selectedCapability: string
}

export function ModelCard({ model, onAddChannel, selectedCapability }: ModelCardProps) {
  const { t } = useLanguage()
  // priceDetails 不变时跳过重算（convertTiers 等会分配新对象）
  const priceInfo = useMemo(() => formatPriceInfo(model.priceDetails), [model.priceDetails])

  const properties: ModelProperties = useMemo(() => {
    try {
      return typeof model.properties === 'string'
        ? JSON.parse(model.properties)
        : model.properties
    } catch {
      return {}
    }
  }, [model.properties])

  const playgroundPath = useMemo(() => {
    return getPlaygroundPath(selectedCapability)
  }, [selectedCapability])
  return (
    <BaseModelCard
      model={model}
      showOwnerName={false}
      headerAction={
        <Button
          size="sm"
          variant="ghost"
          className="text-muted-foreground hover:text-foreground hover:bg-white/50 gap-1"
          onClick={() => onAddChannel?.(model)}
        >
          <Settings className="h-4 w-4" />
          私有渠道
        </Button>
      }
      infoSection={
        <>
          {(properties?.max_input_context && properties?.max_output_context) && (
            <InfoRow
              label={t("inputOutputLength")}
              value={`${String(properties?.max_input_context ?? "?")} / ${String(properties?.max_output_context ?? "?")}`}
            />
          )}

          {
            priceInfo.tag === 'tiers' ? (
              <TiersTable data={priceInfo.data} discount={priceInfo.batchDiscount}/>
            ) : priceInfo.tag === 'price' ? (
              <>
                <InfoRow
                  label={`${t("inputOutputPricing")}（${priceInfo.unit}）:`}
                  value={`¥${priceInfo.price}`}
                />
                {/* 批量折扣展示逻辑：1=不打折，0=免费 其他正常折扣 */}
                {formatBatchDiscount(priceInfo.batchDiscount) && (
                  <div className="text-xs text-right">
                    {formatBatchDiscount(priceInfo.batchDiscount)}
                  </div>
                )}
              </>
            ) : priceInfo.tag === 'textToImage' ? (
              <TextToImageCard data={Array.isArray(priceInfo.data) ? priceInfo.data : []} discount={priceInfo.batchDiscount}/>
            ) : priceInfo.tag === 'webSearch' ? (
              <>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("basicSearchPricing")}（{priceInfo.unit}）:</span>
                  <span className="font-medium">{priceInfo.basicSearchPrice}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("advancedSearchPricing")}（{priceInfo.unit}）:</span>
                  <span className="font-medium">{priceInfo.advancedSearchPrice}</span>
                </div>
              </>
            ) : priceInfo.tag === 'displayPrice' ? (
              <DisplayPriceCard data={priceInfo.data} batchDiscount={priceInfo.batchDiscount} unit={priceInfo.unit}/>
            ) : 
            (
              <>
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
            )
          }

        </>
      }
      actionSection={
        <>
          <Button size="sm" className="flex-1" asChild>
            <Link href={`${playgroundPath}?model=${model.modelName}&ocrType=${selectedCapability}`}>{t("tryNow")}</Link>
          </Button>
          {model.documentUrl && (
            <Button size="sm" variant="outline" asChild>
              <Link href={`${model.documentUrl}`} target="_blank">
                <ExternalLink className="h-4 w-4" />
              </Link>
            </Button>
          )}
        </>
      }
    />
  )
}