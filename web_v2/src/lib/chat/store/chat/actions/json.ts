/**
 * JSON Actions - 非流式 JSON 写入
 *
 * 职责：
 * 1. 一次性写入 assistant blocks
 * 2. 支持 meta 信息（usage / finishReason / model）
 * 3. 支持错误消息落盘
 *
 * 设计原则：
 * - 不使用 appendTextToken（避免三层缓冲逻辑）
 * - 直接构造完整 TextBlock
 * - 使用 immer 直接修改 draft
 */

import { nanoid } from 'nanoid'
import type {
  ChatStoreState,
  ChatMessage,
  MessageBlock,
  TextBlock,
  TokenUsage,
} from '../../types'

/**
 * 一次性写入 assistant blocks（JSON 模式）
 */
export const writeAssistantBlocks =
  (
    set: (fn: (state: ChatStoreState) => void) => void,
    get: () => ChatStoreState
  ) =>
  (
    blocks: {
      type: 'text' | 'reasoning_content' | 'code' | 'image'
      content?: string
      lang?: string
      url?: string
      alt?: string
    }[],
    meta?: {
      finishReason?: ChatMessage['finishReason']
      usage?: TokenUsage
      model?: string
    }
  ): string => {
    const messageId = nanoid()

    const messageBlocks: MessageBlock[] = blocks.map((block) => {
      if (block.type === 'image') {
        return {
          id: nanoid(),
          type: 'image',
          url: block.url || '',
          status: 'done',
          alt: block.alt,
        }
      }

      // 文本类 block
      const content = block.content || ''

      const textBlock: TextBlock = {
        id: nanoid(),
        type: block.type,
        lang: block.lang,
        segments: content ? [content] : [],
        segmentBuffer: '',
        typingBuffer: '',
        length: content.length,
        isRenderingCompleted: false,
      }

      return textBlock
    })

    const newMessage: ChatMessage = {
      id: messageId,
      role: 'assistant',
      blocks: messageBlocks,
      status: 'done',
      created: Date.now(),
      finishReason: meta?.finishReason ?? null,
      usage: meta?.usage,
      model: meta?.model,
    }

    set((state) => {
      state.messageIds.push(messageId)
      state.messageMap[messageId] = newMessage

      // JSON 模式不应该存在 streaming 状态
      state.streamingMessageId = undefined
      state.streamingBlockId = undefined
    })

    return messageId
  }

/**
 * 写入 assistant 错误消息
 *
 * 错误必须落成一条 assistant message（你的要求）
 */
export const writeAssistantError =
  (
    set: (fn: (state: ChatStoreState) => void) => void,
    get: () => ChatStoreState
  ) =>
  (errorMessage: string): string => {
    const messageId = nanoid()

    const textBlock: TextBlock = {
      id: nanoid(),
      type: 'text',
      segments: [errorMessage],
      segmentBuffer: '',
      typingBuffer: '',
      length: errorMessage.length,
      isRenderingCompleted: false,
    }

    const newMessage: ChatMessage = {
      id: messageId,
      role: 'assistant',
      blocks: [textBlock],
      status: 'error',
      created: Date.now(),
      error: {
        code: 'JSON_REQUEST_ERROR',
        httpCode: 500,
        message: errorMessage,
      },
    }

    set((state) => {
      state.messageIds.push(messageId)
      state.messageMap[messageId] = newMessage

      state.streamingMessageId = undefined
      state.streamingBlockId = undefined
    })

    return messageId
  }