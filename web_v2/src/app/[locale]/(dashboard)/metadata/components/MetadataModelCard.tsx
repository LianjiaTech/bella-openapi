import { Button } from "@/components/common/button"
import { Settings } from "lucide-react"
import { Model } from "@/lib/types/openapi"
import { formatPriceInfo } from "@/lib/utils/price"
import { BaseModelCard, InfoRow } from "@/components/ui/baseModelCard"

interface MetadataModelCardProps {
  model: Model
  onConfigure?: (model: Model) => void
}

/**
 * 元数据模型卡片组件
 * 用于元数据管理页面展示模型信息
 */
export function MetadataModelCard({ model, onConfigure }: MetadataModelCardProps) {
  // 格式化价格信息
  const priceInfo = formatPriceInfo(model.priceDetails)

  // 解析 properties（是 JSON 字符串）
  const properties = (() => {
    try {
      return typeof model.properties === 'string'
        ? JSON.parse(model.properties)
        : model.properties
    } catch {
      return {}
    }
  })()

  // 格式化上下文窗口显示
  const formatContext = (value: unknown): string => {
    if (typeof value === 'number') {
      if (value >= 1000) {
        return `${(value / 1000).toFixed(0)}K`
      }
      return value.toString()
    }
    return '?'
  }

  // 获取上下文窗口和最大输出
  const maxInput = formatContext(properties?.max_input_context)
  const maxOutput = formatContext(properties?.max_output_context)

  return (
    <BaseModelCard
      model={model}
      showOwnerName={true}
      infoSection={
        <>
          <InfoRow label="最大输入:" value={maxInput} />
          <InfoRow label="最大输出:" value={maxOutput} />
          {/* <InfoRow
            label="计价:"
            value={`¥${priceInfo.input}/¥${priceInfo.output}/${priceInfo.unit}`}
          /> */}
        </>
      }
      actionSection={
        <Button
          size="sm"
          className="w-full gap-2 cursor-pointer"
          onClick={() => onConfigure?.(model)}
        >
          <Settings className="h-4 w-4" />
          配置
        </Button>
      }
    />
  )
}
