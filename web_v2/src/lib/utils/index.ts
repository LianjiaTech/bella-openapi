import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import { DEFAULT_ENDPOINT } from "@/lib/constants/constants";

/**
 * 合并 Tailwind CSS 类名
 * 用于动态组合样式类，自动处理冲突
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
/**
 * 获取初始端点
 * 如果 URL 参数中没有指定端点,则返回默认端点
 */
export function getInitialEndpoint(endpoint: string | null): string {
    return endpoint || DEFAULT_ENDPOINT
}