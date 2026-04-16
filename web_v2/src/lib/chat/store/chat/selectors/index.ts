/**
 * Selectors - 便于组件订阅
 */

import type { ChatStoreState, ChatStoreActions } from '../../types'

type ChatStore = ChatStoreState & ChatStoreActions

/**
 * 获取所有消息 ID 列表
 */
export const selectMessageIds = (state: ChatStore) => state.messageIds

/**
 * 获取指定消息
 */
export const selectMessage = (messageId: string) => (state: ChatStore) =>
  state.messageMap[messageId]

/**
 * 获取当前是否正在流式输出
 */
export const selectIsStreaming = (state: ChatStore) =>
  state.streamingMessageId !== undefined
