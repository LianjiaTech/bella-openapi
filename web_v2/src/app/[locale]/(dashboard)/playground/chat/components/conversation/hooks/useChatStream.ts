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
import { getBaseURL } from '@/lib/api/client'
import type { ContentPart } from '@/lib/chat/store/chat/actions/message'

/**
 * SSE Chunk 数据类型定义
 */
interface SSEChunkData {
  /** chunk 类型 */
  type: 'text' | 'code' | 'reasoning_content' | 'image' | 'done' | 'error'
  /** 文本增量（type=text/code/reasoning 时有效） */
  delta?: string
  /** 代码语言（type=code 时有效） */
  lang?: string
  /** 图片 URL（type=image 时有效） */
  url?: string
  /** 错误信息（type=error 时有效） */
  error?: string
}

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
  console.log('config', config);
  // 只订阅 streamingMessageId 用于判断 isStreaming
  const streamingMessageId = useChatStore((s) => s.streamingMessageId)
  const { user } = useAuth()
  const { model, temperature, maxTokens, thinkingMode, streaming, systemPrompt } = config

  // 当前 block 类型引用（用于追踪当前正在写入的 block 类型）
  const currentBlockTypeRef = useRef<'text' | 'code' | 'reasoning_content' | null>(null)
  const transportRef = useRef<IChatTransport | null>(null)

  /**
   * 解析 SSE chunk 数据
   *
   * 当前实现：简单的 JSON 解析
   * 未来可扩展为完整的 OpenAI SSE 协议解析
   *
   * @param chunk - SSE 原始数据
   * @returns 解析后的数据对象
   */
  const parseSSEChunk = useCallback((chunk: string): SSEChunkData | null => {
    try {
      // 移除 SSE 协议前缀（如 "data: "）
      const cleanChunk = chunk.replace(/^data:\s*/i, '').trim()

      // 空行或 [DONE] 标记
      if (!cleanChunk || cleanChunk === '[DONE]') {
        return { type: 'done' }
      }

      // 解析 JSON
      const parsed = JSON.parse(cleanChunk)

      // OpenAI 格式：choices[0].delta.content 或 reasoning_content
      if (parsed.choices && parsed.choices[0]?.delta) {
        const delta = parsed.choices[0].delta

        // 优先判断 reasoning_content
        if (delta.reasoning_content !== undefined) {
          return {
            type: 'reasoning_content',
            delta: delta.reasoning_content,
          }
        }

        // 否则使用 content
        if (delta.content !== undefined) {
          return {
            type: 'text',
            delta: delta.content,
          }
        }
      }

      // 自定义格式（根据你的后端协议调整）
      if (parsed.type && parsed.delta) {
        return {
          type: parsed.type,
          delta: parsed.delta,
          lang: parsed.lang,
          url: parsed.url,
        }
      }

      return null
    } catch (err) {
      console.warn('[parseSSEChunk] 解析失败:', chunk, err)
      return null
    }
  }, [])
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
            case 'code':
            case 'reasoning_content':
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
              // 创建图片 block
              if (parsed.url) {
                storeActions.startBlock('image')
                storeActions.addImageBlock(parsed.url)
              }
              break

            case 'done':
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
        },
      })

      // 5. 启动 transport（统一调用方式）
      await transportRef.current.start()
    },
    [dispatcherTransport, user, parseSSEChunk, model, temperature, maxTokens, thinkingMode, streaming, systemPrompt]
  )

  /**
   * 中断流式对话
   */
  const abortStream = useCallback(() => {
    transportRef.current?.abort()
    useChatStore.getState().finishStreaming()
    currentBlockTypeRef.current = null
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
