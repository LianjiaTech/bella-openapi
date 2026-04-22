/**
 * ChatStore 单元测试
 *
 * 测试内容：
 * 1. appendToken 10000次性能测试
 * 2. segmentBuffer flush 机制测试
 * 3. finishStreaming 流程测试
 * 4. message 状态变化测试
 */

import { useChatStore } from '../store'
import { SEGMENT_BUFFER_SIZE } from '../../types'
import type { TextBlock } from '../../types'

// Mock nanoid 生成固定 ID，便于测试
let mockIdCounter = 0
jest.mock('nanoid', () => ({
  nanoid: jest.fn(() => `mock-id-${++mockIdCounter}`),
}))

describe('ChatStore 单元测试', () => {
  // 每个测试前重置 store 和 counter
  beforeEach(() => {
    mockIdCounter = 0
    useChatStore.setState({
      messageIds: [],
      messageMap: {},
      streamingMessageId: undefined,
      streamingBlockId: undefined,
    })
  })

  describe('1. appendToken 10000次性能测试', () => {
    it('应该能够高效处理 10000 次 token 追加', () => {
      // 1. 创建流式消息
      useChatStore.getState().startAssistantMessage()

      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!
      expect(messageId).toBeTruthy()

      // 2. 开始 text block
      useChatStore.getState().startBlock('text')

      state = useChatStore.getState()
      const blockId = state.streamingBlockId!
      expect(blockId).toBeTruthy()

      // 3. 追加 10000 次 token
      const startTime = performance.now()
      const tokenContent = 'x' // 每次追加 1 个字符

      for (let i = 0; i < 10000; i++) {
        useChatStore.getState().appendTextToken(tokenContent)
      }

      const endTime = performance.now()
      const duration = endTime - startTime

      // 4. 验证结果
      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      expect(message).toBeDefined()
      expect(message.blocks.length).toBe(1)

      const block = message.blocks[0] as TextBlock
      expect(block.type).toBe('text')

      // 验证 typingBuffer 累积了所有 token
      expect(block.typingBuffer.length).toBe(10000)
      expect(block.typingBuffer).toBe('x'.repeat(10000))

      // 验证 length 累积正确
      expect(block.length).toBe(10000)

      // 验证 segments 数量（10000 / 2048 ≈ 4 个 segments）
      const expectedSegments = Math.floor(10000 / SEGMENT_BUFFER_SIZE)
      expect(block.segments.length).toBe(expectedSegments)

      // 验证 segmentBuffer 剩余内容
      const remainingChars = 10000 % SEGMENT_BUFFER_SIZE
      expect(block.segmentBuffer.length).toBe(remainingChars)

      // 验证总内容长度
      const totalSegmentsLength = block.segments.join('').length
      const totalLength = totalSegmentsLength + block.segmentBuffer.length
      expect(totalLength).toBe(10000)

      // 性能检查：10000 次操作应在合理时间内完成（< 100ms）
      console.log(`10000 次 appendToken 耗时: ${duration.toFixed(2)}ms`)
      expect(duration).toBeLessThan(100)
    })

    it('应该能够处理多字符 token 的 10000 次追加', () => {
      useChatStore.getState().startAssistantMessage()

      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 使用 3 字符的 token
      const token = 'abc'
      const iterations = 10000

      for (let i = 0; i < iterations; i++) {
        useChatStore.getState().appendTextToken(token)
      }

      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      const block = message.blocks[0] as TextBlock

      // 总字符数 = 3 * 10000 = 30000
      expect(block.length).toBe(token.length * iterations)
      expect(block.typingBuffer.length).toBe(token.length * iterations)

      // 验证内容正确性 - typingBuffer 包含所有内容
      expect(block.typingBuffer).toBe(token.repeat(iterations))

      // segments + segmentBuffer 的总长度应该等于 length
      const segmentsAndBufferLength = block.segments.join('').length + block.segmentBuffer.length
      expect(segmentsAndBufferLength).toBe(token.length * iterations)
    })
  })

  describe('2. segmentBuffer flush 机制测试', () => {
    it('应该在 segmentBuffer 达到 2KB 时自动 flush 到 segments', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 创建一个刚好达到阈值的字符串
      const token = 'a'.repeat(SEGMENT_BUFFER_SIZE)

      // 追加第一次，应该触发 flush
      useChatStore.getState().appendTextToken(token)

      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      let block = message.blocks[0] as TextBlock

      // 验证 flush 发生
      expect(block.segments.length).toBe(1)
      expect(block.segments[0]).toBe(token)
      expect(block.segmentBuffer).toBe('')
      expect(block.typingBuffer.length).toBe(SEGMENT_BUFFER_SIZE)

      // 追加第二次，再次触发 flush
      useChatStore.getState().appendTextToken(token)

      state = useChatStore.getState()
      message = state.messageMap[messageId]
      block = message.blocks[0] as TextBlock

      expect(block.segments.length).toBe(2)
      expect(block.segments[1]).toBe(token)
      expect(block.segmentBuffer).toBe('')
      expect(block.typingBuffer.length).toBe(SEGMENT_BUFFER_SIZE * 2)
    })

    it('应该在 segmentBuffer 超过 2KB 时立即 flush', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 先追加一些内容，但不达到阈值
      const smallToken = 'a'.repeat(1000)
      useChatStore.getState().appendTextToken(smallToken)

      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      let block = message.blocks[0] as TextBlock

      expect(block.segments.length).toBe(0)
      expect(block.segmentBuffer.length).toBe(1000)

      // 追加大 token，使 segmentBuffer 超过阈值
      const largeToken = 'b'.repeat(1500)
      useChatStore.getState().appendTextToken(largeToken)

      state = useChatStore.getState()
      message = state.messageMap[messageId]
      block = message.blocks[0] as TextBlock

      // 应该触发 flush
      expect(block.segments.length).toBe(1)
      expect(block.segments[0]).toBe(smallToken + largeToken)
      expect(block.segmentBuffer).toBe('')
    })

    it('应该处理多次小 token 累积后的 flush', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      const smallToken = 'x'.repeat(100) // 每次 100 字符
      const iterations = 25 // 25 * 100 = 2500 > 2048

      for (let i = 0; i < iterations; i++) {
        useChatStore.getState().appendTextToken(smallToken)
      }

      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      const block = message.blocks[0] as TextBlock

      // 应该至少有 1 个 segment
      expect(block.segments.length).toBeGreaterThanOrEqual(1)

      // 总长度应该正确
      const totalLength = block.segments.join('').length + block.segmentBuffer.length
      expect(totalLength).toBe(smallToken.length * iterations)
    })

    it('应该保持 typingBuffer 持续累积，不受 flush 影响', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 追加足够内容触发 flush
      const token = 'a'.repeat(SEGMENT_BUFFER_SIZE + 100)
      useChatStore.getState().appendTextToken(token)

      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      const block = message.blocks[0] as TextBlock

      // 当一次性追加超过阈值的内容时，整个 segmentBuffer 都会被 flush
      expect(block.segments.length).toBe(1)
      expect(block.segments[0].length).toBe(SEGMENT_BUFFER_SIZE + 100)
      expect(block.segmentBuffer.length).toBe(0) // 全部 flush 了

      // typingBuffer 应该包含全部内容（持续累积）
      expect(block.typingBuffer.length).toBe(SEGMENT_BUFFER_SIZE + 100)
      expect(block.typingBuffer).toBe(token)

      // 验证 typingBuffer 和 segments + segmentBuffer 包含相同内容
      const persistedContent = block.segments.join('') + block.segmentBuffer
      expect(block.typingBuffer).toBe(persistedContent)
    })
  })

  describe('3. finishStreaming 流程测试', () => {
    it('应该在流式结束时 flush segmentBuffer 到 segments', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 追加一些内容，但不达到 flush 阈值
      const content = 'Hello, World!'
      useChatStore.getState().appendTextToken(content)

      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      let block = message.blocks[0] as TextBlock

      // 此时 segmentBuffer 应该有内容
      expect(block.segmentBuffer).toBe(content)
      expect(block.segments.length).toBe(0)

      // 调用 finishStreaming
      useChatStore.getState().finishStreaming()

      state = useChatStore.getState()
      message = state.messageMap[messageId]
      block = message.blocks[0] as TextBlock

      // segmentBuffer 应该被 flush 到 segments
      expect(block.segments.length).toBe(1)
      expect(block.segments[0]).toBe(content)
      expect(block.segmentBuffer).toBe('')
    })

    it('应该清空 typingBuffer', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('Some content')

      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      let block = message.blocks[0] as TextBlock
      expect(block.typingBuffer).toBe('Some content')

      // 完成流式
      useChatStore.getState().finishStreaming()

      state = useChatStore.getState()
      message = state.messageMap[messageId]
      block = message.blocks[0] as TextBlock

      // typingBuffer 应该被清空
      expect(block.typingBuffer).toBe('')
    })

    it('应该清空 streamingMessageId 和 streamingBlockId', () => {
      useChatStore.getState().startAssistantMessage()
      useChatStore.getState().startBlock('text')

      let state = useChatStore.getState()
      expect(state.streamingMessageId).toBeTruthy()
      expect(state.streamingBlockId).toBeTruthy()

      // 完成流式
      useChatStore.getState().finishStreaming()

      // 应该清空
      state = useChatStore.getState()
      expect(state.streamingMessageId).toBeUndefined()
      expect(state.streamingBlockId).toBeUndefined()
    })

    it('应该处理空 segmentBuffer 的情况', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 追加刚好达到阈值的内容，触发自动 flush
      const token = 'a'.repeat(SEGMENT_BUFFER_SIZE)
      useChatStore.getState().appendTextToken(token)

      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      let block = message.blocks[0] as TextBlock

      // segmentBuffer 应该为空
      expect(block.segmentBuffer).toBe('')
      expect(block.segments.length).toBe(1)

      // 完成流式（segmentBuffer 为空）
      useChatStore.getState().finishStreaming()

      state = useChatStore.getState()
      message = state.messageMap[messageId]
      block = message.blocks[0] as TextBlock

      // 不应该添加空 segment
      expect(block.segments.length).toBe(1)
      expect(block.segmentBuffer).toBe('')
    })

    it('应该处理没有 streamingMessageId 的情况', () => {
      // 直接调用 finishStreaming，不应该报错
      expect(() => {
        useChatStore.getState().finishStreaming()
      }).not.toThrow()

      // store 状态应该保持稳定
      expect(useChatStore.getState().streamingMessageId).toBeUndefined()
    })
  })

  describe('4. message 状态变化测试', () => {
    it('应该在创建消息时设置 status 为 streaming', () => {
      useChatStore.getState().startAssistantMessage()

      const state = useChatStore.getState()
      const messageId = state.streamingMessageId!
      const message = state.messageMap[messageId]

      expect(message.status).toBe('streaming')
    })

    it('应该在 finishStreaming 后将 status 改为 done', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('Some content')

      // 完成前状态
      state = useChatStore.getState()
      let message = state.messageMap[messageId]
      expect(message.status).toBe('streaming')

      // 完成流式
      useChatStore.getState().finishStreaming()

      // 完成后状态
      state = useChatStore.getState()
      message = state.messageMap[messageId]
      expect(message.status).toBe('done')
    })

    it('应该完整测试从创建到完成的状态转换', () => {
      // 1. 创建消息 - status: streaming
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      expect(state.messageMap[messageId].status).toBe('streaming')
      expect(state.streamingMessageId).toBe(messageId)

      // 2. 开始 block
      useChatStore.getState().startBlock('text')
      state = useChatStore.getState()
      expect(state.streamingBlockId).toBeTruthy()

      // 3. 追加内容 - status 仍为 streaming
      useChatStore.getState().appendTextToken('Hello')
      state = useChatStore.getState()
      expect(state.messageMap[messageId].status).toBe('streaming')

      // 4. 完成流式 - status 变为 done
      useChatStore.getState().finishStreaming()
      const finalState = useChatStore.getState()
      expect(finalState.messageMap[messageId].status).toBe('done')
      expect(finalState.streamingMessageId).toBeUndefined()
      expect(finalState.streamingBlockId).toBeUndefined()
    })

    it('应该支持多个消息的状态独立管理', () => {
      // 创建第一个消息并完成
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId1 = state.streamingMessageId!

      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('Message 1')
      useChatStore.getState().finishStreaming()

      // 创建第二个消息
      useChatStore.getState().startAssistantMessage()
      state = useChatStore.getState()
      const messageId2 = state.streamingMessageId!

      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('Message 2')

      // 验证状态
      state = useChatStore.getState()
      expect(state.messageMap[messageId1].status).toBe('done')
      expect(state.messageMap[messageId2].status).toBe('streaming')
      expect(state.streamingMessageId).toBe(messageId2)
    })
  })

  describe('5. 边界情况测试', () => {
    it('应该处理在没有 streamingBlockId 时调用 appendTextToken', () => {
      useChatStore.getState().startAssistantMessage()
      // 故意不调用 startBlock

      // 应该安全返回，不抛出错误
      const consoleSpy = jest.spyOn(console, 'warn').mockImplementation()
      useChatStore.getState().appendTextToken('Some token')

      expect(consoleSpy).toHaveBeenCalledWith(
        '[appendTextToken] No streaming message or block'
      )
      consoleSpy.mockRestore()
    })

    it('应该处理在没有 streamingMessageId 时调用 appendTextToken', () => {
      // 直接调用 appendTextToken
      const consoleSpy = jest.spyOn(console, 'warn').mockImplementation()
      useChatStore.getState().appendTextToken('Some token')

      expect(consoleSpy).toHaveBeenCalledWith(
        '[appendTextToken] No streaming message or block'
      )
      consoleSpy.mockRestore()
    })

    it('应该处理 block 为 image 类型时调用 appendTextToken', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('image')

      // 调用 appendTextToken（image block 应该被忽略）
      useChatStore.getState().appendTextToken('Some token')

      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      const block = message.blocks[0]

      // image block 不应该有 text 相关属性
      expect(block.type).toBe('image')
      expect('typingBuffer' in block).toBe(false)
    })

    it('应该处理空 token 的追加', () => {
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      useChatStore.getState().startBlock('text')

      // 追加空 token
      useChatStore.getState().appendTextToken('')

      state = useChatStore.getState()
      const message = state.messageMap[messageId]
      const block = message.blocks[0] as TextBlock

      expect(block.length).toBe(0)
      expect(block.typingBuffer).toBe('')
    })

    it('应该处理连续多次 finishStreaming 调用', () => {
      useChatStore.getState().startAssistantMessage()
      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('Content')

      // 第一次调用
      useChatStore.getState().finishStreaming()
      expect(useChatStore.getState().streamingMessageId).toBeUndefined()

      // 第二次调用（不应该报错）
      expect(() => {
        useChatStore.getState().finishStreaming()
      }).not.toThrow()
    })
  })

  describe('6. 综合场景测试', () => {
    it('应该处理完整的多 block 流式场景', () => {
      // 创建消息
      useChatStore.getState().startAssistantMessage()
      let state = useChatStore.getState()
      const messageId = state.streamingMessageId!

      // 第一个 text block
      useChatStore.getState().startBlock('text')
      useChatStore.getState().appendTextToken('First block')

      // 第二个 code block
      useChatStore.getState().startBlock('code', 'typescript')
      useChatStore.getState().appendTextToken('const x = 1;')

      // 第三个 reasoning block
      useChatStore.getState().startBlock('reasoning_content')
      useChatStore.getState().appendTextToken('Think')

      // 完成流式
      useChatStore.getState().finishStreaming()

      state = useChatStore.getState()
      const message = state.messageMap[messageId]

      // 验证多 block 结构
      expect(message.blocks.length).toBe(3)
      expect(message.blocks[0].type).toBe('text')
      expect(message.blocks[1].type).toBe('code')
      expect(message.blocks[2].type).toBe('reasoning_content')

      // 验证所有 block 内容
      const block1 = message.blocks[0] as TextBlock
      const block2 = message.blocks[1] as TextBlock
      const block3 = message.blocks[2] as TextBlock

      // 注意：finishStreaming 只会 flush 最后一个 streaming block
      // 前面的 blocks 的内容保留在 segmentBuffer 中
      expect(block1.segmentBuffer).toBe('First block')
      expect(block2.segmentBuffer).toBe('const x = 1;')
      expect(block2.lang).toBe('typescript')

      // 最后一个 block 被 flush 到 segments
      expect(block3.segments.length).toBe(1)
      expect(block3.segments[0]).toBe('Think')
      expect(block3.segmentBuffer).toBe('')

      // 验证状态
      expect(message.status).toBe('done')
      expect(state.streamingMessageId).toBeUndefined()
      expect(state.streamingBlockId).toBeUndefined()

      // finishStreaming 只清空最后一个 block 的 typingBuffer
      expect(block1.typingBuffer).toBe('First block')
      expect(block2.typingBuffer).toBe('const x = 1;')
      expect(block3.typingBuffer).toBe('') // 最后一个 block 被清空
    })
  })
})
