import { useState, useCallback } from 'react';
import { useIME } from './useIME';

/**
 * 内容块类型定义
 */
export type ContentPart =
  | { type: 'text'; text: string }
  | { type: 'image_url'; image_url: { url: string } }
  | { type: 'video_url'; video_url: { url: string },fps?: string }

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
   * 当前输入值，支持纯文本或多模态内容数组
   */
  value: string | ContentPart[];

  /**
   * 设置输入值
   */
  setValue: (value: string | ContentPart[]) => void;

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
 * 支持两种输入类型：
 * 1. string - 纯文本输入
 * 2. ContentPart[] - 多模态输入（文本 + 图片）
 *    - type: "text" 可以为空
 *    - type: "image_url" 必定存在
 *
 * @example
 * ```tsx
 * const chatStream = useChatStream({ onMessage, onError });
 * const chatInput = useChatInput({ isStreaming: chatStream.isStreaming });
 *
 * // 纯文本发送
 * chatInput.setValue("Hello");
 *
 * // 多模态发送（文本可为空）
 * chatInput.setValue([
 *   { type: "text", text: "呢哈" },
 *   { type: "image_url", image_url: { url: "base64..." } }
 * ]);
 *
 * // 仅图片发送
 * chatInput.setValue([
 *   { type: "image_url", image_url: { url: "base64..." } }
 * ]);
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

  // 输入状态，支持 string 或 ContentPart[]
  const [value, setValue] = useState<string | ContentPart[]>('');

  // 清空输入
  const clear = useCallback(() => {
    setValue('');
  }, []);

  // 判断内容是否有效（非空检测）
  const hasContent = (): boolean => {
    if (typeof value === 'string') {
      // 字符串类型：检查是否非空
      return value.trim().length > 0;
    }
    // 数组类型：只要数组不为空就算有内容（图片必存在，文本可为空）
    return value.length > 0;
  };

  // 判断是否可以发送
  // 条件：有有效内容 且 未在输入中文
  const canSend = hasContent() && !isComposing();

  return {
    value,
    setValue,
    canSend,
    clear,
  };
}
