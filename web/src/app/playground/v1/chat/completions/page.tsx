'use client';

import React, {useEffect, useRef, useState} from 'react';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Alert, AlertDescription} from '@/components/ui/alert';
import {
  ChatCompletionsEventType,
  ChatCompletionsProcessor,
  ChatMessage
} from '@/components/playground/ChatCompletionsProcessor';
import {api_host} from '@/config';
import {SendIcon} from 'lucide-react';
import {Textarea} from '@/components/ui/textarea';
import {ChatMessage as MessageComponent} from '@/components/ui/ChatMessage';
import {v4 as uuidv4} from 'uuid';
import {useUser} from "@/lib/context/user-context";

// 默认system prompt
const DEFAULT_SYSTEM_PROMPT = '你是一个智能助手，可以回答各种问题并提供帮助。请尽量提供准确、有帮助的信息。';

// 增强版消息类型定义，包含ID
// 参照MessageHandler.ts的实现
interface EnhancedChatMessage extends ChatMessage {
  id: string;
  timestamp?: number;
  // 深度思考内容
  reasoning_content?: string;
  // 是否是思考内容
  isReasoningContent?: boolean;
  // 会话轮次，用于标记当前回复属于哪个轮次
  sessionTurn?: number;
}

export default function ChatCompletions() {
  // 状态管理
  const [input, setInput] = useState('');
  const [systemPrompt, setSystemPrompt] = useState(DEFAULT_SYSTEM_PROMPT);
  const [isEditingPrompt, setIsEditingPrompt] = useState(false);
  const [isPromptExpanded, setIsPromptExpanded] = useState(false);
  const [messages, setMessages] = useState<EnhancedChatMessage[]>([{
    id: uuidv4(),
    role: 'system',
    content: DEFAULT_SYSTEM_PROMPT,
    timestamp: Date.now()
  }]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [model, setModel] = useState('');
  const [streamingResponse, setStreamingResponse] = useState('');
  const {userInfo} = useUser()

  // Refs
  const chatCompletionsWriterRef = useRef<ChatCompletionsProcessor | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 从URL中获取model参数
  useEffect(() => {
    // 从URL参数中获取model值
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    // 如果有参数并且是有效的模型，则使用它
    setModel(modelParam || '');
  }, []);

  // 初始化ChatCompletionsWriter
  useEffect(() => {
    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const host = api_host || window.location.host;
    chatCompletionsWriterRef.current = new ChatCompletionsProcessor({
      url: `${protocol}//${host}/v1/chat/completions`,
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const writer = chatCompletionsWriterRef.current;

    // 设置事件监听
    writer.on(ChatCompletionsEventType.START, () => {
      setStreamingResponse('');
      setMessages(prev => {
        const newMessages = [...prev];
        newMessages.push({
          id: uuidv4(),
          role: 'assistant',
          content: '',
          timestamp: Date.now()
        });
        return newMessages;
      });
    });

    writer.on(ChatCompletionsEventType.DELTA, (data) => {
      setMessages(prev => {
        const messages = [...prev];
        // 检查最后一条消息是否是助手消息
        if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
          // 更新现有助手消息
          const lastMsg = messages[messages.length - 1];
          const current : EnhancedChatMessage = {
            ...lastMsg,
          }
          if(data.reasoning_content && data.isReasoningContent) {
            current.reasoning_content = (lastMsg.reasoning_content || '') + data.reasoning_content;
            current.isReasoningContent = true
          } else if(data.content) {
            current.content = (lastMsg.content || '') + data.content
          }
          messages[messages.length - 1] = current;
        }
        return messages;
      });
      // 直接更新流式响应状态
      setStreamingResponse(prev => prev + data.content);
    });

    writer.on(ChatCompletionsEventType.FINISH, (data) => {
      setIsLoading(false);
      // 完成时重置流式响应和思考内容
      setStreamingResponse('');
    });

    writer.on(ChatCompletionsEventType.ERROR, (error) => {
      setError(`请求错误: ${error}`);
      setMessages(prev => {
        const messages = [...prev];
        // 检查最后一条消息是否是助手消息
        if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
          // 更新现有助手消息
          const lastMsg = messages[messages.length - 1];
          messages[messages.length - 1] = {
            ...lastMsg,
            error: error
          };
        }
        return messages;
      });
      setIsLoading(false);
    });

    return () => {
      // 清理事件监听
      if (writer) {
        writer.removeAllListeners();
      }
    };
  }, []);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingResponse]);

  // 更新系统提示词
  const updateSystemPrompt = () => {
    // 更新messages中的system消息
    setMessages(prev => [
      { id: uuidv4(), role: 'system', content: systemPrompt, timestamp: Date.now() },
      ...prev.filter(msg => msg.role !== 'system')
    ]);
    setIsPromptExpanded(false);
    setIsEditingPrompt(false);
  };

  // 清除对话
  const clearConversation = () => {
    // 保留系统提示词，清除所有其他消息
    const systemMsg = messages.find(msg => msg.role === 'system') || {
      id: uuidv4(),
      role: 'system',
      content: systemPrompt,
      timestamp: Date.now()
    };
    setMessages([systemMsg]);
    setError('');
    setStreamingResponse('');
  };

  // 发送消息
  const sendMessage = async () => {
    if (!input.trim() || isLoading) return;

    // 如果正在编辑系统提示词，先保存
    if (isEditingPrompt) {
      updateSystemPrompt();
    }

    // 先停止之前的加载状态
    setIsLoading(false);
    // 清除之前的错误
    setError('');
    // 等待一帧渲染完成，确保不会同时有多条加载消息
    setTimeout(() => {
      setIsLoading(true);
    }, 0);

    // 添加用户消息
    const userMessage: EnhancedChatMessage = {
      id: uuidv4(),
      role: 'user',
      content: input,
      timestamp: Date.now()
    };
    // 重要更改: 爱用函数式更新来确保最新状态
    setMessages(prev => [...prev, userMessage]);

    // 重置流式响应和思考内容
    setStreamingResponse('');

    // 清空输入
    setInput('');

    try {
      // 构建请求
        // 确保首条消息是system prompt
        const systemMessage = messages.find(msg => msg.role === 'system') || {
          role: 'system',
          content: systemPrompt
        };

        // 保留历史对话，只过滤掉system消息和reasoning_content
        const historyMessages = messages
          .filter(msg => msg.role !== 'system')
          .map(msg => {
            // 创建新对象，移除reasoning_content相关属性
            const { reasoning_content, isReasoningContent, ...cleanedMsg } = msg;
            return cleanedMsg;
          });

        const request = {
          model: model,
          messages: [systemMessage, ...historyMessages, userMessage],
          stream: true,
          user: userInfo?.userId
        };

      // 发送请求
      await chatCompletionsWriterRef.current?.send(request);
    } catch (err) {
      setError(`发送请求失败: ${err instanceof Error ? err.message : String(err)}`);
      setIsLoading(false);
    }
  };

  // 取消请求
  const cancelRequest = () => {
    chatCompletionsWriterRef.current?.cancel();
    setIsLoading(false);
  };

  // 处理Enter键发送
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-2xl font-bold">智能问答</h1>

        <div className="mt-2 md:mt-0 flex items-center">
          <span className="text-sm font-medium mr-2">模型:</span>
          <div className="p-2 rounded">
            {model}
          </div>
        </div>
      </div>

      {/* 系统提示词编辑区域 */}
      <div className="mb-3 flex-shrink-0">
        <div
          className="flex items-center cursor-pointer"
          onClick={() => {
            if (!isLoading) {
              if (isPromptExpanded) {
                updateSystemPrompt();
              } else {
                setIsPromptExpanded(true);
                setIsEditingPrompt(true);
              }
            }
          }}
        >
          <span className="text-sm font-medium mr-1">系统提示词：</span>
          {!isPromptExpanded ? (
            <span className="text-sm text-gray-700 overflow-hidden text-ellipsis whitespace-nowrap flex-1">
              {systemPrompt.length > 50 ? systemPrompt.substring(0, 50) + '...' : systemPrompt}
            </span>
          ) : null}
          {!isEditingPrompt && (
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className={`h-4 w-4 ml-1 transition-transform ${isPromptExpanded ? 'rotate-180' : ''}`}
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          )}
        </div>

        {isPromptExpanded && (
          <div className="mt-2">
            <div className="flex flex-col">
              <Textarea
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                className="min-h-[80px] bg-white border border-gray-300 focus:border-gray-400 focus:ring-gray-300"
                placeholder="输入系统提示词..."
                disabled={isLoading}
                autoFocus
              />
              <div className="self-end mt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={updateSystemPrompt}
                  className="text-gray-600 hover:bg-gray-100"
                  disabled={isLoading}
                >
                  保存
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card className="shadow-sm flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden">
          <div className="h-full overflow-y-auto p-3 bg-white rounded">
            {messages.filter(msg => msg.role !== 'system').length === 0 ? (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>开始一个新的对话</p>
              </div>
            ) : (
              <div className="space-y-2">
                {/* 按消息的时间戳顺序显示所有消息 */}
                {(() => {
                  // 只考虑非系统消息，按时间戳排序
                  const visibleMessages = messages
                    .filter(msg => msg.role !== 'system')
                    .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));

                  return visibleMessages.map((msg) => {
                    const isUser = msg.role === 'user';
                    // 根据时间戳或序号确定是否是最新消息 - 只有用户最新消息后没有助手回复时才显示加载
                    const assistantMessages = messages.filter(m => m.role === 'assistant');
                    const lastAssistantMessage = assistantMessages.length > 0 ? assistantMessages[assistantMessages.length - 1] : null;

                    // 没有助手回复的最新用户消息才显示加载状态
                    const needsLoadingIndicator = isLoading && !isUser &&
                      msg.id === lastAssistantMessage?.id;

                    return (
                      <MessageComponent
                        key={msg.id}
                        isUser={isUser}
                        content={msg.content || ''}
                        reasoning_content={msg.reasoning_content}
                        error={msg.error}
                        isLoading={needsLoadingIndicator}
                      />
                    );
                  });
                })()}


                <div ref={messagesEndRef} />
              </div>
            )}
          </div>
        </CardContent>
        <div className="p-2 bg-gray-50 border-t border-gray-100 flex justify-end">
          <Button
            variant="ghost"
            size="sm"
            onClick={clearConversation}
            className="text-gray-500 hover:text-blue-600 hover:bg-blue-50 flex items-center"
            title="清除对话"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="1 4 1 10 7 10"></polyline>
              <polyline points="23 20 23 14 17 14"></polyline>
              <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"></path>
            </svg>
            清除对话
          </Button>
        </div>
      </Card>

      <div className="flex items-end gap-3 mt-3 flex-shrink-0">
        <div className="flex-grow">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入问题..."
            className="h-20 resize-none rounded-xl shadow-sm focus:border-blue-400 focus:ring-blue-400"
            disabled={isLoading}
          />
        </div>

        {isLoading ? (
          <Button
            variant="destructive"
            onClick={cancelRequest}
            className="h-12 px-5 rounded-full shadow-sm">
            取消
          </Button>
        ) : (
          <Button
            onClick={sendMessage}
            disabled={!input.trim()}
            className="h-12 px-6 rounded-full shadow-sm bg-blue-600 hover:bg-blue-700">
            <SendIcon className="h-5 w-5 mr-2" /> 发送
          </Button>
        )}
      </div>

      <div className="mt-2 text-xs text-gray-500 text-center flex-shrink-0">
        <p>提示：按Enter键发送，Shift+Enter换行</p>
      </div>
    </div>
  );
}
