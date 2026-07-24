'use client';

import { useCallback, useEffect, useMemo } from 'react';
import { ChatInput } from './chatInput';
import { useChatInput, useChatStream, useChatScroll } from './hooks';
import type { ChatInputController } from './types';
import { ChatList } from './ChatList';
import { ScrollToBottomButton } from './ScrollToBottomButton';
import ClearConversationButton from './ClearConversationButton';
import { useChatStore } from '@/lib/chat/store/chat/store';
import { RefreshCw } from 'lucide-react';


/**
 * Conversation 组件（重构版）
 *
 * 职责：
 * 1. 容器组件，组合 ChatList、ChatInput 和 ScrollToBottomButton
 * 2. 整合 useChatInput、useChatStream 和 useChatScroll
 * 3. 处理发送逻辑
 * 4. 统一管理滚动逻辑，通过 props 传递给子组件
 * 5. 接收并传递模型能力信息给 ChatInput
 *
 * 设计说明：
 * - 使用 useChatScroll 管理滚动逻辑，获取 scrollRef、handleScroll、showScrollButton、scrollToBottom
 * - 将 scrollRef 和 handleScroll 传递给 ChatList，避免在子组件中创建重复的滚动实例
 * - 将 showScrollButton 和 scrollToBottom 传递给 ScrollToBottomButton 组件
 * - 确保只有一个 useChatScroll 实例，所有滚动相关操作都基于同一个 scrollRef
 * - 将 modelFeatures 传递给 ChatInput 以控制上传功能
 */
export function Conversation({ config, modelFeatures }: { config: any; modelFeatures: { vision?: boolean; video?: boolean } }) {

  // 🆕 订阅渲染状态
  const renderingBlockIds = useChatStore((s) => s.renderingBlockIds);
  const isRendering = renderingBlockIds.size > 0;

  // 滚动控制 Hook - 获取完整的滚动控制（scrollRef、contentRef、bottomRef、handleScroll、showScrollButton、scrollToBottom）
  const { scrollRef, contentRef, bottomRef, showScrollButton, scrollToBottom,handleScrollToBottom } = useChatScroll();

  // 流控制 Hook
  const { startStream, abortStream, isStreaming } = useChatStream(config);

  // 输入控制 Hook - 解构各个属性（避免依赖整个对象）
  const chatInput = useChatInput();
  const { value, setValue, canSend, clear } = chatInput;

  /**
   * 处理发送消息
   * 优化：只依赖 value、clear、startStream（都是稳定的）
   */
  const handleSend = useCallback(async () => {
    console.log('canSend', canSend);
    if (!canSend) return;

    const content = value;
    console.log('content', content);

    // 清空输入框
    clear();

    // 启动流式对话
    await startStream(content);
  }, [value, canSend, clear, startStream]);

  /**
   * 处理中断
   *
   * 行为：
   * 1. 如果 SSE 还在进行，停止接收数据
   * 2. 停止所有打字机渲染（保留已显示的内容）
   */
  const handleAbort = useCallback(() => {
    // 停止 SSE 数据接收
    if (isStreaming) {
      abortStream();
    }
    // 停止所有打字机渲染
    useChatStore.getState().stopAllRendering();
  }, [isStreaming, abortStream]);

  /**
   * 构造 ChatInput 需要的 controller
   * 优化：使用 useMemo 稳定对象引用
   *
   * 🆕 状态合并：isStreaming || isRendering 都显示为 'streaming' 状态
   * 这样按钮会在"数据流 + 打字机动画"都完成后才变为发送按钮
   */
  const controller: ChatInputController = useMemo(() => ({
    value,
    setValue,
    canSend,
    send: handleSend,
    abort: handleAbort,
    status: (isStreaming || isRendering) ? 'streaming' : 'idle'
  }), [value, setValue, canSend, handleSend, handleAbort, isStreaming, isRendering]);


  return (
    <div className="flex flex-col h-full">
      {/* 消息列表区域 - 添加 relative 定位以支持绝对定位的按钮 */}
      {/* 滚动逻辑由父组件统一管理，通过 props 传递给 ChatList */}
      {/* flex-1 占据剩余空间，overflow-hidden 防止内容溢出 */}
      <div className="relative flex-1 overflow-hidden p-6">
        {/* 内框：与向量化页面 Textarea 边框风格一致，形成外框套内框的视觉层次 */}
        <div className="h-full border border-border rounded-md overflow-hidden">
          <ChatList
            streamingMode={config.streaming}
            scrollRef={scrollRef}
            contentRef={contentRef}
            bottomRef={bottomRef}
            handleScrollToBottom={handleScrollToBottom}
          />
        </div>

        {/* 滚动到底部按钮 - 绝对定位覆盖在内容上方 */}
        <ScrollToBottomButton
          show={showScrollButton}
          onScrollToBottom={handleScrollToBottom}
        />
      </div>
      <ClearConversationButton />
      {/* 输入区域 - flex-shrink-0 防止被压缩，固定在底部 */}
      <div className="flex-shrink-0 border-t border-border p-4">
        <div>
          <ChatInput controller={controller} modelFeatures={modelFeatures} />
        </div>
      </div>
    </div>
  );
}
