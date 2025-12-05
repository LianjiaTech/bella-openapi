import { usePlaygroundContext } from "@/lib/context/playground-context"

/**
 * 自定义 Hook: 方便访问 Playground 共享数据
 * 
 * @example
 * ```tsx
 * const { endpointDetails, loading, error, refetch } = usePlaygroundData()
 * const models = endpointDetails?.models || []
 * ```
 */
export function usePlaygroundData() {
  return usePlaygroundContext()
}

