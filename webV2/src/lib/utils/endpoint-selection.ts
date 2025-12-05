/**
 * Get the initial endpoint selection based on priority:
 * 1. URL parameter (highest priority)
 * 2. sessionStorage value
 * 3. Default fallback value
 *
 * @param urlEndpoint - Endpoint from URL search parameters
 * @param storageKey - sessionStorage key to check (default: "sidebar-selected-endpoint")
 * @param defaultEndpoint - Default endpoint if none found (default: "/v1/chat/completions")
 * @returns The selected endpoint string
 */
export function getInitialEndpoint(
  urlEndpoint: string | null,
  storageKey: string = "sidebar-selected-endpoint",
  defaultEndpoint: string = "/v1/chat/completions"
): string {
  // 1. 优先读取 URL 中的 endpoint 参数
  if (urlEndpoint) {
    return urlEndpoint
  }

  // 2. 如果 URL 没有,读取 sessionStorage
  if (typeof window !== "undefined") {
    const endpointFromStorage = sessionStorage.getItem(storageKey)
    if (endpointFromStorage) {
      return endpointFromStorage
    }
  }

  // 3. 默认选中指定的 endpoint
  return defaultEndpoint
}
