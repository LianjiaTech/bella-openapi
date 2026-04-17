/**
 * TextToImageCard 组件
 *
 * 职责：
 * - 专门负责渲染文生图模型的价格展示
 * - 显示不同尺寸（size）下的价格信息
 * - 展示低清/中清/高清三种质量的价格
 * - 展示文本和图像的 Token 价格
 *
 * 设计说明：
 * - 使用 React.memo 优化，避免不必要的 re-render
 * - 仅在 data prop 变化时才重新渲染
 * - 子组件 PriceCard 也使用 memo 优化
 */

import React from "react"
import { Maximize2, Zap, Image } from "lucide-react"
import { PriceCard } from "./priceCard"
import { formatBatchDiscount } from "@/lib/utils/price"

/**
 * 文生图价格详情接口
 */
interface TextToImagePriceDetail {
  size: string
  ldPricePerImage: number
  mdPricePerImage: number
  hdPricePerImage: number
  unit: string

  tokenUnit: string
  textTokenPriceStr: string | null
  imageTokenPriceStr: string | null
}

interface TextToImageCardProps {
  data: TextToImagePriceDetail[] | {}
  discount: number | undefined
}


/**
 * TextToImageCard 组件
 * 渲染文生图模型的价格信息列表
 */
export const TextToImageCard = React.memo<TextToImageCardProps>(({ data, discount }) => {
  if(!Array.isArray(data)) return
  // 如果没有数据，不渲染任何内容
  if (!data || data.length === 0) {
    return null
  }

  return (
    <>
      {data.map((detail, index) => (
        <div key={index} className="bg-muted/30 rounded-2xl p-4 mb-5">
          {/* 尺寸信息 */}
          <div className="flex items-center justify-center gap-2 mb-4 pb-3 border-b border-border/50">
            <Maximize2 className="w-4 h-4 text-muted-foreground" />
            <span className="text-sm font-medium text-foreground">
              尺寸：{detail.size}
            </span>
          </div>

          {/* 价格网格 */}
          <div className="grid grid-cols-3 gap-3">
            <PriceCard
              label="低清"
              price={detail.ldPricePerImage}
              unit={detail.unit}
            />
            <PriceCard
              label="中清"
              price={detail.mdPricePerImage}
              unit={detail.unit}
              variant="highlight"
            />
            <PriceCard
              label="高清"
              price={detail.hdPricePerImage}
              unit={detail.unit}
            />
          </div>

          {/* Token 价格 */}
          <div className="flex items-center justify-center gap-6 mt-4 pt-3 border-t border-border/50">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Zap className="w-3.5 h-3.5" />
              <span>文本: {detail.textTokenPriceStr ? `${detail.textTokenPriceStr}${detail.tokenUnit}` : "暂无"}</span>
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Image className="w-3.5 h-3.5" />
              <span>图像: {detail.imageTokenPriceStr ? `${detail.imageTokenPriceStr}${detail.tokenUnit}` : "暂无"}</span>
            </div>
          </div>
        </div>
      ))}
      {formatBatchDiscount(discount) && (
        <div className="text-xs text-right">
          <span>批量折扣：{formatBatchDiscount(discount)}</span>
        </div>
      )}
    </>
  )
})

TextToImageCard.displayName = "TextToImageCard"
