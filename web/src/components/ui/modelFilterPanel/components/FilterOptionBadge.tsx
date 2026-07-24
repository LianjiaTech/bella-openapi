import { memo } from "react"
import { Badge } from "@/components/common/badge"
import { CustomFilterOption } from "../types"
import { cn } from "@/lib/utils"

interface FilterOptionBadgeProps {
  option: CustomFilterOption
  isSelected: boolean
  onClick: () => void
  isSingleSelect?: boolean
}

/**
 * 筛选选项徽章组件
 * 用于展示单个筛选选项,支持单选和多选模式
 */
export const FilterOptionBadge = memo(({
  option,
  isSelected,
  onClick,
  isSingleSelect = false,
}: FilterOptionBadgeProps) => {
  return (
    <div className="relative">
      <Badge
        variant={isSelected ? "default" : "outline"}
        className={cn(
          "cursor-pointer transition-colors font-normal rounded-md",
          isSingleSelect && "w-full justify-start",
          isSelected && (option.color || (isSingleSelect && "bg-primary text-primary-foreground"))
        )}
        onClick={onClick}
        title={option.description}
        style={isSingleSelect ? { display: "flex", alignItems: "center" } : undefined}
      >
        {option.label}
      </Badge>
    </div>
  )
})

FilterOptionBadge.displayName = "FilterOptionBadge"
