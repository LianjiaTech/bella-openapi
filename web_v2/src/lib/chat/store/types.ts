/**
 * Zustand ChatStore Types (多模态 + SSE 流式)
 *
 * 设计原则：
 * 1. messageIds + messageMap 解耦顺序与内容
 * 2. Block 化多模态结构（text/code/thinking/image）
 * 3. 三层缓冲（typing/segment/segments）
 * 4. 支持按 message 粒度订阅
 */

// ============================================================================
// Block Types（多模态 Block）
// ============================================================================

/**
 * TextBlock - 可流式的文本 Block
 *
 * 三层缓冲架构：
 * - typingBuffer: 🥉 打字机层（高频/逐字感）
 * - segmentBuffer: 🥈 聚合层（2KB flush）
 * - segments: 🥇 持久层（低频写入）
 */
import type { StreamStatus } from '@/lib/types/chat';
export interface TextBlock {
  /** Block 唯一标识 */
  id: string

  /** Block 类型 */
  type: 'text' | 'reasoning_content' | 'code'

  /** 代码语言（仅 type=code 时有效） */
  lang?: string

  /** 🥇 持久层：已固化的文本段数组（低频写入） */
  segments: string[]

  /** 🥈 聚合层：2KB 缓冲区（达到阈值后 flush 到 segments） */
  segmentBuffer: string

  /** 🥉 打字机层：逐字追加缓冲（高频写入，给 UI 展示打字机效果） */
  typingBuffer: string

  /** 累计长度（避免每次 join 计算） */
  length: number

  isRenderingCompleted: boolean
}

/**
 * ImageBlock - 图片 Block
 */
export interface ImageBlock {
  /** Block 唯一标识 */
  id: string

  /** Block 类型 */
  type: 'image'

  /** 图片 URL */
  url: string

  /** 加载状态（可选） */
  status?: 'loading' | 'done'
  alt?: string
}

/**
 * VideoBlock - 视频 Block
 *
 * 设计说明：
 * - 独立于 ImageBlock，支持视频特有的元数据
 * - thumbnail: 视频缩略图 URL（用于预览）
 * - duration: 视频时长（秒），用于显示播放进度
 * - fps: 帧率信息（可选）
 */
export interface VideoBlock {
  /** Block 唯一标识 */
  id: string

  /** Block 类型 */
  type: 'video'

  /** 视频 URL（支持 base64/blob/CDN） */
  url: string

  /** 视频缩略图 URL（可选） */
  thumbnail?: string

  /** 视频时长（秒） */
  duration?: number

  /** 帧率信息（可选） */
  fps?: string

  /** 加载状态（可选） */
  status?: 'loading' | 'done'

  /** 描述文本（可选） */
  alt?: string
}

/**
 * MessageBlock - 多模态 Block 联合类型
 */
export type MessageBlock = TextBlock | ImageBlock | VideoBlock

// ============================================================================
// Message Types
// ============================================================================

/**
 * 消息角色
 */
export type MessageRole = 'user' | 'assistant' | 'system'

/**
 * 消息状态
 */
export type MessageStatus = StreamStatus

/**
 * ChatMessage - 单条消息
 *
 * 采用 Block 化多模态结构，支持：
 * - 文本/代码/思考流式输出
 * - 图片等非流式内容
 * - 未来扩展音频/视频等
 */
export interface ChatMessage {
  /** 消息唯一标识 */
  id: string

  /** 消息角色 */
  role: MessageRole

  /** Block 数组（多模态内容） */
  blocks: MessageBlock[]

  /** 消息状态 */
  status: MessageStatus
  /** ===== 元信息 ===== */

  model?: string
  created?: number

  /** 结束原因 */
  finishReason?: 'stop' | 'length' | 'function_call' | 'content_filter' | null

  /** token 使用 */
  usage?: TokenUsage

  /** 错误信息 */
  error?: OpenapiError

  /** 安全检查 */
  sensitives?: any
  requestRiskData?: any
}

// ============================================================================
// Store State
// ============================================================================

/**
 * ChatStoreState - 顶层 Store 状态
 *
 * 设计要点：
 * 1. messageIds：顺序稳定，适配虚拟列表，除新增外不变
 * 2. messageMap：内容仓库，流式 token 只改 map 不改 ids
 * 3. streaming 定位：单流版本，未来可扩展为 map 支持多流
 */
export interface ChatStoreState {
  /** 消息 ID 顺序数组（稳定顺序，适配虚拟列表） */
  messageIds: string[]

  /** 消息内容仓库（O(1) 查找，流式更新只改 map） */
  messageMap: Record<string, ChatMessage>

  /** 当前流式消息 ID（单流版本） */
  streamingMessageId?: string

  /** 当前流式 Block ID（单流版本） */
  streamingBlockId?: string

  /** 正在渲染打字机动画的 block IDs */
  renderingBlockIds: Set<string>

  /** 每个 block 的当前渲染长度（用于停止渲染时截断内容） */
  renderingLengths: Record<string, number>

