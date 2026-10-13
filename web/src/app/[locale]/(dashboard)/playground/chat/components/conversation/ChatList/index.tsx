'use client';

import React from 'react';
import { useChatStore } from '@/lib/chat/store/chat/store';
import { MessageItem } from './MessageItem';
import { MessageSquare } from 'lucide-react';

/**
 * ChatList 组件
 *
 * 职责：
 * 1. 渲染消息列表（遍历 messageIds）
 * 2. 接收并使用父组件传入的滚动控制（scrollRef、handleScroll）
 *
 * 设计说明：
 * - 只订阅 messageIds 而非 messageMap，避免消息内容变化时触发列表 re-render
 * - 消息内容的渲染和更新由 MessageItem 组件独立处理
 * - 滚动逻辑由父组件 Conversation 通过 useChatScroll Hook 管理，通过 props 传入
 * - 不再在内部调用 useChatScroll，避免创建重复的滚动实例
 *
 * 避免 re-render 策略：
 * 1. 使用 React.memo 阻止父组件 re-render 时的连带 re-render
 * 2. ChatList 只订阅 messageIds（数组引用变化时才 re-render）
 * 3. MessageItem 内部订阅 messageMap[id]（内容变化只影响单个组件）
 * 4. scrollRef 和 handleScroll 是稳定的引用，不会导致 re-render
 */

interface ChatListProps {
  /** 是否启用流式模式 */
  streamingMode: boolean;
  /** 滚动容器的 ref，由父组件传入 */
  scrollRef: React.RefObject<HTMLDivElement | null>;
  /** 内容容器的 ref，用于监听内容高度变化，由父组件传入 */
  contentRef: React.RefObject<HTMLDivElement | null>;
  /** 滚动事件处理函数，由父组件传入 */
  handleScrollToBottom: () => void;
  /** 底部标记元素的 ref，用于监听可见性，由父组件传入 */
  bottomRef: React.RefObject<HTMLDivElement | null>;
}

export const ChatList = React.memo(function ChatList({
  streamingMode,
  scrollRef,
  contentRef,
  bottomRef,
  handleScrollToBottom
}: ChatListProps) {
  // 只订阅 messageIds，消息内容变化不会触发此组件 re-render
  const messageIds = useChatStore((s) => s.messageIds);

  // 空状态：无消息时展示引导提示，避免在有消息时触发 re-render
  if (messageIds.length === 0) {
    return (
      <div className="h-full flex flex-col items-center justify-center gap-3 text-muted-foreground select-none">
        <MessageSquare className="w-10 h-10 opacity-30" />
        <span className="text-sm">开始一个新的对话</span>
      </div>
    );
  }

  return (
    <div
      ref={scrollRef}
      className="h-full overflow-y-auto px-4 py-6 space-y-4"
    >
      {/* onScroll={handleScrollToBottom} */}
      <div ref={contentRef} >
      {messageIds.map((id) => (
        <MessageItem key={id} id={id} streamingMode={streamingMode} />
      ))}
      </div>
      <div ref={bottomRef} className="h-[40px] w-full invisible"></div>
    </div>
  );
});
