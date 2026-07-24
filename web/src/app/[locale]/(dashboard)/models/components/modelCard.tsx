import { Button } from "@/components/common/button"
import { Activity, ExternalLink, Settings } from "lucide-react"
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

function formatCompactNumber(value: number | undefined): string {
  if (value == null || !Number.isFinite(value)) return "--"
  const rounded = Math.round(value)
  if (rounded >= 1000000) return `${Math.round(rounded / 100000) / 10}m`
  if (rounded >= 1000) return `${Math.round(rounded / 100) / 10}k`
  return String(rounded)
}

function formatCapacityText(model: Model): string {
  if (model.capacity?.type === "llm") {
    return [
      (model.capacity.rpm || 0) > 0 ? `RPM ${formatCompactNumber(model.capacity.rpm)}` : "",
      (model.capacity.tpm || 0) > 0 ? `TPM ${formatCompactNumber(model.capacity.tpm)}` : "",
    ].filter(Boolean).join(" · ")
  }
  if (model.capacity?.type === "realtime") {
    return (model.capacity.parallelLimit || 0) > 0 ? `并发 ${formatCompactNumber(model.capacity.parallelLimit)}` : ""
  }
  if (model.capacity?.type === "qps") {
    return (model.capacity.qps || 0) > 0 ? `QPS ${formatCompactNumber(model.capacity.qps)}` : ""
  }
  return ""
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

  const playgroundHref = useMemo(() => {
    const params = new URLSearchParams({
      model: model.modelName,
      endpoint: selectedCapability,
      ocrType: selectedCapability,
    })
    return `${playgroundPath}?${params.toString()}`
  }, [model.modelName, playgroundPath, selectedCapability])

  const statusHref = useMemo(() => {
    const params = new URLSearchParams({
      endpoint: selectedCapability,
      model: model.terminalModel || model.modelName,
    })
    return `/status?${params.toString()}`
  }, [model.modelName, model.terminalModel, selectedCapability])

  const capacityText = useMemo(() => formatCapacityText(model), [model])

  const capacitySection = capacityText ? (
    <div className="flex min-h-8 items-center justify-between gap-2 rounded-md border bg-muted/20 px-2.5 py-1.5 text-[11px]">
      <div className="flex min-w-0 items-center gap-1.5 text-muted-foreground">
        <Activity className="h-3 w-3 flex-shrink-0" />
        <span className="truncate">{capacityText}</span>
      </div>
      <Link href={statusHref} className="flex-shrink-0 text-primary hover:underline">
        查看监控
      </Link>
    </div>
  ) : null

  return (
    <div className="h-full">
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
          {capacitySection}

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
                {formatBatchDiscount(priceInfo.supplierDiscount) && (
                  <div className="text-xs text-right">
                    供应商折扣：{formatBatchDiscount(priceInfo.supplierDiscount)}
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
              <DisplayPriceCard data={priceInfo.data} batchDiscount={priceInfo.batchDiscount} supplierDiscount={priceInfo.supplierDiscount} unit={priceInfo.unit}/>
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
              <Link href={playgroundHref}>{t("tryNow")}</Link>
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
    </div>
  )
}
