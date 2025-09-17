import React, { useState } from 'react';
import { WaitingForVoiceInput, WaitingForAIResponse } from './WaitingIndicators';

/**
 * 聊天消息组件
 */
export interface ChatMessageProps {
  isUser: boolean;
  content: string;
  isLoading?: boolean;
  /** 深度思考内容 */
  reasoning_content?: string;
  error?: string;
  /** 是否包含需要以HTML方式渲染的内容 */
  dangerousHTML?: boolean;
}

export const ChatMessage = ({ 
  isUser, 
  content, 
  reasoning_content, 
  error,
  isLoading = false,
  dangerousHTML = false 
}: ChatMessageProps) => {
  const [isReasoningExpanded, setIsReasoningExpanded] = useState(true);
  
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-3`}>
      <div className={`flex ${isUser ? 'flex-row-reverse' : 'flex-row'} max-w-[85%]`}>
        <div className={`flex-shrink-0 h-8 w-8 rounded-full flex items-center justify-center ${isUser ? 'bg-blue-500 ml-2' : 'bg-gray-400 mr-2'}`}>
          {isUser ? (
            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-white" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" />
            </svg>
          ) : (
            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-white" viewBox="0 0 20 20" fill="currentColor">
              <path d="M2 10a8 8 0 018-8v8h8a8 8 0 11-16 0z" />
              <path d="M12 2.252A8.014 8.014 0 0117.748 8H12V2.252z" />
            </svg>
          )}
        </div>
        <div className={`py-2 px-3 rounded-lg ${isUser ? 'bg-blue-100 text-blue-900' : 'bg-gray-100 text-gray-800'}`}>
          <div className="whitespace-pre-wrap text-sm">
            {/* 深度思考内容 */}
            {reasoning_content && (
              <div className="bg-yellow-50 pt-2 px-2 rounded-t border-t border-l border-r border-yellow-200">
                <div className="flex justify-between items-center text-xs text-yellow-700 mb-1">
                  <span className="font-medium">深度思考</span>
                  <button
                    onClick={() => setIsReasoningExpanded(!isReasoningExpanded)}
                    className="text-yellow-600 hover:text-yellow-800 focus:outline-none"
                  >
                    {isReasoningExpanded ? '收起' : '展开'}
                  </button>
                </div>
                {isReasoningExpanded && (
                  <div className="whitespace-pre-wrap text-gray-700">{reasoning_content}</div>
                )}
              </div>
            )}

            {/* 普通内容 - 根据dangerousHTML属性决定渲染方式 */}
            {dangerousHTML ? (
              <div 
                className="content-wrapper" 
                dangerouslySetInnerHTML={{ __html: content }}
              />
            ) : (
              <div className="content-wrapper">{content}</div>
            )}
            
            {isLoading && (
              <span className="inline-block ml-1">
                {isUser ? <WaitingForVoiceInput /> : <WaitingForAIResponse />}
              </span>
            )}
            {error && (
              <span className="text-red-500">
                {error}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};