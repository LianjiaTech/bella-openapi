import { memo } from "react"
import { Button } from "@/components/common/button"
import { Layers } from "lucide-react"
import { EndpointWithCategory } from "../utils"

interface CapabilitySelectorProps {
  endpoints: EndpointWithCategory[]
  selectedCapability: string
  onCapabilityChange: (endpoint: string) => void
  label: string
}

/**
 * 能力分类选择器组件
 * 用于显示和选择不同的 AI 能力分类（如聊天、嵌入、语音等）
 */
export const CapabilitySelector = memo(({
  endpoints,
  selectedCapability,
  onCapabilityChange,
  label,
}: CapabilitySelectorProps) => {
  if (endpoints.length === 0) {
    return null
  }

  return (
    <div className="mb-6">
      <div className="mb-3 flex items-center gap-2">
        <Layers className="h-4 w-4 text-muted-foreground" />
        <span className="text-sm font-medium text-muted-foreground">
          {label}
        </span>
      </div>
      <div className="flex gap-2 overflow-x-auto pb-2">
        {endpoints.map((capability) => {
          const isSelected = selectedCapability === capability.endpoint
          return (
            <Button
              key={capability.endpoint}
              variant={isSelected ? "default" : "outline"}
              size="sm"
              onClick={() => onCapabilityChange(capability.endpoint)}
              className={`flex items-center gap-2 whitespace-nowrap font-normal cursor-pointer ${
                isSelected ? "bg-primary text-primary-foreground" : ""
              }`}
            >
              {capability.endpointName}
            </Button>
          )
        })}
      </div>
    </div>
  )
})

CapabilitySelector.displayName = "CapabilitySelector"
