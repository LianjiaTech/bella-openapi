/**
 * WebSocket 连接配置
 */
export interface WebSocketConfig {
  url: string;
  appkey: string;
  reconnectAttempts?: number;
  reconnectInterval?: number;
  timeoutMs?: number;
}

/**
 * WebSocket 连接状态
 */
export enum WebSocketState {
  CONNECTING = 'CONNECTING',
  OPEN = 'OPEN',
  CLOSING = 'CLOSING',
  CLOSED = 'CLOSED',
}

/**
 * WebSocket 事件类型
 */
export type WebSocketEventType =
  | 'open'
  | 'message'
  | 'close'
  | 'error'
  | 'custom'
  | 'state_change'
  | string;

/**
 * WebSocket 回调函数
 */
export type WebSocketCallback = (data: any) => void;

/**
 * WebSocket 管理器类
 * 负责处理 WebSocket 连接和消息处理
 */
export class WebSocketManager {
  private ws: WebSocket | null = null;
  private state: WebSocketState = WebSocketState.CLOSED;
  private reconnectAttempts: number = 0;
  private readonly maxReconnectAttempts: number;
  private readonly reconnectInterval: number;
  private readonly timeoutMs: number;
  private eventListeners: Map<WebSocketEventType, WebSocketCallback[]> = new Map();
  private config: WebSocketConfig;

  /**
   * 构造函数
   * @param config WebSocket 配置
   */
  constructor(config: WebSocketConfig) {
    this.config = config;
    this.maxReconnectAttempts = config.reconnectAttempts || 3;
    this.reconnectInterval = config.reconnectInterval || 2000;
    this.timeoutMs = config.timeoutMs || 10000;
  }

