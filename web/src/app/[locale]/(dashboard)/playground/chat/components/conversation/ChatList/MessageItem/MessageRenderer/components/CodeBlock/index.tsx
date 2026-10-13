/**
 * CodeBlock 组件
 *
 * 职责:
 * 1. 显示代码内容,支持语法高亮
 * 2. 提供代码复制功能
 * 3. 支持长代码的展开/收起功能
 *
 * 设计说明:
 * - 使用 <pre> + <code> 标签保持代码格式
 * - 从 className 中解析语言类型(格式: language-xxx)
 * - 使用本地状态管理复制和展开状态,避免全局状态污染
 * - 通过 CSS 控制最大高度,避免过长代码影响页面性能
 *
 * 避免 re-render:
 * - 仅在用户交互(复制/展开)时更新本地状态
 * - 代码内容作为 children 传入,不会触发不必要的更新
 */

'use client';

import { useState } from 'react';
import { copyToClipboard } from '@/lib/utils/clipboard';

interface CodeBlockProps {
  /** 代码语言,可选 */
  lang?: string;
  /** 代码内容 */
  code: string;
  /** CSS 类名,可能包含 language-xxx 格式的语言标识 */
  className?: string;
}

/**
 * 从 className 中解析语言类型
 * @param className CSS 类名字符串
 * @returns 解析出的语言,如果没有则返回 'text'
 */
function parseLangFromClassName(className?: string): string {
  if (!className) return 'text';

  const match = className.match(/language-(\w+)/);
  return match ? match[1] : 'text';
}

export default function CodeBlock({ lang, code, className }: CodeBlockProps) {
  // 解析语言类型
  const language = lang || parseLangFromClassName(className);

  // 复制状态管理
  const [isCopied, setIsCopied] = useState(false);
  // 展开/折叠状态管理 - 默认展开(true)
  const [isExpanded, setIsExpanded] = useState(true);

  /**
   * 处理代码复制
   * 复制成功后显示反馈,2秒后恢复
   */
  const handleCopy = async () => {
    const success = await copyToClipboard(code);
    if (success) {
      setIsCopied(true);
      setTimeout(() => {
        setIsCopied(false);
      }, 2000);
    }
  };

  /**
   * 切换展开/折叠状态
   */
  const handleToggleExpand = () => {
    setIsExpanded(!isExpanded);
  };
console.log('---language ====', language)
  return (
    <div className="relative my-4 rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
      {/* Header: 显示语言和操作按钮 */}
      <div className="flex items-center justify-between border-b border-gray-200 bg-gray-100 px-4 py-2 dark:border-gray-700 dark:bg-gray-800">
        <span className="text-xs font-medium text-gray-600 dark:text-gray-400">
          {language}
        </span>
        <div className="flex items-center gap-2">
          {/* Expand/Collapse 按钮 */}
          <button
            onClick={handleToggleExpand}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-600 transition-colors hover:bg-gray-200 dark:text-gray-400 dark:hover:bg-gray-700"
            title={isExpanded ? 'Collapse code' : 'Expand code'}
          >
            {isExpanded ? (
              <>
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
                </svg>
                <span>Collapse</span>
              </>
            ) : (
              <>
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
                <span>Expand</span>
              </>
            )}
          </button>

          {/* Copy 按钮 */}
          <button
            onClick={handleCopy}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-600 transition-colors hover:bg-gray-200 dark:text-gray-400 dark:hover:bg-gray-700"
            title={isCopied ? 'Copied!' : 'Copy code'}
          >
            {isCopied ? (
              <>
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                <span>Copied</span>
              </>
            ) : (
              <>
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
                <span>Copy</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Code Content */}
      <div
        className={`overflow-x-auto transition-all duration-300 ${
          isExpanded ? 'max-h-[none]' : 'max-h-[100px] overflow-y-hidden'
        }`}
      >
        <pre className="p-4 text-sm">
          <code className={className}>{code}</code>
        </pre>
      </div>

      {/* 折叠时的渐变遮罩 */}
      {!isExpanded && (
        <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-20 bg-gradient-to-t from-gray-50 to-transparent dark:from-gray-900" />
      )}
    </div>
  );
}
