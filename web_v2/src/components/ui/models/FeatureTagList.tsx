import React, { memo } from "react"
import { Badge } from "@/components/common/badge"
import { MetadataFeature } from "@/lib/types/openapi"
import { getTagColor } from "@/lib/constants/theme"

interface FeatureTagListProps {
  features: MetadataFeature[]
  selectedTags: string[]
  onTagToggle: (tag: string) => void
}

/**
 * 特性标签列表组件
 * 使用 memo 优化性能，避免父组件更新时不必要的重渲染
 */
export const FeatureTagList = memo(({
  features,
  selectedTags,
  onTagToggle,
}: FeatureTagListProps) => {
  if (features.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap gap-2">
      {features.map((feature) => {
        const isSelected = selectedTags.includes(feature.code)
        // 使用基于特性代码的稳定颜色映射,确保相同特性始终显示相同颜色
        const colorClass = isSelected ? getTagColor(feature.code) : ""

        return (
          <Badge
            key={feature.code}
            variant={isSelected ? "default" : "outline"}
            className={`cursor-pointer transition-colors font-normal rounded-md ${colorClass}`}
            onClick={() => onTagToggle(feature.code)}
          >
            {feature.name}
          </Badge>
        )
      })}
    </div>
  )
})

FeatureTagList.displayName = "FeatureTagList"
