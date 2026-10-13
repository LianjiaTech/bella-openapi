"use client"

import * as React from "react"
import { useSidebar } from "@/components/providers"
import { flattenCategoryTrees, EndpointWithCategory } from "@/components/ui/modelFilterPanel/utils"


/**
 * useEndpoint Hook 返回值接口
 */
export interface UseEndpointResult {
  /** 扁平化后的端点列表 */
  flattenedEndpoints: EndpointWithCategory[]
  /** 是否正在加载 */
  isLoading: boolean
}

export function useEndpoint(): UseEndpointResult {
  const { categoryTrees, isLoading } = useSidebar()

  // 扁平化端点列表
  const flattenedEndpoints = React.useMemo(
    () => flattenCategoryTrees(categoryTrees),
    [categoryTrees]
  )

  return {
    flattenedEndpoints,
    isLoading
  }
}
