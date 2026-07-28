"use client"

import { cn } from "@/lib/utils"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/common/tooltip"

interface TruncatedTextProps {
  value?: string | null
  maxChars?: number
  className?: string
}

export function TruncatedText({ value, maxChars = 5, className }: TruncatedTextProps) {
  const text = value || "-"
  const characters = Array.from(text)
  const isTruncated = characters.length > maxChars
  const displayText = isTruncated ? `${characters.slice(0, maxChars).join("")}…` : text

  if (!isTruncated) {
    return <span className={className}>{displayText}</span>
  }

  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className={cn("inline-block cursor-help", className)} tabIndex={0}>
            {displayText}
          </span>
        </TooltipTrigger>
        <TooltipContent className="max-w-sm break-words">{text}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
