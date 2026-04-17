'use client';

import { useState } from 'react';
import { useBlockTypingEffect } from '../../../../hooks/useBlockTypingEffect';

/**
 * ReasoningBlock 组件
 *
 * 职责：
 * 1. 展示 AI 思考内容（折叠/展开交互）
 * 2. 提供视觉化的思考过程区分（蓝色边框、图标标识）
 * 3. 使用打字机效果展示思考内容
 * 4. 优化用户体验：默认展开，点击可收起
 *
 * 避免 re-render 策略：
 * - 仅使用单一 state (open) 控制展开/收起
 * - useTypingEffect Hook 内部状态变化只影响当前组件
 * - 不依赖外部 store 或 context
 */

export function ReasoningBlock({ content, streamingMode }: any) {
  // 唯一状态：控制展开/收起
  const [open, setOpen] = useState(false);

  // 拼接思考内容
  const fullContent = content.typingBuffer + content.segments.join('') || '';

  // 使用 useBlockTypingEffect Hook 实现打字机效果及 Store 交互
  const displayedText = useBlockTypingEffect(
    fullContent,
    content.id,
    content.isRenderingCompleted,
    streamingMode,
    50
  );

  // 切换展开/收起状态
  const handleToggle = () => {
    setOpen(!open);
  };

  return (
    <div className="rounded-lg border-2 border-blue-500/30 bg-blue-50/50 dark:bg-blue-950/20 overflow-hidden">
      {/* Header: 可点击的标题栏 */}
      <div
        className="flex items-center gap-2 px-4 py-2.5 cursor-pointer hover:bg-blue-100/50 dark:hover:bg-blue-900/20 transition-colors select-none"
        onClick={handleToggle}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleToggle();
          }
        }}
      >
        {/* 图标 */}
        <span className="text-lg leading-none">🧠</span>

        {/* 标题 */}
        <span className="text-sm font-medium text-blue-700 dark:text-blue-300">
          思考
        </span>

        {/* 右侧：箭头 + 提示文字 */}
        <div className="ml-auto flex items-center gap-1.5 text-xs text-blue-600 dark:text-blue-400">
          {/* 箭头图标 */}
          <svg
            className={`w-4 h-4 transition-transform duration-200 ${
              open ? 'rotate-180' : ''
            }`}
            viewBox="0 0 16 16"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M4 6L8 10L12 6"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>

          {/* 提示文字 */}
          <span>{open ? '收起' : '点击展开'}</span>
        </div>
      </div>

      {/* Body: 思考内容（根据 open 状态显示/隐藏） */}
      {open && (
        <div className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300 border-t border-blue-500/20 bg-white/50 dark:bg-blue-950/10 whitespace-pre-wrap break-words">
          {displayedText}
        </div>
      )}
    </div>
  );
}
