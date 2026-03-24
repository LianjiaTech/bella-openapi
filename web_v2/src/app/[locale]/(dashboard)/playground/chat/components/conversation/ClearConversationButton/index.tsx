import { memo, useCallback } from 'react';
import { useChatStore } from '@/lib/chat/store/chat/store';

/**
 * 清除对话按钮组件
 *
 * 职责：
 * - 渲染清除对话的UI按钮
 * - 内部实现清除对话的逻辑
 * - 支持禁用状态
 *
 * 设计说明：
 * - 组件完全自包含，不依赖外部传入的回调函数
 * - 直接调用 useChatStore 的 clearMessages 方法
 * - 使用 useCallback 确保事件处理函数引用稳定
 *
 * @component
 */

interface ClearConversationButtonProps {
  /** 是否禁用按钮 */
  disabled?: boolean;
}

const ClearConversationButton = memo<ClearConversationButtonProps>(({
  disabled = false
}) => {
  /**
   * 清除对话处理函数
   * 使用 useCallback 确保函数引用稳定，避免不必要的重新渲染
   */
  const handleClearConversation = useCallback(() => {
    if (disabled) return;
    useChatStore.getState().clearMessages();
  }, [disabled]);

  return (
    <div
      className="flex justify-end items-center gap-2 bg-gray-100 p-2 cursor-pointer"
      onClick={handleClearConversation}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label="清除对话"
      onKeyDown={(e) => {
        if (!disabled && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault();
          handleClearConversation();
        }
      }}
    >
      <span className={`text-xs ${disabled ? 'text-gray-300 cursor-not-allowed' : 'text-gray-500 hover:text-red-600'}`}>
        清除对话
      </span>
    </div>
  );
});

ClearConversationButton.displayName = 'ClearConversationButton';

export default ClearConversationButton;
