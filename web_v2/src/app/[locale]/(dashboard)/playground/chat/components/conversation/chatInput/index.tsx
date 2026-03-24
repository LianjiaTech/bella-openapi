'use client';

import { useCallback, KeyboardEvent, useEffect, memo } from 'react';
import { Textarea } from '@/components/ui/textarea';
import type { ChatInputController } from '../types';
import { Send, CircleStop } from 'lucide-react';

export interface ChatInputProps {
  controller: ChatInputController;
  onCompositionStart?: () => void;
  onCompositionEnd?: () => void;
}

/**
 * 聊天输入框组件
 *
 * 职责：
 * - 提供消息输入界面
 * - 处理发送和中止消息操作
 * - 支持流式输出状态下的交互
 *
 * 性能优化：
 * - 使用 React.memo 避免不必要的重渲染
 * - 自定义比较函数只比较 controller 的关键状态属性
 * - 函数引用变化不会触发重渲染
 */
const ChatInputComponent = ({
  controller
}: ChatInputProps) => {
  console.log('ChatInput render');
  const { value, setValue, canSend, send, abort, status } = controller;

  // 判断当前是否正在流式输出
  const isStreaming = status === 'streaming';

  // 判断按钮是否禁用(输入框为空且非流式状态)
  const isDisabled = !isStreaming && !value.trim();

  useEffect(() => {
    console.log('status', status);
  }, [status]);

  /**
   * 处理按钮点击
   *
   * 行为逻辑：
   * - streaming状态 + 有输入内容 → 先abort，再发送新消息
   * - streaming状态 + 无输入内容 → 仅abort
   * - 非streaming状态 + 有内容 → 发送消息
   */
  const handleButtonClick = useCallback(async () => {
    if (isStreaming) {
      // 先停止当前流式输出
      abort();

      // 如果输入框有内容,发送新消息
      if (value.trim()) {
        setValue('');
        await send();
      }
    } else if (canSend) {
      // 正常发送
      setValue('');
      await send();
    }
  }, [isStreaming, canSend, value, abort, send, setValue]);

  /**
   * 处理键盘事件
   *
   * Enter 行为：
   * - streaming状态 + 有输入 → 先abort，再发送新消息
   * - streaming状态 + 无输入 → 不处理（避免误触）
   * - 非streaming状态 + 有输入 → 发送消息
   * - Shift+Enter → 换行
   */
  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key !== 'Enter' || e.shiftKey) return

      if (e.nativeEvent.isComposing) return

      if (!value.trim()) return

      e.preventDefault();

      handleButtonClick();

    },
    [canSend, isStreaming, value, handleButtonClick]
  );

  return (
    <div className="relative w-full">
      {/* 输入框容器 */}
      <div className="relative">
        <Textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入消息... (Enter发送, Shift+Enter换行)"
          className="resize-none overflow-y-auto"
          disabled={status === 'connecting'}
        />

        {/* 发送/停止按钮 - 绝对定位在右下角 */}
        <div
          onClick={isStreaming ? abort : (isDisabled ? undefined : handleButtonClick)}
          className={`
            absolute right-2 bottom-2 w-8 h-8
            flex items-center justify-center
            rounded-md font-medium
            transition-colors duration-200
            text-white
            ${isStreaming
              ? 'bg-red-500 hover:bg-red-600 cursor-pointer'
              : isDisabled
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-500 hover:bg-blue-600 cursor-pointer'
            }
          `}
        >
          {isStreaming ? <CircleStop className="w-5 h-5" /> : <Send className="w-5 h-5" />}
        </div>
      </div>
    </div>
  );
};

export const ChatInput = memo(ChatInputComponent);
