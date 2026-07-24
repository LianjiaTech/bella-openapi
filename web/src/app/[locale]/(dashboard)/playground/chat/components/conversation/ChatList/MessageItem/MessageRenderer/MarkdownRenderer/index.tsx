/**
 * MarkdownRenderer 组件
 *
 * 职责：渲染单个 block 的 markdown 内容
 *
 * 功能：
 * - 拼接 segments + segmentBuffer + typingBuffer
 * - 使用 react-markdown 渲染 markdown 内容
 * - 支持 code/blockquote/img 等元素
 * - 使用 useTypingEffect Hook 实现打字机效果
 *
 * 设计：
 * - 使用 useMemo 缓存 fullContent 拼接结果
 * - 使用 useTypingEffect Hook 处理打字机动画
 * - 所有消息都启用打字机动画
 * - 流式结束后立即显示完整内容
 * - 依赖 props 变化触发重渲染
 * - 不修改传入的 block 数据
 *
 * 避免 re-render：
 * - 只有 block props 变化时才触发 re-render
 * - useTypingEffect Hook 内部状态变化只影响当前组件
 * - 不订阅全局 store
 * - 使用 useMemo 缓存 fullContent 拼接结果
 */

import React, { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import CodeBlock from '../components/CodeBlock';
import type { TextBlock, VideoBlock ,MessageBlock} from '@/lib/chat/store/types';
import { useBlockTypingEffect } from '../../../../hooks/useBlockTypingEffect';

interface MarkdownRendererProps {
  block: TextBlock;
  streamingMode: boolean;
}

export function MarkdownRenderer({ block, streamingMode }: MarkdownRendererProps) {
  // 只有当 segments/segmentBuffer/typingBuffer 真正变化时才重新拼接
  const fullContent = useMemo(() => {
    return block.segments.join('') + block.segmentBuffer + block.typingBuffer;
  }, [block.segments, block.segmentBuffer, block.typingBuffer]);

  // 使用 useBlockTypingEffect Hook 实现打字机效果及 Store 交互
  const displayedText = useBlockTypingEffect(
    fullContent,
    block.id,
    block.isRenderingCompleted,
    streamingMode,
    50
  );

  return (
    <div className="text-sm dark:text-white whitespace-normal leading-loose">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 代码块渲染
          code({ inline, className, children, ...props }: any) {
            const match = /language-(\w+)/.exec(className || '');
            const language = match ? match[1] : '';
            return language === 'text' || !language ? (
              <code className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-800 rounded text-sm font-mono text-red-600 dark:text-red-400" {...props}>
                {children}
              </code>
            ) : (
              <CodeBlock
                lang={language}
                code={String(children).replace(/\n$/, '')}
                className={className}
              />
            );
          },
          // 引用块渲染
          blockquote({ children, ...props }: any) {
            return (
              <blockquote className="border-l-4 border-blue-500 pl-4 py-2 my-2 italic text-gray-700 dark:text-gray-300 bg-blue-50 dark:bg-blue-900/20" {...props}>
                {children}
              </blockquote>
            );
          },
          // 图片渲染
          img({ src, alt, ...props }: any) {
            return (
              <img src={src} alt={alt} className="max-w-full h-auto rounded-lg my-2" {...props} />
            );
          },
        }}
      >
        {displayedText}
      </ReactMarkdown>
    </div>
  );
}
