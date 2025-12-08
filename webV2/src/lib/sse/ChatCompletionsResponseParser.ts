import { ChatCompletionsResponse } from './types';

/**
 * Chat Completions 响应解析器
 */
export class ChatCompletionsResponseParser {
  /**
   * 检查是否为完成标记
   */
  static isDone(data: string): boolean {
    return data === '[DONE]';
  }

  /**
   * 解析响应数据
   */
  static parseData(data: string): ChatCompletionsResponse | null {
    try {
      const response = JSON.parse(data) as ChatCompletionsResponse;
      return response;
    } catch {
      return null;
    }
  }
}
