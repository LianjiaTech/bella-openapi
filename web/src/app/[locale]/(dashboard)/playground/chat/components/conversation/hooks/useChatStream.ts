/**
 * useChatStream Hook - 桥接层
 *
 * 职责：
 * 1. 通过 RequestDispatcher 创建统一的 Transport 实例（StreamManager/JsonManager）
 * 2. 将 Transport 的回调绑定到 Zustand Store
 * 3. 解析 SSE chunk 并调用 Store actions
 * 4. 提供 startStream 和 abortStream 方法
 * 5. 处理流式数据的多模态分发
 *
 * 设计原则：
 * - Store 不负责网络请求（由 Transport 负责）
 * - Store 不负责协议解析（由本 Hook 负责）
 * - Store 只负责数据结构和原子更新
 * - 统一 Transport 接口（IChatTransport），stream/json 调用方式一致
 */

import { useMemo, useCallback, useRef, useEffect } from 'react'
import { useChatStore } from '@/lib/chat/store/chat/store'
import { useAuth } from '@/components/providers/auth-provider'
import { RequestDispatcher } from '@/lib/chat/transport/requestDispatcher'
import type { IChatTransport } from '@/lib/chat/transport/types'
import { normalizeImageSource, parseSSEChunk, splitInlineImageText } from '@/lib/chat/transport/utils/parseSSEChunk'
import { getBaseURL } from '@/lib/api/client'
import type { ContentPart } from '@/lib/chat/store/chat/actions/message'

/**
 * 聊天流控制 Hook
 *
 * 避免 re-render 策略：
 * - 不订阅整个 store（避免任何 store 变化都触发 re-render）
 * - 只订阅 streamingMessageId 用于判断 isStreaming 状态
 * - 使用 useChatStore.getState() 直接获取 actions（不触发订阅）
 *
 * @returns {Object} 流控制方法
 * @returns {Function} startStream - 开始流式对话
 * @returns {Function} abortStream - 中断流式对话
 * @returns {boolean} isStreaming - 是否正在流式输出
 */
