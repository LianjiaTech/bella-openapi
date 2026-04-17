"use client"

import * as React from "react"

interface SeparatorProps extends React.HTMLAttributes<HTMLDivElement> {}

/**
 * 分隔线组件
 * 用于在内容之间创建视觉分隔
 */
const Separator = React.forwardRef<HTMLDivElement, SeparatorProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={`shrink-0 bg-border h-[1px] w-full ${className || ''}`}
      {...props}
    />
  )
)
Separator.displayName = "Separator"

export { Separator }