  /** 终止当前 SSE 流的方法（由 useChatStream hook 注入） */
  abortStream?: () => void
}

// ============================================================================
// Type Guards（类型守卫，便于业务层使用）
// ============================================================================

/**
 * 判断是否为 TextBlock
 */
export function isTextBlock(block: MessageBlock): block is TextBlock {
  return block.type === 'text' || block.type === 'reasoning_content' || block.type === 'code'
}

/**
 * 判断是否为 ImageBlock
 */
export function isImageBlock(block: MessageBlock): block is ImageBlock {
  return block.type === 'image'
}

/**
 * 判断是否为 VideoBlock
 */
export function isVideoBlock(block: MessageBlock): block is VideoBlock {
  return block.type === 'video'
}

// ============================================================================
// Constants（常量定义）
// ============================================================================

/**
 * 聚合层缓冲区阈值：2KB
 *
 * 估算：4MB 文本 ≈ 2000 segments（可控）
 */
export const SEGMENT_BUFFER_SIZE = 2 * 1024 // 2KB

/**
 * Token 使用统计
 */
export interface TokenUsage {
  /** 输入 token 数 */
  prompt_tokens: number

  /** 输出 token 数 */
  completion_tokens: number

  /** 总 token 数 */
  total_tokens: number

  /** 缓存创建 token */
  cache_creation_tokens?: number

  /** 缓存读取 token */
  cache_read_tokens?: number

  /** 输出 token 详情 */
  completion_tokens_details?: TokenDetail

  /** 输入 token 详情 */
  prompt_tokens_details?: TokenDetail
}

/**
 * Token 详细统计
 */
export interface TokenDetail {
  reasoning_tokens?: number
  cached_tokens?: number
  cache_creation_tokens?: number
  audio_tokens?: number
  image_tokens?: number
}
/**
 * OpenAPI 错误信息
 */
export interface OpenapiError {
  /** 错误代码 */
  code: string

  /** HTTP 状态码 */
  httpCode: number

  /** 错误描述 */
  message: string

  /** 错误类型 */
  type?: string

  /** 相关参数 */
  param?: string

  /** 敏感信息（安全检查） */
  sensitive?: any
}

/**
 * 内容块类型定义（多模态输入）
 */
export type ContentPart =
  | { type: 'text'; text: string }
  | { type: 'image_url'; image_url: { url: string } }
  | { type: 'video_url'; video_url: { url: string }; fps?: string }

export interface ChatStoreActions {
  /**
   * 添加用户消息
   * @param content - 用户输入内容（支持纯文本或多模态）
   * @returns 新消息 ID
   */
  addUserMessage: (content: string | ContentPart[]) => string

  /**
   * 开始助手消息（创建空消息，准备接收流）
   * @returns 新消息 ID
   */
  startAssistantMessage: () => string

  /**
   * 开始新 Block（在当前流式消息中）
   * @param type - Block 类型
   * @param lang - 代码语言（可选）
   * @returns 新 Block ID
   */
  startBlock: (type: 'text' | 'reasoning_content' | 'code' | 'image', lang?: string) => string

  /**
   * 追加文本 token（到当前流式 Block）
   * @param token - 文本片段
   */
  appendTextToken: (token: string) => void

  /**
   * 添加图片 Block
   * @param url - 图片 URL
   */
  addImageBlock: (url: string) => void

  /**
   * 完成当前流式消息
   */
  finishStreaming: () => void

  /**
   * 设置消息错误状态
   * @param messageId - 消息 ID
   * @param error - 错误信息
   */
  setMessageError: (messageId: string, error: string) => void

  /**
   * 清空所有消息
   */
  clearMessages: () => void

  /**
   * 删除指定消息
   * @param messageId - 消息 ID
   */
  deleteMessage: (messageId: string) => void

  /**
   * 获取指定消息（selector 辅助）
   * @param messageId - 消息 ID
   */
  getMessage: (messageId: string) => ChatMessage | undefined

  /**
   * 设置消息状态
   * @param messageId - 消息 ID
   * @param status - 新的状态值
   */
  setMessageStatus: (messageId: string, status: MessageStatus) => void

  /**
   * 注册 block 开始打字机渲染
   * @param blockId - Block ID
   */
  startBlockRendering: (blockId: string) => void

  /**
   * 注销 block 完成打字机渲染
   * @param blockId - Block ID
   */
  finishBlockRendering: (blockId: string) => void

  /**
   * 更新 block 的当前渲染长度
   * @param blockId - Block ID
   * @param length - 当前渲染的字符长度
   */
  updateRenderingLength: (blockId: string, length: number) => void

  /**
   * 更新 block 的 isRenderingCompleted 状态
   * @param blockId - Block ID
   * @param completed - 是否完成渲染
   */
  updateBlockRenderingCompleted: (blockId: string, completed: boolean) => void

  /**
   * 停止所有打字机渲染（保留已渲染内容，丢弃未渲染部分）
   */
  stopAllRendering: () => void
}