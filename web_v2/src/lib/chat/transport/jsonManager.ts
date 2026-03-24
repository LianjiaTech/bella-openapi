// transport/json-manager.ts

import type { IChatTransport, RequestDispatcherOptions } from './types'
import { useChatStore } from '../store/chat/store'
import { normalizeJsonResponse } from './utils/normalizeJsonResponse'
import { nanoid } from 'nanoid'
import { getFullUrl, getDomain } from '@/lib/utils/url'

/**
 * JsonManager - JSON 响应模式处理器
 *
 * 核心功能：
 * - 处理非流式 JSON 响应（一次性返回完整数据）
 * - requestId 批次隔离机制（防止竞态污染）
 * - AbortController 管理（支持中断）
 *
 * 职责：
 * - 实现 IChatTransport 接口，提供统一的 start() 和 abort() 方法
 * - 构造时接收完整的 RequestDispatcherOptions 配置
 * - 不负责创建消息（由外部 useChatStream 统一管理）
 * - 只负责获取数据、解析数据、写入 blocks
 *
 * 设计原则：
 * - 与 StreamManager 保持一致的职责边界
 * - 外部创建消息，内部只写入内容
 * - 通过回调通知外部状态变化
 */
export class JsonManager implements IChatTransport {
  private controller?: AbortController
  private aborted = false
  private currentRequestId: string | null = null;
  constructor(private options: RequestDispatcherOptions) { }

  async start(): Promise<void> {
    const {
      startBlock,
      finishStreaming,
      setMessageError,
    } = useChatStore.getState()
    // 生成新的 requestId（批次隔离）
    const requestId = nanoid();
    this.currentRequestId = requestId;

    // 🔍 调试日志：输出真实请求 URL
    const fullUrl = getFullUrl(this.options.url);
    const domain = getDomain(fullUrl);

    console.log('[JsonManager] 🌐 发起 JSON 请求', {
      '真实 URL': fullUrl,
      '后端域名': domain,
      'RequestID': requestId,
      '请求方法': this.options.method ?? 'POST',
      'Headers': this.options.headers,
      'Body': this.options.body,
    });

    try {
      // 1️⃣ 创建 AbortController
      this.controller = new AbortController()
      this.aborted = false
      // 竞态检查：确保是当前有效的请求
      if (this.currentRequestId !== requestId) return;
      // 2️⃣ 发起请求
      const response = await fetch(this.options.url, {
        method: this.options.method ?? 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-BELLA-CONSOLE': 'true', // 标识控制台请求（Java 后端需要）
        },
        body:
          typeof this.options.body === 'string'
            ? this.options.body
            : JSON.stringify(this.options.body),
        signal: this.controller.signal,
        credentials: 'include',
      })

      console.log('[JsonManager] ✅ 收到响应', {
        'URL': fullUrl,
        '状态码': response.status,
        'StatusText': response.statusText,
        'RequestID': requestId,
      });

      // 3️⃣ HTTP 错误处理
      if (!response.ok) {
        const errorText = await response.text().catch(() => '')
        console.error('[JsonManager] ❌ HTTP 错误', {
          'URL': fullUrl,
          '状态码': response.status,
          'StatusText': response.statusText,
          '错误详情': errorText,
        });
        throw new Error(
          `HTTP ${response.status}: ${errorText || response.statusText}`
        )
      }

      // 4️⃣ 解析 JSON
      const body = await response.json()
      if (this.aborted) return

      // 5️⃣ 归一化为 blocks
      const { blocks } = normalizeJsonResponse(body)

      // 6️⃣ 写入 store（一次性完整写入）
      for (const block of blocks) {
        if (block.type === 'image') {
          startBlock('image')
          useChatStore.getState().addImageBlock(block.url)
        } else {
          startBlock(block.type, block.type === 'code' ? block.lang : undefined)

          // 直接使用 segmentBuffer 机制：一次性写入
          useChatStore
            .getState()
            .appendTextToken(block.content)

          // ⚠️ 这里我们直接调用 finishStreaming 统一 flush
          // 但不要在循环内调用
        }
      }

      // 7️⃣ 完成流式
      console.log('[JsonManager] ✅ JSON 请求完成', {
        'URL': fullUrl,
        'RequestID': requestId,
        'Blocks 数量': blocks.length,
      });
      finishStreaming()
    } catch (error: any) {
      if (this.aborted) return

      console.error('[JsonManager] ❌ JSON 请求错误', {
        'URL': fullUrl,
        '后端域名': domain,
        'RequestID': requestId,
        '错误详情': error,
      });

      // 错误处理：使用当前 streamingMessageId
      const { streamingMessageId, finishStreaming, setMessageError } = useChatStore.getState()

      // 追加错误信息到当前消息
      if (streamingMessageId) {
        useChatStore.getState().appendTextToken(error?.message || '请求失败')
        setMessageError(streamingMessageId, error?.message || '请求失败')
      }

      finishStreaming()
    }
  }

  abort(): void {
    this.aborted = true
    this.controller?.abort()

    const { finishStreaming } = useChatStore.getState()
    finishStreaming()
  }
}