export function useChatStream(config: any) {
  // 只订阅 streamingMessageId 用于判断 isStreaming
  const streamingMessageId = useChatStore((s) => s.streamingMessageId)
  const { user } = useAuth()
  const { model, temperature, maxTokens, thinkingMode, streaming, systemPrompt } = config

  // 当前 block 类型引用（用于追踪当前正在写入的 block 类型）
  const currentBlockTypeRef = useRef<'text' | 'code' | 'reasoning_content' | null>(null)
  const imageBufferRef = useRef<{ chunks: string[]; mimeType: string; blockStarted: boolean } | null>(null)
  const transportRef = useRef<IChatTransport | null>(null)

  const ensureImageBlock = useCallback(() => {
    const buffer = imageBufferRef.current
    if (!buffer || buffer.blockStarted) return

    const storeActions = useChatStore.getState()
    storeActions.flushStreamingTextBlock()
    storeActions.startBlock('image')
    buffer.blockStarted = true
    currentBlockTypeRef.current = null
  }, [])

  const flushImageBuffer = useCallback(() => {
    const buffer = imageBufferRef.current
    if (!buffer || buffer.chunks.length === 0) return

    ensureImageBlock()
    const imageSource = normalizeImageSource(buffer.chunks.join(''), buffer.mimeType)
    if (imageSource) {
      useChatStore.getState().addImageBlock(imageSource)
    }
    imageBufferRef.current = null
  }, [ensureImageBlock])

  const startInlineImageBuffer = useCallback(() => {
    if (!imageBufferRef.current) {
      imageBufferRef.current = {
        chunks: [],
        mimeType: 'image/png',
        blockStarted: false,
      }
    }
    ensureImageBlock()
  }, [ensureImageBlock])

  const appendTextToken = useCallback((token: string) => {
    const storeActions = useChatStore.getState()
    if (currentBlockTypeRef.current !== 'text') {
      storeActions.startBlock('text')
      currentBlockTypeRef.current = 'text'
    }
    storeActions.appendTextToken(token)
  }, [])

  const handleTextDelta = useCallback((delta: string) => {
    const { parts } = splitInlineImageText(delta, imageBufferRef.current !== null)

    for (const part of parts) {
      if (part.type === 'text') {
        appendTextToken(part.content)
      } else if (part.type === 'image_start') {
        startInlineImageBuffer()
        if (part.mimeType && imageBufferRef.current) {
          imageBufferRef.current.mimeType = part.mimeType
        }
      } else if (part.type === 'image_data') {
        if (!imageBufferRef.current) {
          startInlineImageBuffer()
        }
        imageBufferRef.current?.chunks.push(part.content)
      } else {
        flushImageBuffer()
      }
    }
  }, [appendTextToken, flushImageBuffer, startInlineImageBuffer])
  const handleStreamingMessage = useCallback((chunk: string) => {
    const state = useChatStore.getState()
    const { streamingMessageId, getMessage, setMessageStatus } = state
  
    // 获取当前流式消息
    const currentMessage = streamingMessageId ? getMessage(streamingMessageId) : undefined
  
    // 开发环境日志
    if (process.env.NODE_ENV === 'development') {
      console.log('onMessage 收到的原始数据:', chunk, '当前消息:', currentMessage)
    }
  
    // 首次接收数据时,将状态从 'connecting' 转换为 'streaming'
    if (streamingMessageId && currentMessage?.status === 'connecting') {
      setMessageStatus(streamingMessageId, 'streaming')
    }
  
    return currentMessage
  }, [])

  const dispatcherTransport = useMemo(() => {
    return new RequestDispatcher()
  }, [])

  /**
   * 开始流式对话
   *
   * 流程：
   * 1. 添加用户消息到 Store
   * 2. 创建助手消息占位
   * 3. 创建初始 text block
   * 4. 启动 StreamManager
   * 5. 处理流式数据并更新 Store
   *
   * @param userMessage - 用户输入的消息内容（支持纯文本或多模态）
   */
  const startStream = useCallback(
    async (userMessage: string | ContentPart[]) => {
      // 获取 store actions（不触发订阅）
      const storeActions = useChatStore.getState()

      // 1. 添加用户消息
      storeActions.addUserMessage(userMessage)

      // 2. 立即创建 assistant 消息占位（不等待连接建立）
      storeActions.startAssistantMessage()

      // 3. 立即创建初始 text block（默认从 text 开始）
      storeActions.startBlock('text')
      currentBlockTypeRef.current = 'text'

      console.log('[useChatStream] 已创建 assistant message 占位，开始连接...', typeof streaming)
      // 4. 创建并启动 transport
      // 智能拼接 URL：移除 baseURL 末尾的斜杠，避免 `//v1/...` 导致的协议相对 URL 问题
      const baseURL = getBaseURL().replace(/\/$/, '')
      transportRef.current = dispatcherTransport.send({
        url: `${baseURL}/v1/chat/completions`,
        body: {
          model: model, // 模型
          temperature: temperature, // 温度
          max_tokens: maxTokens, // 最大长度
          enable_thinking: thinkingMode, // 是否启用思考模式
          stream: streaming, // 是否流式输出
          messages: [
            {
              role: 'system',
              content: systemPrompt,
            },
            {
              role: 'user',
              // OpenAI 格式支持 string 或 ContentPart[]
              content: userMessage,
            },
          ],
          user: user?.userId,
        },
        mode: streaming ? 'stream' : 'json',
        /**
         * onOpen: 连接建立回调
         *
         * 修改说明：
         * - assistant message 和初始 block 已在 startStream 开始时创建
         * - 此回调仅用于确认连接成功，记录日志
         */
        onOpen: () => {
          console.log('[useChatStream] SSE 连接已建立，开始接收流式数据')
        },

        /**
         * onMessage: 接收到流式数据回调
         *
         * 核心逻辑：
         * 1. 解析 SSE chunk
         * 2. 根据 type 分发到不同的 Store action
         * 3. 处理 block 切换（text → code → reasoning）
         */
        onMessage: (chunk: string) => {
          handleStreamingMessage(chunk)

          const parsed = parseSSEChunk(chunk)
          if (!parsed) return

          console.log('[useChatStream] 收到数据:', parsed)

          // 获取 store actions
          const storeActions = useChatStore.getState()
          switch (parsed.type) {
            case 'text':
              if (parsed.delta) {
                handleTextDelta(parsed.delta)
              }
              break
            case 'code':
            case 'reasoning_content':
              if (imageBufferRef.current) {
                flushImageBuffer()
              }

              // 如果 block 类型切换，创建新 block
              if (currentBlockTypeRef.current !== parsed.type) {
                console.log('[useChatStream] Block 类型切换:', {
                  from: currentBlockTypeRef.current,
                  to: parsed.type,
                })
                storeActions.startBlock(parsed.type, parsed.lang)
                currentBlockTypeRef.current = parsed.type
              }

              // 追加文本 token 到当前 block
              if (parsed.delta) {
                storeActions.appendTextToken(parsed.delta)
              }
              break
            case 'image':
              if (!parsed.image?.source) break

              if (parsed.image.append) {
                if (!imageBufferRef.current) {
                  imageBufferRef.current = {
                    chunks: [],
                    mimeType: parsed.image.mimeType || 'image/png',
                    blockStarted: false,
                  }
                }

                imageBufferRef.current.chunks.push(parsed.image.source)
                ensureImageBlock()
              } else {
                const imageSource = normalizeImageSource(parsed.image.source, parsed.image.mimeType || 'image/png')
                if (imageSource) {
                  storeActions.flushStreamingTextBlock()
                  storeActions.startBlock('image')
                  storeActions.addImageBlock(imageSource)
                  currentBlockTypeRef.current = null
                }
              }
              break

            case 'done':
              flushImageBuffer()
              // 流结束标记（通常由 onDone 处理，这里作为备份）
              console.log('[useChatStream] 收到 [DONE] 标记')
              break

            case 'error':
              // 流错误（通常由 onError 处理，这里作为备份）
              console.error('[useChatStream] 收到错误:', parsed.error)
              // 使用 store 的 streamingMessageId 替代局部变量
              const { streamingMessageId: errorMessageId } = useChatStore.getState()
              if (errorMessageId) {
                storeActions.setMessageError(
                  errorMessageId,
                  parsed.error || 'Unknown error'
                )
              }
              break

            default:
              console.warn('[useChatStream] 未知 chunk 类型:', parsed)
          }
        },

        /**
         * onDone: 流正常结束回调
         */
        onDone: () => {
          console.log('[useChatStream] 流式对话完成')
          flushImageBuffer()
          useChatStore.getState().finishStreaming()
          currentBlockTypeRef.current = null
        },

        /**
         * onError: 流错误回调
         *
         * 修改说明：
         * - 使用 store 的 streamingMessageId 替代局部变量
         * - 确保错误处理能正确找到当前流式消息
         */
        onError: (error: unknown) => {
          // 从 store 获取当前流式消息 ID
          const { streamingMessageId } = useChatStore.getState()
          if (streamingMessageId) {
            useChatStore.getState().setMessageError(
              streamingMessageId,
              error instanceof Error ? error.message : 'Stream error'
            )
          }
          useChatStore.getState().finishStreaming()
          currentBlockTypeRef.current = null
          imageBufferRef.current = null
        },
      })

      // 5. 启动 transport（统一调用方式）
      await transportRef.current.start()
    },
    [dispatcherTransport, user, model, temperature, maxTokens, thinkingMode, streaming, systemPrompt, ensureImageBlock, flushImageBuffer, handleTextDelta]
  )

  /**
   * 中断流式对话
   */
  const abortStream = useCallback(() => {
    transportRef.current?.abort()
    useChatStore.getState().finishStreaming()
    currentBlockTypeRef.current = null
    imageBufferRef.current = null
  }, [])

  /**
   * 注册 abortStream 到 store
   *
   * 职责：
   * - 组件挂载时将 abortStream 方法注入到 store
   * - 组件卸载时清理引用，避免内存泄漏
   *
   * 设计说明：
   * - 使用 useEffect 确保在组件生命周期内正确注册/注销
   * - abortStream 使用 useCallback 确保引用稳定
   * - 清理函数移除引用，防止调用已卸载组件的方法
   */
  useEffect(() => {
    // 注入 abortStream 到 store
    useChatStore.setState({ abortStream })

    return () => {
      // 清理时移除引用
      useChatStore.setState({ abortStream: undefined })
    }
  }, [abortStream])

  /**
   * 获取当前流状态
   * 使用顶部订阅的 streamingMessageId
   */
  const isStreaming = streamingMessageId !== undefined

  return {
    /** 开始流式对话 */
    startStream,
    /** 中断流式对话 */
    abortStream,
    /** 是否正在流式输出 */
    isStreaming,
  }
}
