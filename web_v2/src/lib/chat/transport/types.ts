import type { StreamStatus } from '@/lib/types/chat';

/**
 * 响应模式类型
 * - stream: 流式响应模式，支持服务器推送事件
 * - json: 标准 JSON 响应模式，一次性返回完整数据
 */
export type ResponseMode = 'stream' | 'json';

/**
 * StreamManager 配置选项
 */
export interface StreamOptions {
  url: string;
  body: any;
  headers?: Record<string, string>;
  onOpen?: () => void;
  onMessage?: (chunk: string) => void;
  onDone?: () => void;
  onError?: (err: unknown) => void;
}
/**
 * start() 负责发请求并写入 store
 * abort() 负责中断当前请求
 */
export interface IChatTransport {
  start(): Promise<void>
  abort(): void
}
export interface RequestDispatcherOptions {
  url: string
  method?: string
  headers?: Record<string, string>
  body?: any
  mode: ResponseMode
  // StreamManager 需要的回调函数（可选）
  onStatusChange?: (status: StreamStatus) => void
  onOpen?: () => void
  onMessage?: (chunk: string) => void
  onDone?: () => void
  onError?: (err: unknown) => void
}

export interface IRequestDispatcher {
  send(options: RequestDispatcherOptions): IChatTransport
}

export type NormalizedBlock =
  | {
      type: 'text' | 'reasoning_content'
      content: string
    }
  | {
      type: 'code'
      content: string
      lang?: string
    }
  | {
      type: 'image'
      url: string
      alt?: string
    }