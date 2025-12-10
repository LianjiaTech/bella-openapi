import {EventEmitter} from 'events';
import {createParser} from 'eventsource-parser';

/**
 * ChatCompletions请求配置
 */
export interface ChatCompletionsConfig {
  /** API请求地址 */
  url: string;
  /** 请求超时时间(ms) */
  timeoutMs?: number;
  /** 请求头 */
  headers?: Record<string, string>;
}

/**
 * 消息角色类型
 */
export type MessageRole = 'user' | 'assistant' | 'system' | 'function';

/**
 * 消息对象
 */
export interface ChatMessage {
  /** 消息角色 */
  role: MessageRole;
  /** 消息内容 - 支持文本或多模态内容 */
  content: string | any[];
  /** 深度思考内容 */
  reasoning_content?: string;
  /** 是否是思考内容 */
  isReasoningContent?: boolean;
  /** 对于function角色，可选的函数名称 */
  name?: string;
  /** 对于function角色，可选的函数参数 */
  error?: string;
}

/**
 * ChatCompletions请求参数
 */
export interface ChatCompletionsRequest {
  /** 模型名称 */
  model: string;
  /** 消息列表 */
  messages: ChatMessage[];
  /** 请求ID */
  request_id?: string;
  /** 是否流式响应 */
  stream?: boolean;
  /** 生成随机性 */
  temperature?: number;
  /** Token限制 */
  max_tokens?: number;
  /** 附加参数 */
  [key: string]: any;
}

/**
 * ChatCompletions事件类型
 */
export enum ChatCompletionsEventType {
  /** 开始响应 */
  START = 'start',
  /** 中间响应 */
  DELTA = 'delta',
  /** 完成 */
  FINISH = 'finish',
  /** 错误 */
  ERROR = 'error',
  /** 请求状态变化 */
  STATE_CHANGE = 'state_change'
}

/**
 * ChatCompletions状态
 */
export enum ChatCompletionsState {
  /** 空闲 */
  IDLE = 'idle',
  /** 连接中 */
  CONNECTING = 'connecting',
  /** 连接已建立 */
  CONNECTED = 'connected',
  /** 响应中 */
  RESPONDING = 'responding',
  /** 已完成 */
  FINISHED = 'finished',
  /** 已取消 */
  CANCELLED = 'cancelled',
  /** 错误 */
  ERROR = 'error'
}

/**
 * ChatCompletions响应
 */
export interface ChatCompletionsResponse {
  /** 创建时间 */
  created: number;
  /** 响应ID */
  id: string;
  /** 模型名称 */
  model: string;
  /** 选择列表 */
  choices: {
    /** 序号 */
    index: number;
    /** 结束原因 */
    finish_reason?: string;
    /** 消息或增量更新 */
    message?: ChatMessage;
    /** 增量更新 */
    delta?: Partial<ChatMessage>;
  }[];
  /** 使用统计 */
  usage?: {
    /** 输入token数 */
    prompt_tokens: number;
    /** 输出token数 */
    completion_tokens: number;
    /** 总token数 */
    total_tokens: number;
    /** 缓存创建token数 */
    cache_creation_tokens?: number;
    /** 缓存读取token数 */
    cache_read_tokens?: number;
  };
}

/**
 * ChatCompletions响应解析器
 */
class ChatCompletionsResponseParser {
  /**
   * 判断是否是结束信号
   * @param data 数据
   */
  static isDone(data: string): boolean {
    return data === '[DONE]';
  }

  /**
   * 解析数据
   * @param data 数据
   */
  static parseData(data: string): ChatCompletionsResponse | null {
    if (!data || data === '[DONE]') {
      return null;
    }

    try {
      return JSON.parse(data) as ChatCompletionsResponse;
    } catch (error) {
      console.error('解析SSE数据失败:', error, data);
      return null;
    }
  }
}

