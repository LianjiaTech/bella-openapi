/**
 * Zustand ChatStore Implementation
 *
 * 职责：
 * 1. 创建 Zustand store
 * 2. 组装 state 和 actions
 * 3. 重新导出 selectors
 *
 * 设计原则：
 * - 只负责 store 创建和组装，不实现具体逻辑
 * - 所有 action 实现从 ./actions/ 导入
 * - 所有 selector 从 ./selectors/ 重新导出
 * - 使用 immer 中间件自动处理不可变更新，避免手动展开运算符导致的过度 re-render
 */

// 导入 Immer 配置，启用 MapSet 插件支持
import '../immer-config'
import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import type { ChatStoreState } from '../types'
import type { ChatStoreActions } from '../types'
import {
  addUserMessage,
  deleteMessage,
  clearMessages,
  getMessage,
  setMessageStatus,
} from './actions/message'
import {
  startAssistantMessage,
  startBlock,
  appendTextToken,
  addImageBlock,
  finishStreaming,
} from './actions/streaming'
import {
  setMessageError,
} from './actions/error'
import {
  startBlockRendering,
  finishBlockRendering,
  updateRenderingLength,
  stopAllRendering,
  updateBlockRenderingCompleted,
} from './actions/rendering'
import {
  writeAssistantBlocks,
  writeAssistantError,
} from './actions/json'

type ChatStore = ChatStoreState & ChatStoreActions

const initialState: ChatStoreState = {
  messageIds: [],
  messageMap: {},
  streamingMessageId: undefined,
  streamingBlockId: undefined,
  renderingBlockIds: new Set(),
  renderingLengths: {},
}

export const useChatStore = create<ChatStore>()(
  immer((set, get) => ({
    // State
    ...initialState,

    // Actions - 从 actions/ 文件夹注入实现
    addUserMessage: addUserMessage(set, get),
    startAssistantMessage: startAssistantMessage(set, get),
    startBlock: startBlock(set, get),
    appendTextToken: appendTextToken(set, get),
    addImageBlock: addImageBlock(set, get),
    finishStreaming: finishStreaming(set, get),
    setMessageError: setMessageError(set, get),
    clearMessages: clearMessages(set, get),
    deleteMessage: deleteMessage(set, get),
    getMessage: getMessage(set, get),
    setMessageStatus: setMessageStatus(set),
    startBlockRendering: startBlockRendering(set, get),
    finishBlockRendering: finishBlockRendering(set, get),
    updateRenderingLength: updateRenderingLength(set, get),
    stopAllRendering: stopAllRendering(set, get),
    updateBlockRenderingCompleted: updateBlockRenderingCompleted(set),
    writeAssistantBlocks: writeAssistantBlocks(set, get),
    writeAssistantError: writeAssistantError(set, get)
  }))
)

// ============================================================================
// Selectors - 从 selectors/ 重新导出
// ============================================================================
export * from './selectors'
