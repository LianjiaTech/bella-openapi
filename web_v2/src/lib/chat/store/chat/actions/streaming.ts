/**
 * Streaming Actions - 流式消息处理
 */

import { nanoid } from 'nanoid'
import type { ChatMessage, ChatStoreState, MessageBlock } from '../../types'
import { SEGMENT_BUFFER_SIZE, isTextBlock } from '../../types'

/**
 * 开始助手消息（创建空消息,准备接收流）
 *
 * 使用 immer 直接修改 draft:
 * - 直接 push 到 messageIds 数组
 * - 直接赋值 messageMap[messageId]
 * - immer 自动处理不可变更新,只更新变化的引用
 */
export const startAssistantMessage = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (): string => {
  const messageId = nanoid()

  const newMessage: ChatMessage = {
    id: messageId,
    role: 'assistant',
    blocks: [],
    status: 'connecting',
    created: Date.now(),
  }

  set((state) => {
    state.messageIds.push(messageId)
    state.messageMap[messageId] = newMessage
    state.streamingMessageId = messageId
    state.streamingBlockId = undefined
  })

  return messageId
}

/**
 * 开始新 Block（在当前流式消息中）
 *
 * 使用 immer 直接修改 draft:
 * - 直接 push 到 message.blocks 数组
 * - 直接赋值 streamingBlockId
 * - immer 自动处理不可变更新
 */
export const startBlock = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (type: 'text' | 'reasoning_content' | 'code' | 'image', lang?: string): string => {
  const { streamingMessageId } = get()

  // 校验:必须有正在流式的消息
  if (!streamingMessageId) {
    console.warn('[startBlock] 没有流信息')
    return ''
  }

  const blockId = nanoid()

  set((state) => {
    const message = state.messageMap[streamingMessageId]
    if (!message) return

    // 创建新 Block
    let newBlock: MessageBlock
    if (type === 'image') {
      // 图片 Block（url 稍后通过 addImageBlock 更新）
      newBlock = {
        id: blockId,
        type: 'image',
        url: '',
        status: 'loading',
      }
    } else {
      // 文本类 Block（text/reasoning_code/code）
      newBlock = {
        id: blockId,
        type,
        lang,
        segments: [],
        segmentBuffer: '',
        typingBuffer: '',
        length: 0,
        isRenderingCompleted: false,
      }
    }

    // 添加到当前消息的 blocks
    message.blocks.push(newBlock)
    state.streamingBlockId = blockId
  })

  return blockId
}

/**
 * 追加文本 token（到当前流式 Block）
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 block 的属性（typingBuffer, segmentBuffer, length）
 * - 直接 push 到 segments 数组
 * - immer 自动追踪修改,只更新 streamingMessageId 对应的 message 引用
 *
 * 避免 re-render：
 * - immer 确保只有 messageMap[streamingMessageId] 的引用变化
 * - 其他 message 的引用保持不变，不会触发 re-render
 */
export const appendTextToken = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (token: string): void => {
  const { streamingMessageId, streamingBlockId } = get()

  // 校验:必须有正在流式的消息和 block
  if (!streamingMessageId || !streamingBlockId) {
    console.warn('[appendTextToken] No streaming message or block')
    return
  }

  set((state) => {
    const message = state.messageMap[streamingMessageId]
    if (!message) return

    // 找到当前流式 block
    const block = message.blocks.find((b) => b.id === streamingBlockId)
    if (!block || !isTextBlock(block)) return

    // 直接修改 block 属性（immer draft）
    block.typingBuffer += token
    block.segmentBuffer += token
    block.length += token.length

    // 判断是否需要 flush segments
    if (block.segmentBuffer.length >= SEGMENT_BUFFER_SIZE) {
      block.segments.push(block.segmentBuffer)
      block.segmentBuffer = ''
    }
  })
}

/**
 * 添加图片 Block
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 block 的 url 和 status
 */
export const addImageBlock = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (url: string): void => {
  const { streamingMessageId, streamingBlockId } = get()

  // 校验:必须有正在流式的消息和 block
  if (!streamingMessageId || !streamingBlockId) {
    console.warn('[addImageBlock] No streaming message or block')
    return
  }

  set((state) => {
    const message = state.messageMap[streamingMessageId]
    if (!message) return

    // 找到当前流式 block
    const block = message.blocks.find((b) => b.id === streamingBlockId)
    if (!block || block.type !== 'image') return

    // 设置图片 URL
    block.url = url
    block.status = 'done'
  })
}

/**
 * 完成当前流式消息
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 block 的 segments, segmentBuffer, typingBuffer
 * - 直接修改 message.status
 * - 直接赋值 streamingMessageId 和 streamingBlockId
 */
export const finishStreaming = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (): void => {
  const { streamingMessageId, streamingBlockId } = get()

  // 如果没有正在流式的消息,直接返回
  if (!streamingMessageId) {
    return
  }

  set((state) => {
    const message = state.messageMap[streamingMessageId]
    if (!message) return

    // 如果有正在流式的 block,flush segmentBuffer 并清空 typingBuffer
    if (streamingBlockId) {
      const block = message.blocks.find((b) => b.id === streamingBlockId)
      if (block && isTextBlock(block)) {
        // 1. flush segmentBuffer 到 segments
        if (block.segmentBuffer.length > 0) {
          block.segments.push(block.segmentBuffer)
          block.segmentBuffer = ''
        }
        // 2. 清空 typingBuffer
        block.typingBuffer = ''
      }
    }

    // 3. 设置 message.status = 'done'
    console.log('---设置 message.status = done ====', message)
    message.status = message.status === 'error' ? 'error' : 'done'

    // 4. 清空 streamingMessageId
    state.streamingMessageId = undefined
    // 5. 清空 streamingBlockId
    state.streamingBlockId = undefined
  })
}