/**
 * ChatCompletions Writer
 * 用于处理流式LLM响应
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
      timeoutMs: 300000,
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

    if (this.state !== ChatCompletionsState.IDLE &&
        this.state !== ChatCompletionsState.FINISHED &&
        this.state !== ChatCompletionsState.ERROR &&
        this.state !== ChatCompletionsState.CANCELLED) {
      throw new Error(`不能在${this.state}状态下发送请求`);
    }

    // 重置状态
    this.responseText = '';
    this.lastUsage = null;
    this.setState(ChatCompletionsState.CONNECTING);

    // 创建取消控制器
    this.controller = new AbortController();
    const signal = this.controller.signal;

    // 准备请求参数
    const requestParams: RequestInit = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...this.config.headers
      },
      body: JSON.stringify(request),
      credentials: 'include', // 添加此行确保携带cookie
      signal
    };

    // 设置超时
    const timeoutId = setTimeout(() => {
      this.cancel('请求超时');
    }, this.config.timeoutMs);

    try {

      // 发送请求并获取流式响应
      const response = await fetch(this.config.url, requestParams);

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`请求失败: ${response.status} ${response.statusText} - ${errorText}`);
      }

      if (response.body === null) {
        throw new Error('响应体为空');
      }

      // 触发开始事件
      this.setState(ChatCompletionsState.CONNECTED);

      // 开始处理流式响应
      await this.processStream(response.body);

    } catch (error) {
      clearTimeout(timeoutId);
      if (signal.aborted) {
        // 请求已被取消，不需要处理错误
        return;
      }

      this.setState(ChatCompletionsState.ERROR);
      this.emit(ChatCompletionsEventType.ERROR, error instanceof Error ? error.message : String(error));
      // 在错误处理后自动重置状态到IDLE，这样可以继续发送新的请求
      setTimeout(() => {
        if (this.state === ChatCompletionsState.ERROR) {
          this.setState(ChatCompletionsState.IDLE);
        }
      }, 500);
    }
  }

  /**
   * 处理流式响应
   * @param body 响应体
   */
  private async processStream(body: ReadableStream<Uint8Array>): Promise<void> {

    // 设置状态
    this.setState(ChatCompletionsState.RESPONDING);
    this.emit(ChatCompletionsEventType.START, {});

    const reader = body.getReader();
    const decoder = new TextDecoder();

    try {
      // 创建SSE解析器
      const parser = createParser({
        onEvent: (event) => {
          // 所有的onEvent调用都是事件
          {
          const data = event.data;

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
                    thoughtSignature: (choice.delta as any).thoughtSignature,
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
                    thoughtSignature: (choice.delta as any).thoughtSignature,
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
            return;
          }
          }
        },
        onError: (err) => {
          this.setState(ChatCompletionsState.ERROR);
          this.emit(ChatCompletionsEventType.ERROR, err instanceof Error ? err.message : String(err));
        }
      });

      // 流式处理返回数据
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        // 将二进制数据转换为文本并传递给解析器
        const text = decoder.decode(value, { stream: true });

        parser.feed(text);
      }

      // 确保最终状态为完成
      if (this.state !== ChatCompletionsState.FINISHED) {
        this.setState(ChatCompletionsState.FINISHED);
        this.emit(ChatCompletionsEventType.FINISH, { usage: this.lastUsage });
      }

    } catch (error) {
      if (this.controller && !this.controller.signal.aborted) {
        this.setState(ChatCompletionsState.ERROR);
        this.emit(ChatCompletionsEventType.ERROR, error instanceof Error ? error.message : String(error));

        // 在流处理错误后也自动重置状态
        setTimeout(() => {
          if (this.state === ChatCompletionsState.ERROR) {
            this.setState(ChatCompletionsState.IDLE);
          }
        }, 500);
      }
    } finally {
      reader.releaseLock();
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
      this.emit(ChatCompletionsEventType.FINISH, { cancelled: true, reason: reason || '用户取消' });
    }
  }
}
