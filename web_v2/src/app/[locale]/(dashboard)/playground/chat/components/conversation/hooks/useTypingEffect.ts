/**
 * useTypingEffect Hook
 *
 * 职责：
 * - 提供打字机效果的通用逻辑
 * - 输入完整文本，返回逐步显示的文本
 * - 使用 RAF (requestAnimationFrame) 实现平滑动画
 *
 * 功能：
 * - 可配置打字速度（字符/秒）
 * - 自动处理内容变化（增长/缩短）
 * - 内容增长时继续动画，缩短时重置显示长度
 *
 * 设计：
 * - 使用 useState 维护已显示的字符长度
 * - 使用 useRef 记录上次更新时间戳，避免重复计算
 * - 使用 useEffect 监听 content 长度变化，触发动画
 * - 动画完成后自动停止，节省性能
 *
 * 避免 re-render：
 * - Hook 内部状态变化只影响调用组件
 * - 不依赖全局 store 或 context
 * - 只依赖 content.length，避免字符串比较导致的不必要更新
 * - 使用 RAF 而非 setInterval，减少 state 更新频率
 *
 * @param content - 完整的文本内容
 * @param charsPerSecond - 配置打字速度（字符/秒），默认 50
 * @returns 打字机效果状态对象
 *
 * @example
 * ```tsx
 * function MyComponent({ text }: { text: string }) {
 *   const { displayedText, isTyping, currentLength } = useTypingEffect(text, 50);
 *   return <div>{displayedText}</div>;
 * }
 * ```
 */

import { useState, useEffect, useRef } from 'react';

/**
 * useTypingEffect 返回值
 */
interface UseTypingEffectReturn {
  /** 当前应该显示的文本内容 */
  displayedText: string
  /** 是否正在打字（未完成渲染） */
  isTyping: boolean
  /** 当前已渲染的字符长度 */
  currentLength: number
}

export function useTypingEffect(
  content: string,
  charsPerSecond: number = 50,
  streamingMode: boolean = true
): UseTypingEffectReturn {
  // 已显示的字符长度
  const [displayedLength, setDisplayedLength] = useState(0);

  // 上次更新时间戳
  const lastUpdateTimeRef = useRef(0);

  // 打字机动画效果
  useEffect(() => {
    // 如果已全部显示，无需动画
    if (displayedLength >= content.length || !streamingMode) {
      setDisplayedLength(content.length);
      return;
    }

    let animationFrameId: number;

    const animate = (timestamp: number) => {
      // 初始化时间戳
      if (lastUpdateTimeRef.current === 0) {
        lastUpdateTimeRef.current = timestamp;
      }

      // 计算距离上次更新的时间
      const elapsed = timestamp - lastUpdateTimeRef.current;
      const intervalMs = 1000 / charsPerSecond; // 每个字符的间隔时间

      if (elapsed >= intervalMs) {
        // 计算应该显示多少个字符
        const charsToAdd = Math.floor(elapsed / intervalMs);
        setDisplayedLength(prev => Math.min(prev + charsToAdd, content.length));
        lastUpdateTimeRef.current = timestamp;
      }

      // 继续动画
      if (displayedLength < content.length) {
        animationFrameId = requestAnimationFrame(animate);
      }
    };

    animationFrameId = requestAnimationFrame(animate);

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [content.length, displayedLength, charsPerSecond, streamingMode]); // 只依赖 length，避免字符串比较

  // 当 content 长度变化时，检查是否需要重置 displayedLength
  useEffect(() => {
    // 如果新内容比当前显示的长，保持 displayedLength 继续增长
    // 如果新内容比当前显示的短（比如清空了 buffer），重置为新内容长度
    if (content.length < displayedLength) {
      setDisplayedLength(content.length);
      lastUpdateTimeRef.current = 0;
    }
  }, [content.length, displayedLength]); // 只依赖长度，避免不必要的触发

  // 返回应该显示的内容和打字状态
  return {
    displayedText: content.slice(0, displayedLength),
    isTyping: displayedLength < content.length,
    currentLength: displayedLength,
  };
}
