'use client';

import { memo, useCallback } from 'react';
import { ArrowDown } from 'lucide-react';

/**
 * ScrollToBottomButton 组件
 *
 * 职责：
 * 1. 提供悬浮按钮，点击后触发滚动到底部操作
 * 2. 显示向下箭头图标，提示用户可以快速回到底部
 * 3. 根据滚动位置自动显示/隐藏按钮
 *
 * 设计说明：
 * - 纯展示组件，接收显示状态和滚动函数作为 props
 * - 使用 absolute 定位实现悬浮效果，位于父容器右下角
 * - 圆形按钮设计，带阴影和 hover 效果
 * - 使用 ArrowDown 图标表示"滚动到底部"语义
 * - 不显示时返回 null，不占用 DOM 空间
 *
 * 避免 re-render：
 * - 使用 useCallback 稳定化点击回调
 * - 使用 memo 包裹，避免父组件更新时不必要的重渲染
 */

interface ScrollToBottomButtonProps {
  /** 是否显示按钮 */
  show: boolean;
  /** 滚动到底部的函数 */
  onScrollToBottom: () => void;
  /** 自定义样式类名 */
  className?: string;
}

export const ScrollToBottomButton = memo(function ScrollToBottomButton({
  show,
  onScrollToBottom,
  className = '',
}: ScrollToBottomButtonProps) {
  // 处理点击事件，触发平滑滚动
  const handleClick = useCallback(() => {
    onScrollToBottom();
  }, [onScrollToBottom]);

  // 不显示时返回 null，不占用 DOM
  if (!show) {
    return null;
  }

  return (
    <button
      onClick={handleClick}
      className={`
        group
        absolute bottom-4 right-4
        w-10 h-10
        flex items-center justify-center
        rounded-full
        bg-background
        border border-border
        shadow-lg
        hover:bg-accent
        hover:shadow-xl
        active:scale-95
        transition-all duration-200
        cursor-pointer
        ${className}
      `.trim()}
      aria-label="Scroll to bottom"
      type="button"
    >
      <ArrowDown className="w-5 h-5 text-foreground group-hover:text-white transition-colors" />
    </button>
  );
});
