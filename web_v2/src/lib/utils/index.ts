import OCRPlaygroundPage from "@/app/[locale]/(dashboard)/playground/ocr/page";
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

/**
 * 将后端 API endpoint 映射到前端 playground 路径
 *
 * 职责：
 * - 提供 endpoint 到 playground 页面的统一映射关系
 * - 降级策略：不支持的 endpoint 默认返回 chat 页面
 *
 * 设计说明：
 * - 纯函数设计，无副作用，便于测试和维护
 * - 使用 Map 结构提升查找性能
 * - 支持完整 endpoint 路径匹配
 *
 * @param endpoint - 后端 API endpoint 路径（如 "/v1/chat/completions"）
 * @returns playground 页面路径（如 "/playground/chat"）
 */
export function getPlaygroundPath(endpoint: string): string {
  // endpoint 到 playground 路径的映射关系
  const endpointMap = new Map<string, string>([
    ["/v1/chat/completions", "/playground/chat"], // 智能问答
    ["/v1/embeddings", "/playground/embedding"], // 向量化
    ["/v1/audio/speech", "/playground/audio/tts"], // 语音合成
    ["/v1/audio/asr/flash", "/playground/audio/asr/flash"], // 一句话语音识别
    ["/v1/audio/asr/stream", "/playground/audio/asr/realtime/transcription"], // 实时语音识别
    ["/v1/audio/realtime", "/playground/audio/asr/realtime/chat"], // 实时对话
    // 文生图
    ["/v1/images/generations", "/playground/images/generations"], // 图像生成
    // 图生图
    ["/v1/images/edits", "/playground/images/edits"], // 图像编辑
    // 文档解析
    ["/v1/document/parse", "/playground/docparse"], // 文档解析
    // AI工作流
    ["/v1/workflow", "/playground/workflow"], // 工作流
    // OCR
    ["/v1/ocr/idcard", "/playground/ocr"], //  身份证OCR
    ["/v1/ocr/bankcard", "/playground/ocr"], // 银行卡 OCR
    ["/v1/ocr/hmt-residence-permit", "/playground/ocr"], // 港澳台居住证 OCR
    ["/v1/ocr/tmp-idcard", "/playground/ocr"], // 临时身份证 OCR
    ["/v1/ocr/general", "/playground/ocr"], // 通用文字识别
    // ["/v1/ocr/business-license", "/playground/ocr"], // 营业执照 OCR
  ])

  // 查找映射，找不到则降级到 chat 页面
  return endpointMap.get(endpoint) || "/playground/chat"
}
