"use client"

import * as React from "react"
import { Button } from "@/components/common/button"
import { Skeleton } from "@/components/common/skeleton"
import { useEndpoint } from "./hooks/useEndpoint"

/**
 * 能力分类选择组件
 * 用于显示和选择不同的 API 端点能力分类
 */
export function CategorySelect({value,onValueChange}:{value:string,onValueChange:(param:string)=>void}) {
  // 使用 useEndpoint Hook 获取端点数据
  const { flattenedEndpoints, isLoading} = useEndpoint()
  const handleEndpointChange = (endpoint: string) => {
    onValueChange?.(endpoint)
  }

  // 加载状态：显示骨架屏
  if (isLoading) {
    return (
      <div className="flex gap-2 overflow-x-auto pb-2">
        {[80, 100, 90, 110, 95].map((width, index) => (
          <Skeleton
            key={index}
            className={`h-8 rounded-md`}
            style={{ width: `${width}px` }}
          />
        ))}
      </div>
    )
  }

  // 数据加载完成：显示能力分类按钮
  return (
    <div className="flex gap-2 overflow-x-auto pb-2">
      {flattenedEndpoints.map((capability) => {
        const isSelected = value === capability.endpoint
        return (
          <Button
            key={capability.endpoint}
            variant={isSelected ? "default" : "outline"}
            size="sm"
            onClick={() => handleEndpointChange(capability.endpoint)}
            className={`whitespace-nowrap font-normal cursor-pointer ${
              isSelected ? "bg-primary text-primary-foreground" : ""
            }`}
          >
            {capability.endpointName}
          </Button>
        )
      })}
    </div>
  )
}
