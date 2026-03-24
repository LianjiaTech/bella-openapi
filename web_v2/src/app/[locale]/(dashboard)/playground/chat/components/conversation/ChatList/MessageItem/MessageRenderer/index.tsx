/**
 * MessageRenderer 组件
 *
 * 职责:
 * 1. 渲染 Bot/Assistant 消息及头像
 * 2. 遍历 message.blocks 数组
 * 3. 根据 block.type 分发到对应的渲染组件
 * 4. image 类型使用 ImagePreview,其它类型使用 MarkdownRenderer
 * 5. 将 message.role 传递给 MarkdownRenderer 以控制打字机效果
 *
 * 约束:
 * - 不修改 message 数据 (props 只读)
 * - 不新增 state
 * - 不新增 hooks
 * - key 必须使用 block.id
 *
 * 设计说明:
 * - 纯函数组件,无状态无副作用
 * - 简单的类型判断和组件分发逻辑
 * - 依赖父组件控制 re-render
 * - 用户消息直接展示,助手消息使用打字机效果
 * - Bot 头像使用 lucide-react 的 Bot 图标,与 UserBubble 的 User 图标对称
 *
 * 避免 re-render:
 * - 无内部状态,仅依赖 props
 * - 只有 message.blocks 变化时才重新渲染
 * - Bot 头像为静态 UI 元素,不影响渲染性能
 */

import type { ChatMessage } from '@/lib/chat/store/types';
import { Frown, Bot, Loader2 } from 'lucide-react';
import { ImagePreview } from './ImagePreview';
import { MarkdownRenderer } from './MarkdownRenderer';
import { ReasoningBlock } from './ReasoningBlock';

interface MessageRendererProps {
  /** 消息对象 (只读) */
  message: ChatMessage;
  streamingMode: boolean;
}

export function MessageRenderer({ message, streamingMode }: MessageRendererProps) {
  if (message.status === 'error') {
    return <div className="flex items-start gap-2">
      <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
        <Frown className="w-5 h-5 text-destructive" />
      </div>
      <div className="flex flex-col flex-1">
        <div className="text-sm text-red-500">
          <p>请求不可用：{message.error?.message}</p>
          <p>请查看网络或者切换模型试一试</p>
        </div>
      </div>
    </div>
  }
  if(message.status === 'connecting'){
    return <div className="flex items-start gap-2">
      <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
        <Loader2 className="w-5 h-5 text-primary animate-spin" />
      </div>
    </div>
  }
  return (
    <div className="flex items-start gap-2">
      {/* Bot 头像 */}
      <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
        <Bot className="w-5 h-5 text-primary" />
      </div>

      {/* 消息内容区域 */}
      <div className="flex flex-col flex-1">
        {message.blocks.map((block) => {
          // image 类型 → ImagePreview
          if (block.type === 'image') {
            return <ImagePreview key={block.id} imageUrl={block.url} />;
          }
          if (block.type === 'reasoning_content') {
            return <ReasoningBlock key={block.id} content={block} streamingMode={streamingMode} />;
          }
          return <MarkdownRenderer key={block.id} block={block} streamingMode={streamingMode} />;
        })}
      </div>
    </div>
  );
}
