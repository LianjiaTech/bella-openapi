import { useState, useCallback } from 'react';
import { useIME } from './useIME';

/**
 * useChatInput 配置选项
 */
interface UseChatInputOptions {
  /**
   * 外部传入的流式状态，用于判断是否可以发送
   */
  isStreaming?: boolean;
}

/**
 * useChatInput 返回值
 */
interface UseChatInputReturn {
  /**
   * 当前输入值
   */
  value: string;

  /**
   * 设置输入值
   */
  setValue: (value: string) => void;

  /**
   * 是否可以发送
   */
  canSend: boolean;

  /**
   * 清空输入
   */
  clear: () => void;
}

/**
 * 聊天输入 Hook
 *
 * 职责：仅处理输入状态管理和验证逻辑
 * 不包含：发送请求、流管理、消息处理（这些交给 useChatStream）
 *
 * @example
 * ```tsx
 * const chatStream = useChatStream({ onMessage, onError });
 * const chatInput = useChatInput({ isStreaming: chatStream.isStreaming });
 *
 * const handleSend = () => {
 *   if (chatInput.canSend) {
 *     chatStream.send(chatInput.value);
 *     chatInput.clear();
 *   }
 * };
 * ```
 */
export function useChatInput(options: UseChatInputOptions = {}): UseChatInputReturn {

  // IME 输入状态（中文输入法检测）
  const { isComposing } = useIME();

  // 输入状态
  const [value, setValue] = useState<string>('');

  // 清空输入
  const clear = useCallback(() => {
    setValue('');
  }, []);

  // 判断是否可以发送
  // 条件：输入框有内容 且 未在输入中文
  const canSend = value.trim().length > 0 && !isComposing();

  return {
    value,
    setValue,
    canSend,
    clear,
  };
}
