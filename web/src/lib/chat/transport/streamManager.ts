import { fetchEventSource } from '@microsoft/fetch-event-source';
import { nanoid } from 'nanoid';
import type { RequestDispatcherOptions, IChatTransport } from './types';
import { getFullUrl, getDomain } from '@/lib/utils/url';

/**
 * StreamManager V2 - 生产级流生命周期调度器
 *
 * 核心功能：
 * - requestId 批次隔离机制（防止竞态污染/幽灵流）
 * - AbortController 管理（支持中断）
 * - 状态机管理（idle → connecting → streaming → done/abort/error）
 * - 回调驱动（不依赖 React，通过回调通知状态变化）
 *
 * 使用 @microsoft/fetch-event-source 实现 SSE 流式处理
 *
 * 职责：
 * - 实现 IChatTransport 接口，提供统一的 start() 和 abort() 方法
 * - 构造时接收完整的 RequestDispatcherOptions 配置
 * - 通过 onStatusChange 回调通知外部状态变化
 */
export class StreamManager implements IChatTransport {
  private controller: AbortController | null = null;
  private currentRequestId: string | null = null;
  private options: RequestDispatcherOptions;

  constructor(options: RequestDispatcherOptions) {
    this.options = options;
  }

  /**
   * 启动新的流式请求
   * 自动中断旧流，生成新的 requestId
   * 实现 IChatTransport 接口，无需传参
   */
  async start(): Promise<void> {
    // 中断旧流
    this.abort();

    // 创建新的 AbortController
    const controller = new AbortController();
    this.controller = controller;

    // 生成新的 requestId（批次隔离）
    const requestId = nanoid();
    this.currentRequestId = requestId;

    // 进入 connecting 状态
    this.options.onStatusChange?.('connecting');

    // 启动流处理
    await this.run(controller.signal, requestId);
  }

  /**
   * 核心流处理逻辑
   * 使用 requestId 进行批次隔离，防止竞态污染
   */
  private async run(
    signal: AbortSignal,
    requestId: string
  ): Promise<void> {
    // 🔍 调试日志：输出真实请求 URL
    const fullUrl = getFullUrl(this.options.url);
    const domain = getDomain(fullUrl);

    console.log('[StreamManager] 🌐 发起流式请求', {
      '真实 URL': fullUrl,
      '后端域名': domain,
      'RequestID': requestId,
      '请求方法': 'POST',
      'Headers': this.options.headers,
      'Body': this.options.body,
    });
    
    try {
      await fetchEventSource(this.options.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/event-stream',
          'X-BELLA-CONSOLE': 'true', // 标识控制台请求（Java 后端需要）
          'Accept-Encoding': 'identity',
          ...this.options.headers,     
        },
        body: JSON.stringify(this.options.body),
        signal,
        credentials: 'include',

        /**
         * onopen: 连接建立，首包到达
         * 检查响应状态，切换到 streaming 状态
         */
        onopen: async (response) => {
          console.log('[StreamManager] ✅ 连接已建立', {
            'URL': fullUrl,
            '状态码': response.status,
            'StatusText': response.statusText,
            'RequestID': requestId,
          });

          if (!response.ok) {
            console.error('[StreamManager] ❌ HTTP 错误', {
              'URL': fullUrl,
              '状态码': response.status,
              'StatusText': response.statusText,
            });
            this.options.onError?.(new Error(`HTTP error: ${response.status}`));
            return;
          }

          // 竞态检查：确保是当前有效的请求
          if (this.currentRequestId !== requestId) return;

          this.options.onStatusChange?.('streaming');
          this.options.onOpen?.();
        },

        /**
         * onmessage: 接收到流式数据块
         * 检查中断状态和 requestId，防止幽灵流
         */
        onmessage: (event) => {
          // 检查是否已被中断
          if (signal.aborted) return;

          // 竞态检查：确保是当前有效的请求
          if (this.currentRequestId !== requestId) return;

          this.options.onMessage?.(event.data);
        },

        /**
         * onerror: 发生错误
         * 设置错误状态，抛出异常停止重连
         */
        onerror: (err) => {
          // 如果已被中断，不处理错误（避免误报）
          if (signal.aborted) return;

          // 竞态检查
          if (this.currentRequestId !== requestId) return;

          console.error('[StreamManager] ❌ 流式请求错误', {
            'URL': fullUrl,
            '后端域名': domain,
            'RequestID': requestId,
            '错误详情': err,
          });

          this.options.onStatusChange?.('error');
          this.options.onError?.(err);

          // 抛出异常，停止 fetchEventSource 的自动重连
          throw err;
        },

        /**
         * onclose: 流正常结束
         * 设置 done 状态
         */
        onclose: () => {
          // 如果已被中断，不处理关闭
          if (signal.aborted) return;

          // 竞态检查
          if (this.currentRequestId !== requestId) return;

          console.log('[StreamManager] ✅ 流式请求完成', {
            'URL': fullUrl,
            'RequestID': requestId,
          });

          this.options.onStatusChange?.('done');
          this.options.onDone?.();
        },
      });
    } catch (err) {
      // fetchEventSource 抛出的异常已经在 onerror 中处理
      // 这里只处理未被中断的其他异常
      if (!signal.aborted) {
        console.error('[StreamManager] ❌ 捕获未处理异常', {
          'URL': fullUrl,
          'RequestID': requestId,
          '错误详情': err,
        });
        this.options.onStatusChange?.('error');
        this.options.onError?.(err);
      }
    } finally {
      // 清理：如果当前 controller 还是这个 signal 的，清空引用
      if (this.controller?.signal === signal) {
        this.controller = null;
      }
    }
  }

  /**
   * 中断当前流
   * 设置 abort 状态，清理 controller
   */
  abort(): void {
    if (this.controller) {
      this.controller.abort();
      this.controller = null;
      this.options.onStatusChange?.('abort');
    }
  }

  /**
   * 获取当前 requestId（用于调试）
   */
  getCurrentRequestId(): string | null {
    return this.currentRequestId;
  }
}

export default StreamManager;
