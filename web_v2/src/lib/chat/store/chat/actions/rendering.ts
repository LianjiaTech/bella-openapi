/**
 * Rendering Actions - 打字机渲染状态管理
 *
 * 职责：
 * 1. 跟踪哪些 block 正在进行打字机动画
 * 2. 记录每个 block 的当前渲染位置
 * 3. 提供停止所有渲染的能力（保留已渲染内容）
 *
 * 设计原则：
 * - renderingBlockIds: 用于快速判断"是否有任何 block 在渲染"
 * - renderingLengths: 用于停止渲染时精确截断内容
 * - stopAllRendering: 将未渲染的内容丢弃，保留已显示的部分
 */

import type { ChatStoreState } from '../../types'

/**
 * 注册 block 开始打字机渲染
 *
 * 使用场景：
 * - MarkdownRenderer/ReasoningBlock 检测到 isTyping = true 时调用
 *
 * 使用 immer 直接修改 draft:
 * - 直接调用 Set.add()
 */
export const startBlockRendering = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (blockId: string): void => {
  set((state) => {
    state.renderingBlockIds.add(blockId)
  })
}

/**
 * 注销 block 完成打字机渲染
 *
 * 使用场景：
 * - MarkdownRenderer 检测到 isTyping = false 时调用
 * - 组件卸载时调用（cleanup）
 *
 * 使用 immer 直接修改 draft:
 * - 直接调用 Set.delete()
 * - 直接删除 renderingLengths 中的记录
 */
export const finishBlockRendering = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (blockId: string): void => {
  set((state) => {
    state.renderingBlockIds.delete(blockId)
    delete state.renderingLengths[blockId]
  })
}

/**
 * 更新 block 的当前渲染长度
 *
 * 使用场景：
 * - MarkdownRenderer 每次 render 时调用，上报当前显示到哪里了
 *
 * 使用 immer 直接修改 draft:
 * - 直接赋值 renderingLengths[blockId]
 */
export const updateRenderingLength = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (blockId: string, length: number): void => {
  set((state) => {
    state.renderingLengths[blockId] = length
  })
}

/**
 * 停止所有打字机渲染
 *
 * 使用场景：
 * - 用户点击停止按钮时调用
 *
 * 行为：
 * 1. 清空 renderingBlockIds（停止跟踪）
 * 2. 遍历所有 block，将内容截断到当前渲染位置
 * 3. 将已渲染的内容 flush 到 segments
 * 4. 丢弃 typingBuffer 中未渲染的部分
 * 5. 清空 renderingLengths
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 block 的 segments/segmentBuffer/typingBuffer
 * - 直接调用 Set.clear() 和对象赋值
 */
export const stopAllRendering = (
  set: (fn: (state: ChatStoreState) => void) => void,
  get: () => ChatStoreState
) => (): void => {
  const { renderingLengths } = get()

  set((state) => {
    // 1. 清空渲染中的 block IDs
    state.renderingBlockIds.clear()

    // 2. 遍历所有消息，截断正在渲染的 block
    Object.values(state.messageMap).forEach((message) => {
      message.blocks.forEach((block) => {
        // 跳过图片 block
        if (block.type === 'image') return

        // 获取当前渲染长度
        const currentLength = renderingLengths[block.id]
        if (currentLength === undefined) return

        // 拼接完整内容
        const fullContent =
          block.segments.join('') + block.segmentBuffer + block.typingBuffer

        // 截取已渲染的部分
        const renderedContent = fullContent.slice(0, currentLength)

        // 更新 block：只保留已渲染的内容
        block.segments = [renderedContent]
        block.segmentBuffer = ''
        block.typingBuffer = ''
        block.length = currentLength
      })
    })

    // 3. 清空渲染长度记录
    state.renderingLengths = {}
  })
}

/**
 * 更新 block 的 isRenderingCompleted 状态
 *
 * 使用场景：
 * - MarkdownRenderer 打字机动画完成时调用
 *
 * 行为：
 * - 遍历 messageMap 找到对应的 block
 * - 将其 isRenderingCompleted 设置为 true
 * - 找到目标后立即退出，避免不必要的遍历
 *
 * 使用 immer 直接修改 draft:
 * - 直接修改 block.isRenderingCompleted
 */
export const updateBlockRenderingCompleted = (
  set: (fn: (state: ChatStoreState) => void) => void
) => (blockId: string, completed: boolean): void => {
  set((state) => {
    // 🔍 如果在流式消息中没找到，再遍历所有消息
    for (const message of Object.values(state.messageMap)) {
      for (const block of message.blocks) {
        if (block.type !== 'image' && block.id === blockId) {
          block.isRenderingCompleted = completed
          return // ✅ 找到目标，立即退出
        }
      }
    }
  })
}
