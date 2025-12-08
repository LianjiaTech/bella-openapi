import { EventEmitter } from 'events';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import {
  ChatCompletionsConfig,
  ChatCompletionsRequest,
  ChatCompletionsState,
  ChatCompletionsEventType,
  ChatCompletionsResponse,
  ChatCompletionsEventMap
} from './types';
import { ChatCompletionsResponseParser } from './ChatCompletionsResponseParser';

/**
 * Chat Completions 流式处理器
 * 使用 fetch-event-source 库处理 SSE 流式响应
 */
export class ChatCompletionsProcessor extends EventEmitter {
  private config: ChatCompletionsConfig;
  private controller: AbortController | null = null;
  private state: ChatCompletionsState = ChatCompletionsState.IDLE;
  private responseText: string = '';
  private lastUsage: ChatCompletionsResponse['usage'] | null = null;

  /**
   * 构造函数
   * @param config 配置
   */
  constructor(config: ChatCompletionsConfig) {
    super();
    this.config = {
      timeoutMs: 30000,
      ...config
    };
  }

  /**
   * 获取当前状态
   */
  public getState(): ChatCompletionsState {
    return this.state;
  }

  /**
   * 获取当前已收到的完整响应文本
   */
  public getResponseText(): string {
    return this.responseText;
  }

  /**
   * 类型安全的事件监听
   */
  public on<K extends keyof ChatCompletionsEventMap>(
    event: K,
    listener: ChatCompletionsEventMap[K]
  ): this {
    return super.on(event, listener);
  }

  /**
   * 类型安全的事件触发
   */
  public emit<K extends keyof ChatCompletionsEventMap>(
    event: K,
    ...args: Parameters<ChatCompletionsEventMap[K]>
  ): boolean {
    return super.emit(event, ...args);
  }

  /**
   * 设置状态并触发状态变更事件
   * @param newState 新状态
   */
  private setState(newState: ChatCompletionsState): void {
    const oldState = this.state;
    this.state = newState;
    this.emit(ChatCompletionsEventType.STATE_CHANGE, { oldState, newState });
  }

  /**
   * 发送请求
   * @param request 请求参数
   */
  public async send(request: ChatCompletionsRequest): Promise<void> {
    if (
      this.state !== ChatCompletionsState.IDLE &&
      this.state !== ChatCompletionsState.FINISHED &&
      this.state !== ChatCompletionsState.ERROR &&
      this.state !== ChatCompletionsState.CANCELLED
    ) {
      throw new Error(`不能在${this.state}状态下发送请求`);
    }

    // 重置状态
    this.responseText = '';
    this.lastUsage = null;
    this.setState(ChatCompletionsState.CONNECTING);

    // 创建取消控制器
    this.controller = new AbortController();
    const signal = this.controller.signal;

    // 设置超时
    const timeoutId = setTimeout(() => {
      this.cancel('请求超时');
    }, this.config.timeoutMs);

    try {
      await fetchEventSource(this.config.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...this.config.headers
        },
        body: JSON.stringify(request),
        credentials: 'include',
        signal,

        // 连接打开时的回调
        onopen: async (response) => {
          clearTimeout(timeoutId);

          if (!response.ok) {
            const errorText = await response.text();
            throw new Error(
              `请求失败: ${response.status} ${response.statusText} - ${errorText}`
            );
          }

          // 不在这里设置状态，而是在第一次收到消息时设置
        },

        // 收到消息时的回调
        onmessage: (event) => {
          // 首次收到消息时设置状态
          if (this.state === ChatCompletionsState.CONNECTING) {
            this.setState(ChatCompletionsState.CONNECTED);
            this.setState(ChatCompletionsState.RESPONDING);
            this.emit(ChatCompletionsEventType.START, {});
          }

          const data = event.data;
          console.log('onmessage data:', data)
          // 检查是否为结束信号
          if (ChatCompletionsResponseParser.isDone(data)) {
            this.setState(ChatCompletionsState.FINISHED);
            this.emit(ChatCompletionsEventType.FINISH, { usage: this.lastUsage });
            return;
          }

          try {
            // 解析JSON数据
            const response = ChatCompletionsResponseParser.parseData(data);

            if (!response) {
              return;
            }

            // 先保存 usage 信息（必须在触发 FINISH 之前）
            if (response.usage) {
              this.lastUsage = response.usage;
            }

            // 处理数据
            if (response.choices && response.choices.length > 0) {
              const choice = response.choices[0];

              // 处理正常内容
              if (choice.delta?.content !== undefined) {
                // 累积响应文本
                this.responseText += choice.delta.content;

                // 触发内容增量事件
                this.emit(ChatCompletionsEventType.DELTA, {
                  content: choice.delta.content,
                  role: choice.delta.role || 'assistant',
                  response: response
                });
              }

              // 处理深度思考内容
              if (choice.delta?.reasoning_content !== undefined) {
                // 触发深度思考内容增量事件
                this.emit(ChatCompletionsEventType.DELTA, {
                  reasoning_content: choice.delta.reasoning_content,
                  role: choice.delta.role || 'assistant',
                  isReasoningContent: true,
                  response: response
                });
              }

              // 处理完成原因（此时 usage 已经保存）
              if (choice.finish_reason) {
                this.setState(ChatCompletionsState.FINISHED);
                this.emit(ChatCompletionsEventType.FINISH, {
                  reason: choice.finish_reason,
                  usage: this.lastUsage
                });
              }
            }
          } catch (error) {
            // 忽略解析错误
            return;
          }
        },

        // 错误处理回调
        onerror: (err) => {
          clearTimeout(timeoutId);

          // 如果已被取消，不处理错误
          if (signal.aborted) {
            return;
          }

          this.setState(ChatCompletionsState.ERROR);
          this.emit(
            ChatCompletionsEventType.ERROR,
            err instanceof Error ? err.message : String(err)
          );

          // 在错误处理后自动重置状态到IDLE
          setTimeout(() => {
            if (this.state === ChatCompletionsState.ERROR) {
              this.setState(ChatCompletionsState.IDLE);
            }
          }, 500);

          // 抛出错误以停止重连
          throw err;
        },

        // 连接关闭时的回调
        onclose: () => {
          clearTimeout(timeoutId);

          // 确保最终状态为完成
          if (this.state !== ChatCompletionsState.FINISHED) {
            this.setState(ChatCompletionsState.FINISHED);
            this.emit(ChatCompletionsEventType.FINISH, { usage: this.lastUsage });
          }
        }
      });
    } catch (error) {
      clearTimeout(timeoutId);

      // 如果已被取消，不处理错误
      if (signal.aborted) {
        return;
      }

      this.setState(ChatCompletionsState.ERROR);
      this.emit(
        ChatCompletionsEventType.ERROR,
        error instanceof Error ? error.message : String(error)
      );

      // 在错误处理后自动重置状态到IDLE
      setTimeout(() => {
        if (this.state === ChatCompletionsState.ERROR) {
          this.setState(ChatCompletionsState.IDLE);
        }
      }, 500);
    }
  }

  /**
   * 取消请求
   * @param reason 取消原因
   */
  public cancel(reason?: string): void {
    if (this.controller) {
      this.controller.abort();
      this.controller = null;
      this.setState(ChatCompletionsState.CANCELLED);
      // 用户取消不显示为错误，而是完成状态
      this.emit(ChatCompletionsEventType.FINISH, {
        cancelled: true,
        reason: reason || '用户取消'
      });
    }
  }
}
