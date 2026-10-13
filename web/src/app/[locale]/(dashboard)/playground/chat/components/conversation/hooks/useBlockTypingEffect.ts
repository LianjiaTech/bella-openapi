import { useEffect } from 'react';
import { useTypingEffect } from './useTypingEffect';
import { useChatStore } from '@/lib/chat/store/chat/store';

/**
 * useBlockTypingEffect Hook
 *
 * 职责：
 * 1. 封装打字机效果的启用逻辑
 * 2. 自动上报渲染长度到 Store
 * 3. 自动注册/注销打字状态到 Store
 * 4. 处理组件卸载时的清理逻辑
 *
 * 设计：
 * - 统一管理打字机效果与 Store 的交互逻辑
 * - 避免在多个组件中重复相同的业务逻辑
 * - 自动处理流式结束后的状态更新
 *
 * 避免 re-render：
 * - 只在必要的依赖变化时触发 useEffect
 * - 使用 useChatStore.getState() 避免订阅整个 Store
 * - useTypingEffect Hook 内部状态变化只影响当前组件
 *
 * @param content - 要显示的内容
 * @param blockId - Block ID
 * @param isRenderingCompleted - 是否已完成渲染
 * @param streamingMode - 是否开启流式模式
 * @param typingSpeed - 打字速度（毫秒/字符），默认 50ms
 * @returns displayedText - 当前显示的文本
 */
export function useBlockTypingEffect(
  content: string,
  blockId: string,
  isRenderingCompleted: boolean,
  streamingMode: boolean,
  typingSpeed: number = 50
): string {
  // 获取流式消息 ID（用于判断是否处于流式状态）
  const { streamingMessageId } = useChatStore.getState();

  // 使用打字机效果处理内容
  const enableTypingEffect = !isRenderingCompleted && streamingMode;
  const { displayedText, isTyping, currentLength } = useTypingEffect(
    content,
    typingSpeed,
    enableTypingEffect
  );

  // 🆕 上报当前渲染长度到 Store
  useEffect(() => {
    const { updateRenderingLength } = useChatStore.getState();
    updateRenderingLength(blockId, currentLength);
  }, [blockId, currentLength]);

  // 🆕 注册/注销打字状态到 Store
  useEffect(() => {
    const { startBlockRendering, finishBlockRendering, updateBlockRenderingCompleted } =
      useChatStore.getState();
    if (isTyping) {
      startBlockRendering(blockId);
    } else {
      finishBlockRendering(blockId);
      // ✅ 只有在非流式状态下才更新 isRenderingCompleted
      if (!streamingMessageId) {
        updateBlockRenderingCompleted(blockId, true);
      }
    }

    // 组件卸载时清理
    return () => {
      finishBlockRendering(blockId);
    };
  }, [isTyping, blockId, streamingMessageId]);

  return displayedText;
}
