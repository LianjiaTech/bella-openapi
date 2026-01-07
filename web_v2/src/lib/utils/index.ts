import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * 合并 Tailwind CSS 类名
 * 用于动态组合样式类，自动处理冲突
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * 根据优先级获取初始端点选择：
 * 1. URL 参数（最高优先级）
 * 2. sessionStorage 中的值
 * 3. 默认后备值
 *
 * @param urlEndpoint - 来自 URL 查询参数的端点
 * @param storageKey - 要检查的 sessionStorage 键（默认值："sidebar-selected-endpoint"）
 * @param defaultEndpoint - 如果找不到则使用的默认端点（默认值："/v1/chat/completions"）
 * @returns 选中的端点字符串
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