  /**
   * 连接 WebSocket
   * @param timeoutMs 连接超时时间
   */
  public async connect(
    timeoutMs?: number
  ): Promise<boolean> {
    if (this.ws && this.state === WebSocketState.OPEN) {
      console.log('WebSocket 已连接');
      return true;
    }

    return new Promise((resolve, reject) => {
      // 使用配置中的 URL
      const fullUrl = this.config.url;

      // 设置连接超时
      const timeout = setTimeout(() => {
        if (this.state !== WebSocketState.OPEN) {
          this.setState(WebSocketState.CLOSED);
          reject(new Error(`WebSocket 连接超时: ${timeoutMs || this.timeoutMs}ms`));
        }
      }, timeoutMs || this.timeoutMs);

      try {
        this.setState(WebSocketState.CONNECTING);
        this.ws = new WebSocket(fullUrl);

        this.ws.onopen = () => {
          clearTimeout(timeout);
          this.setState(WebSocketState.OPEN);
          this.reconnectAttempts = 0;
          this.emit('open', '连接已建立');

          resolve(true);
        };

        this.ws.onmessage = async (event) => {
          try {
            const data = event.data;
            if (data instanceof Blob) {
              // 处理二进制数据
              this.emit('binary', data);
            } else {
              // 处理 JSON 数据
              const jsonMsg = JSON.parse(data);
              const header = jsonMsg.header;
              if (header) {
                this.emit(header.name, jsonMsg);
              }
              this.emit('message', jsonMsg);
            }
          } catch (error) {
            console.error('处理 WebSocket 消息错误:', error);
            this.emit('error', `消息处理错误: ${error instanceof Error ? error.message : String(error)}`);
          }
        };

        this.ws.onclose = (event) => {
          clearTimeout(timeout);
          this.setState(WebSocketState.CLOSED);
          this.emit('close', `连接关闭: ${event.code} ${event.reason}`);

          // 连接意外关闭且启用了重连
          if (this.maxReconnectAttempts > 0 && event.code !== 1000) {
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
              this.reconnectAttempts++;
              console.log(`WebSocket 连接关闭，尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
              setTimeout(() => this.connect(), this.reconnectInterval);
            } else {
              console.error('WebSocket 重连失败，已达到最大重试次数');
              this.emit('failed', '重连失败，已达到最大重试次数');
              reject(new Error('WebSocket 重连失败，已达到最大重试次数'));
            }
          }
        };

        this.ws.onerror = (event) => {
          console.error('WebSocket 错误:', event);
          this.emit('error', '连接错误');

          // 只在连接阶段处理错误，连接后的错误由 onclose 处理
          if (this.state === WebSocketState.CONNECTING) {
            clearTimeout(timeout);
            this.setState(WebSocketState.CLOSED);
            reject(new Error('WebSocket 连接失败'));
          }
        };
      } catch (error) {
        clearTimeout(timeout);
        this.setState(WebSocketState.CLOSED);
        console.error('创建 WebSocket 实例错误:', error);
        reject(error);
      }
    });
  }

  /**
   * 关闭 WebSocket 连接
   */
  public async close(): Promise<void> {
    if (!this.ws || this.state === WebSocketState.CLOSED) {
      return;
    }

    return new Promise<void>((resolve) => {
      // 设置超时，确保即使 close 事件没有触发也能继续执行
      const timeout = setTimeout(() => {
        this.setState(WebSocketState.CLOSED);
        resolve();
      }, 2000);

      try {
        this.setState(WebSocketState.CLOSING);

        // 监听关闭事件完成
        const onClose = () => {
          clearTimeout(timeout);
          this.removeListener('close', onClose);
          resolve();
        };
        this.on('close', onClose);

        // 发送关闭帧并关闭连接
        if (this.ws) {
          this.ws.close(1000, 'Normal closure');
        }
      } catch (error) {
        clearTimeout(timeout);
        console.error('关闭 WebSocket 错误:', error);
        this.setState(WebSocketState.CLOSED);
        resolve();
      }
    });
  }

  /**
   * 发送文本数据
   * @param data 要发送的文本数据
   */
  public send(data: string): boolean {
    if (!this.isConnected()) {
      console.error('WebSocket 未连接，无法发送数据');
      return false;
    }

    try {
      this.ws!.send(data);
      return true;
    } catch (error) {
      console.error('发送数据错误:', error);
      this.emit('error', `发送数据错误: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 发送二进制数据
   * @param data 要发送的二进制数据
   */
  public sendBinary(data: ArrayBuffer | Uint8Array): boolean {
    if (!this.isConnected()) {
      console.error('WebSocket 未连接，无法发送数据');
      return false;
    }

    try {
      this.ws!.send(data);
      return true;
    } catch (error) {
      console.error('发送二进制数据错误:', error);
      this.emit('error', `发送二进制数据错误: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 发送 JSON 数据
   * @param data 要发送的 JSON 数据
   */
  public sendJson(data: any): boolean {
    try {
      const jsonStr = JSON.stringify(data);
      return this.send(jsonStr);
    } catch (error) {
      console.error('JSON 序列化错误:', error);
      this.emit('error', `JSON 序列化错误: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 检查是否已连接
   */
  public isConnected(): boolean {
    return this.ws !== null && this.state === WebSocketState.OPEN;
  }

  /**
   * 获取当前状态
   */
  public getState(): WebSocketState {
    return this.state;
  }

  /**
   * 设置状态并触发状态变更事件
   */
  private setState(state: WebSocketState): void {
    const oldState = this.state;
    this.state = state;
    this.emit('state_change', { oldState, newState: state });
  }

  /**
   * 注册事件监听器
   * @param event 事件类型
   * @param callback 回调函数
   */
  public on(event: WebSocketEventType, callback: WebSocketCallback): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }

    const callbacks = this.eventListeners.get(event)!;
    if (!callbacks.includes(callback)) {
      callbacks.push(callback);
    }
  }

  /**
   * 移除事件监听器
   * @param event 事件类型
   * @param callback 回调函数
   */
  public removeListener(event: WebSocketEventType, callback: WebSocketCallback): void {
    if (!this.eventListeners.has(event)) {
      return;
    }

    const callbacks = this.eventListeners.get(event)!;
    const index = callbacks.indexOf(callback);
    if (index !== -1) {
      callbacks.splice(index, 1);
    }

    if (callbacks.length === 0) {
      this.eventListeners.delete(event);
    }
  }

  /**
   * 清除指定事件的所有监听器
   * @param event 事件类型
   */
  public removeAllListeners(event?: WebSocketEventType): void {
    if (event) {
      this.eventListeners.delete(event);
    } else {
      this.eventListeners.clear();
    }
  }

  /**
   * 触发事件
   * @param event 事件类型
   * @param data 事件数据
   */
  private emit(event: WebSocketEventType, data: any): void {
    if (this.eventListeners.has(event)) {
      const callbacks = this.eventListeners.get(event)!;
      for (const callback of callbacks) {
        try {
          callback(data);
        } catch (error) {
          console.error(`执行 ${event} 事件处理器错误:`, error);
        }
      }
    }
  }
}
