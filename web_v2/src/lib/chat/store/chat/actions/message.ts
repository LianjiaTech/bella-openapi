/**
 * Message Actions - 消息 CRUD 操作
 */

import { nanoid } from 'nanoid'
import type { ChatMessage, ChatStoreState, MessageBlock } from '../../types'

/**
 * 内容块类型定义（与 useChatInput.ts 保持一致）
 */
export type ContentPart =
  | { type: 'text'; text: string }
  | { type: 'image_url'; image_url: { url: string } }
  | { type: 'video_url'; video_url: { url: string }; fps?: string }

/**
 * 添加用户消息
 *
 * 职责：
 * - 支持纯文本（string）和多模态（ContentPart[]）两种输入格式
 * - 将 ContentPart[] 转换为 MessageBlock[] 存储到 Store
 * - 自动生成消息 ID 和 block ID
 *
 * 数据转换：
 * - string → 单个 TextBlock
 * - ContentPart[] → 多个 TextBlock/ImageBlock
 *   - type: 'text' → TextBlock
 *   - type: 'image_url' → ImageBlock
 *   - type: 'video_url' → ImageBlock (暂时复用，URL 中包含视频信息)
 *
 * 使用 immer 直接修改 draft:
 * - 直接 push 到 messageIds 数组
 * - 直接赋值 messageMap[messageId]
 */
export const addUserMessage = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (content: string | ContentPart[]): string => {
  const messageId = nanoid()

  // 根据 content 类型构建 blocks
  let blocks: MessageBlock[]

  if (typeof content === 'string') {
    // 纯文本：创建单个 TextBlock
    blocks = [
      {
        id: nanoid(),
        type: 'text',
        segments: [content],
        segmentBuffer: '',
        typingBuffer: '',
        length: content.length,
        isRenderingCompleted: false,
      },
    ]
  } else {
    // 多模态：将 ContentPart[] 转换为 MessageBlock[]
    blocks = content.map((part) => {
      if (part.type === 'text') {
        // 文本块
        return {
          id: nanoid(),
          type: 'text' as const,
          segments: [part.text],
          segmentBuffer: '',
          typingBuffer: '',
          length: part.text.length,
          isRenderingCompleted: false,
        }
      } else if (part.type === 'image_url') {
        // 图片块
        return {
          id: nanoid(),
          type: 'image' as const,
          url: part.image_url.url,
          status: 'done' as const,
        }
      } else if (part.type === 'video_url') {
        // 视频块（独立的 VideoBlock 类型）
        return {
          id: nanoid(),
          type: 'video' as const,
          url: part.video_url.url,
          fps: part.fps,  // 保存帧率信息
          status: 'done' as const,
        }
      }
      // 类型安全：永远不会到达这里
      throw new Error(`Unknown content part type: ${(part as any).type}`)
    })
  }

  const newMessage: ChatMessage = {
    id: messageId,
    role: 'user',
    blocks,
    status: 'done',
    created: Date.now(),
  }

  set((state) => {
    state.messageIds.push(messageId)
    state.messageMap[messageId] = newMessage
  })

  return messageId
}

/**
 * 删除指定消息
 *
 * 使用 immer 直接修改 draft:
 * - 直接过滤 messageIds 数组
 * - 直接 delete messageMap[messageId]
 */
export const deleteMessage = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (messageId: string): void => {
  set((state) => {
    state.messageIds = state.messageIds.filter((id) => id !== messageId)
    delete state.messageMap[messageId]
  })
}

/**
 * 清空所有消息
 *
 * 职责：
 * 1. 终止正在进行的 SSE 流（如果有）
 * 2. 清空所有消息数据和流状态
 * 3. 清空渲染状态（虽然 React 会自动清理，但显式清理更安全）
 *
 * 使用 immer 直接修改 draft:
 * - 直接重置所有状态为初始值
 *
 * 避免 re-render：
 * - 使用 get() 获取 abortStream 引用，不触发订阅
 * - abort 操作本身不更新 store 状态
 * - 所有状态更新在一个 set() 调用中批量完成
 */
export const clearMessages = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (): void => {
  const state = get()

  // 1. 先终止正在进行的 SSE 连接（如果有）
  if (state.abortStream) {
    state.abortStream()
  }

  // 2. 清空所有数据（React 会自动卸载组件和清理渲染状态）
  set((state) => {
    state.messageIds = []
    state.messageMap = {}
    state.streamingMessageId = undefined
    state.streamingBlockId = undefined
    state.renderingBlockIds.clear()
    state.renderingLengths = {}
  })
}

/**
 * 获取指定消息（selector 辅助）
 */
export const getMessage = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (messageId: string): ChatMessage | undefined => {
  return get().messageMap[messageId]
}

/**
 * 设置消息状态
 *
 * 职责：
 * 更新指定消息的 status 字段
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 messageMap[messageId].status
 *
 * 避免 re-render：
 * - 只更新指定消息的引用，其他消息不受影响
 *
 * @param messageId - 消息 ID
 * @param status - 新的状态值
 */
export const setMessageStatus = (
  set: (fn: (state: ChatStoreState) => void) => void
) => (messageId: string, status: ChatMessage['status']): void => {
  set((state) => {
    const message = state.messageMap[messageId]
    if (message) {
      message.status = status
    }
  })
}
