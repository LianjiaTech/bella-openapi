import {
  MessageSquare,
  Brain,
  Mic,
  Volume2,
  Radio,
  ImageIcon,
  Database,
  Network,
  FileText,
  WorkflowIcon,
  Search,
  ScanText,
  LucideIcon,
} from "lucide-react"

/**
 * Endpoint 映射配置接口
 */
export interface EndpointMapping {
  endpoint: string // 完整的 API endpoint，如 'v1/chat/completions'
  route: string // 简短的路由路径，如 'chat'
  icon: LucideIcon // 图标组件
}

/**
 * Endpoint 到路由的映射表
 * 用于将完整的 API endpoint 映射到简短的路由路径
 */
export const ENDPOINT_MAPPINGS: EndpointMapping[] = [
  {
    endpoint: "/v1/chat/completions",
    route: "chat",
    icon: MessageSquare,
  },
  {
    endpoint: "/v1/images/generations",
    route: "images",
    icon: ImageIcon,
  },
  {
    endpoint: "/v1/audio/transcriptions",
    route: "audio",
    icon: Mic,
  },
  {
    endpoint: "/v1/audio/speech",
    route: "tts",
    icon: Volume2,
  },
  {
    endpoint: "/v1/embeddings",
    route: "embedding",
    icon: Brain,
  },
  {
    endpoint: "/v1/realtime",
    route: "realtime",
    icon: Radio,
  },
  {
    endpoint: "/v1/knowledge",
    route: "knowledge",
    icon: Database,
  },
  {
    endpoint: "/v1/rag",
    route: "rag",
    icon: Network,
  },
  {
    endpoint: "/v1/docparse",
    route: "docparse",
    icon: FileText,
  },
  {
    endpoint: "/v1/workflow",
    route: "workflow",
    icon: WorkflowIcon,
  },
  {
    endpoint: "/v1/search",
    route: "search",
    icon: Search,
  },
  {
    endpoint: "/v1/ocr",
    route: "ocr",
    icon: ScanText,
  },
]

/**
 * 根据 endpoint 查找对应的路由配置
 * 支持带斜杠和不带斜杠的格式
 */
export function findMappingByEndpoint(endpoint: string): EndpointMapping | undefined {
  // 标准化 endpoint：确保以斜杠开头
  const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  return ENDPOINT_MAPPINGS.find((mapping) => mapping.endpoint === normalizedEndpoint)
}

/**
 * 根据 route 查找对应的路由配置
 */
export function findMappingByRoute(route: string): EndpointMapping | undefined {
  return ENDPOINT_MAPPINGS.find((mapping) => mapping.route === route)
}

/**
 * 根据 endpoint 获取路由路径
 */
export function getRouteFromEndpoint(endpoint: string): string | undefined {
  return findMappingByEndpoint(endpoint)?.route
}

/**
 * 根据 route 获取 endpoint
 */
export function getEndpointFromRoute(route: string): string | undefined {
  return findMappingByRoute(route)?.endpoint
}

/**
 * 根据 endpoint 获取完整的 playground URL
 * 例如: /v1/chat/completions -> /playground/chat?endpoint=/v1/chat/completions
 */
export function getPlaygroundUrl(endpoint: string): string {
  // 标准化 endpoint：确保以斜杠开头
  const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  const route = getRouteFromEndpoint(normalizedEndpoint)
  if (!route) {
    // 如果找不到映射，使用 endpoint 本身作为路由
    return `/playground?endpoint=${encodeURIComponent(normalizedEndpoint)}`
  }
  return `/playground/${route}?endpoint=${encodeURIComponent(normalizedEndpoint)}`
}

/**
 * 根据 endpoint 或 endpointCode 获取图标
 */
export function getIconForEndpoint(endpointOrCode: string): LucideIcon {
  // 先尝试完整匹配 endpoint
  let mapping = findMappingByEndpoint(endpointOrCode)
  
  // 如果没找到，尝试匹配 route (用于 endpointCode)
  if (!mapping) {
    mapping = findMappingByRoute(endpointOrCode.toLowerCase())
  }
  
  // 返回找到的图标，或默认图标
  return mapping?.icon || MessageSquare
}

