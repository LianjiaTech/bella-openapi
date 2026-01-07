import React, { memo } from "react"
import { Badge } from "@/components/common/badge"
import { MetadataFeature } from "@/lib/types/openapi"

interface FeatureTagListProps {
  features: MetadataFeature[]
  selectedTags: string[]
  onTagToggle: (tag: string) => void
  tagColors: readonly string[]
}

/**
 * 特性标签列表组件
 * 使用 memo 优化性能，避免父组件更新时不必要的重渲染
 */
export const FeatureTagList = memo(({
  features,
  selectedTags,
  onTagToggle,
  tagColors,
}: FeatureTagListProps) => {
  if (features.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap gap-2">
      {features.map((feature, idx) => {
        const isSelected = selectedTags.includes(feature.code)
        const colorClass = isSelected ? tagColors[idx % tagColors.length] : ""

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
