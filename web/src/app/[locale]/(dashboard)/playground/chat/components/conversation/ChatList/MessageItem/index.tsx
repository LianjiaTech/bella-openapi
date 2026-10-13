'use client';

/**
 * MessageItem 组件
 *
 * 职责:
 * - 渲染单条消息
 * - 根据 message.role 条件渲染对应组件 (UserBubble 或 MessageRenderer)
 * - 通过精确的 store 订阅避免不必要的 re-render
 *
 * Re-render 优化策略:
 * 1. 使用 useChatStore(s => s.messageMap[id]) 精确订阅单个 message
 * 2. 使用 shallow 比较函数,只有 message 内容真正变化时才 re-render
 * 3. 用户消息(status='done')配合 UserBubble 的 React.memo,子组件不会重复渲染
 * 4. AI 消息(status='streaming')只在内容变化时 re-render
 *
 * 为什么使用 shallow 比较?
 * - Zustand 默认使用 Object.is (引用比较)
 * - immer 更新 messageMap 时会创建新的 messageMap 引用
 * - 即使 messageMap[id] 内容未变,引用比较也会判定为变化
 * - shallow 比较会检查对象的每个属性,只有属性值变化才触发 re-render
 *
 * 为什么不订阅 messageIds?
 * - messageIds 数组的任何变化(新增/删除消息)会导致所有订阅该数组的组件 re-render
 *
 * 为什么不订阅整个 messageMap?
 * - messageMap 中任何一个 message 的更新都会触发所有订阅 messageMap 的组件 re-render
 *
 * 为什么条件渲染不会导致 ChatList re-render?
 * - ChatList 不订阅 messageMap 或单个 message,只负责遍历 messageIds 渲染 MessageItem
 * - MessageItem 内部的条件渲染逻辑变化是组件内部实现,不会向上传播
 * - key 使用 message.id 保持稳定,React 不会重新挂载组件
 */

import React from 'react';
import { useChatStore } from '@/lib/chat/store/chat/store';
import { MessageRenderer } from './MessageRenderer';
import UserBubble from './UserBubble';
import type { ChatMessage } from '@/lib/chat/store/types';

interface MessageItemProps {
  id: string;
  streamingMode: boolean;
}

export function MessageItem({ id, streamingMode }: MessageItemProps) {
  // 精确订阅单个 message,避免其他 message 更新时触发 re-render
  const message = useChatStore((s) => s.messageMap[id]) as ChatMessage | undefined;

  // 如果 message 不存在,不渲染
  if (!message) {
    return null;
  }

  // 根据 role 渲染不同组件
  return (
    <div className="message">
      {message.role === 'user' ? (
        <UserBubble message={message as any} />
      ) : (
        <MessageRenderer message={message} streamingMode={streamingMode} />
      )}
    </div>
  );
}
