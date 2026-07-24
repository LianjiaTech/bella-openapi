/**
 * Error Actions - 错误处理
 */

import type { ChatStoreState } from '../../types'

/**
 * 设置消息错误状态
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 message.status 和 message.error
 */
export const setMessageError = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (messageId: string, error: string): void => {
  set((state) => {
    const message = state.messageMap[messageId]
    if (!message) return

    // 设置错误信息
    message.status = 'error'
    message.error = {
      code: 'UNKNOWN_ERROR',
      httpCode: 500,
      message: error,
    }
  })
}
