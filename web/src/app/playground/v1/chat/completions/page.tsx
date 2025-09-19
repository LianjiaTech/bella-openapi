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
  // 原始内联数据
  rawInlineData?: string;
  // 处理后的内容（包含HTML标签）
  processedContent?: string;
  // 用于API请求的清理后内容
  apiContent?: string;
  // 错误信息
  error?: string;
  // 多模态内容
  multimodalContent?: any[];
  // 是否包含图像
  hasImage?: boolean;
}

// 多模态内容项类型
interface MultimodalContentItem {
  type: string;
  text?: string;
  image_url?: {
    url: string;
  };
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
  const {userInfo} = useUser();
  
  // 新增状态：用于处理内联数据
  const [isReceivingInline, setIsReceivingInline] = useState(false);
  const [inlineBuffer, setInlineBuffer] = useState('');

  // Refs
  const chatCompletionsWriterRef = useRef<ChatCompletionsProcessor | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 处理内联内容的函数
  function processInlineContent(content: string) {
    // 匹配 <inline><data>...</data><mimeType>...</mimeType></inline> 格式
    const regex = /<inline><data>(.*?)<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g;
    let result = content;
    let match;
    
    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const base64Data = match[1];
      const mimeType = match[2];
      
      if (mimeType.startsWith('image/')) {
        // 创建可显示的图像元素
        const imageElement = `<img src="data:${mimeType};base64,${base64Data}" alt="AI生成的图像" style="max-width: 100%;" />`;
        result = result.replace(fullMatch, imageElement);
      } else {
        // 处理其他类型的内联数据
        result = result.replace(fullMatch, `[包含${mimeType}类型的数据]`);
      }
    }
    
    return result;
  }

  // 提取内联图像数据
  function extractInlineImages(content: string): { text: string, images: Array<{mimeType: string, base64Data: string}> } {
    if (!content) return { text: '', images: [] };
    
    const regex = /<inline><data>(.*?)<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g;
    const images: Array<{mimeType: string, base64Data: string}> = [];
    let textContent = content;
    let match;
    
    // 收集所有图像数据
    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const base64Data = match[1];
      const mimeType = match[2];
      
      if (mimeType.startsWith('image/')) {
        images.push({ mimeType, base64Data });
        // 在文本中用占位符替换图像
        textContent = textContent.replace(fullMatch, '');
      }
    }
    
    return { text: textContent, images };
  }

  // 从HTML内容中提取图像
  function extractImagesFromHTML(content: string): { text: string, images: Array<{mimeType: string, base64Data: string}> } {
    if (!content) return { text: '', images: [] };
    
    const regex = /<img\s+src="data:(.*?);base64,(.*?)"\s+alt=".*?"\s+style=".*?".*?\/>/g;
    const images: Array<{mimeType: string, base64Data: string}> = [];
    let textContent = content;
    let match;
    
    // 收集所有图像数据
    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const mimeType = match[1];
      const base64Data = match[2];
      
      if (mimeType.startsWith('image/')) {
        images.push({ mimeType, base64Data });
        // 在文本中用占位符替换图像
        textContent = textContent.replace(fullMatch, '');
      }
    }
    
    return { text: textContent, images };
  }

  // 创建多模态内容
  function createMultimodalContent(content: string): { multimodalContent: MultimodalContentItem[], hasImage: boolean } {
    // 首先尝试从原始内联格式提取图像
    let extracted = extractInlineImages(content);
    
    // 如果没有找到图像，尝试从HTML格式提取
    if (extracted.images.length === 0) {
      extracted = extractImagesFromHTML(content);
    }
    
    const multimodalContent: MultimodalContentItem[] = [];
    
    // 添加文本内容（如果有）
    if (extracted.text.trim()) {
      multimodalContent.push({
        type: "text",
        text: extracted.text.trim()
      });
    }
    
    // 添加图像内容
    for (const image of extracted.images) {
      multimodalContent.push({
        type: "image_url",
        image_url: {
          url: `data:${image.mimeType};base64,${image.base64Data}`
        }
      });
    }
    
    return { 
      multimodalContent, 
      hasImage: extracted.images.length > 0 
    };
  }

  // 为API请求清理内联数据的函数 - 增强版
  function cleanInlineContentForAPI(content: string) {
    if (!content) return content;
    
    let cleanedContent = content;
    
    // 1. 清理原始内联数据格式
    cleanedContent = cleanedContent.replace(
      /<inline><data>.*?<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g, 
      '[图像内容，MIME类型: $1]'
    );
    
    // 2. 清理已转换为HTML的图像标签
    cleanedContent = cleanedContent.replace(
      /<img\s+src="data:(.*?);base64,.*?"\s+alt=".*?"\s+style=".*?".*?\/>/g,
      '[图像内容，MIME类型: $1]'
    );
    
    // 3. 清理其他可能的base64数据
    cleanedContent = cleanedContent.replace(
      /data:(.*?);base64,[a-zA-Z0-9+/=]+/g,
      '[图像内容，MIME类型: $1]'
    );
    
    return cleanedContent;
  }

  // 添加一个处理模型输出内容的函数
  function processModelOutput(content: string, currentModel: string) {
    if (!content) return content;
    
    // 特定模型的处理规则
    const modelRules: {[key: string]: (text: string) => string} = {
      'gemini-2.5-flash-image-preview': (text) => {
        // 移除每个块末尾的换行符
        return text.endsWith('\n') ? text.slice(0, -1) : text;
      },
      // 可以添加其他模型的特殊处理规则
      'default': (text) => text // 默认不做处理
    };
    
    // 获取当前模型的处理函数，如果没有特定规则则使用默认处理
    const processFunction = modelRules[currentModel] || modelRules.default;
    return processFunction(content);
  }

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
      // 重置内联数据处理状态
      setIsReceivingInline(false);
      setInlineBuffer('');
    });

    writer.on(ChatCompletionsEventType.DELTA, (data) => {
      if (!data.content) return;
      
      // 使用通用处理函数处理内容
      const processedContent = processModelOutput(data.content, model);
      
      // 检查是否开始接收内联数据
      if (processedContent.includes('<inline>') && !processedContent.includes('</inline>')) {
        setIsReceivingInline(true);
        setInlineBuffer(prev => prev + processedContent);
        
        // 更新消息，但不处理内联数据
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            messages[messages.length - 1] = {
              ...lastMsg,
              content: (lastMsg.content || '') + processedContent
            };
          }
          return messages;
        });
        return;
      }
      
      // 处理内联数据的中间和结束
      if (isReceivingInline) {
        const updatedBuffer = inlineBuffer + processedContent;
        setInlineBuffer(updatedBuffer);
        
        // 更新消息内容，但暂不处理
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            messages[messages.length - 1] = {
              ...lastMsg,
              content: (lastMsg.content || '') + processedContent
            };
          }
          return messages;
        });
        
        // 检查是否接收完成
        if (processedContent.includes('</inline>')) {
          setIsReceivingInline(false);
          
          // 处理完整的内联数据
          const processedInlineContent = processInlineContent(updatedBuffer);
          
          // 创建多模态内容
          const { multimodalContent, hasImage } = createMultimodalContent(updatedBuffer);
          
          // 更新消息，替换原始内联数据为处理后的内容
          setMessages(prev => {
            const messages = [...prev];
            if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
              const lastMsg = messages[messages.length - 1];
              const currentContent = lastMsg.content || '';
              
              // 替换内联数据部分
              const newContent = currentContent.replace(updatedBuffer, processedInlineContent);
              
              messages[messages.length - 1] = {
                ...lastMsg,
                content: newContent,
                rawInlineData: updatedBuffer,
                processedContent: processedInlineContent,
                apiContent: cleanInlineContentForAPI(newContent),
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }
            return messages;
          });
          
          // 更新流式响应
          setStreamingResponse(prev => {
            return prev.replace(updatedBuffer, processedInlineContent);
          });
          
          // 清空缓冲区
          setInlineBuffer('');
        }
        return;
      }
      
      // 正常处理非内联数据
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
          } else if(processedContent) {
            current.content = (lastMsg.content || '') + processedContent;
            // 同时更新apiContent
            current.apiContent = cleanInlineContentForAPI(current.content);
            
            // 检查是否需要更新多模态内容
            if (current.content.includes('<img') || current.content.includes('data:image/')) {
              const { multimodalContent, hasImage } = createMultimodalContent(current.content);
              current.multimodalContent = multimodalContent;
              current.hasImage = hasImage;
            }
          }
          messages[messages.length - 1] = current;
        }
        return messages;
      });
      // 直接更新流式响应状态
      setStreamingResponse(prev => prev + processedContent);
    });

    writer.on(ChatCompletionsEventType.FINISH, (data) => {
      setIsLoading(false);
      // 完成时重置流式响应和思考内容
      setStreamingResponse('');
      
      // 确保所有内联数据都已处理完成
      if (isReceivingInline && inlineBuffer) {
        // 如果还有未处理完的内联数据，尝试处理
        const processedContent = processInlineContent(inlineBuffer);
        const { multimodalContent, hasImage } = createMultimodalContent(inlineBuffer);
        
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            const currentContent = lastMsg.content || '';
            
            // 替换内联数据部分
            const newContent = currentContent.replace(inlineBuffer, processedContent);
            
            messages[messages.length - 1] = {
              ...lastMsg,
              content: newContent,
              rawInlineData: inlineBuffer,
              processedContent: processedContent,
              apiContent: cleanInlineContentForAPI(newContent),
              multimodalContent: multimodalContent,
              hasImage: hasImage
            };
          }
          return messages;
        });
        
        // 重置内联数据状态
        setIsReceivingInline(false);
        setInlineBuffer('');
      }
      
      // 最终检查所有消息，确保所有内联内容都已处理
      setMessages(prev => {
        return prev.map(msg => {
          if (msg.role === 'assistant' && msg.content) {
            // 检查消息内容是否包含未处理的内联数据
            if (msg.content.includes('<inline>') && msg.content.includes('</inline>')) {
              const processedContent = processInlineContent(msg.content);
              const { multimodalContent, hasImage } = createMultimodalContent(msg.content);
              
              return {
                ...msg,
                content: processedContent,
                processedContent: processedContent,
                apiContent: cleanInlineContentForAPI(processedContent),
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }
            
            // 检查是否需要更新多模态内容
            if (!msg.multimodalContent && (msg.content.includes('<img') || msg.content.includes('data:image/'))) {
              const { multimodalContent, hasImage } = createMultimodalContent(msg.content);
              return {
                ...msg,
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }
          }
          return msg;
        });
      });
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
      // 重置内联数据状态
      setIsReceivingInline(false);
      setInlineBuffer('');
    });

    return () => {
      // 清理事件监听
      if (writer) {
        writer.removeAllListeners();
      }
    };
  }, [isReceivingInline, inlineBuffer, model]);

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
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');
  };

  // 发送消息 - 支持多模态格式
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
    
    // 重置流式响应和思考内容
    setStreamingResponse('');
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');

    // 清空输入
    setInput('');

    try {
      // 重要：先获取当前所有消息的副本
      const allCurrentMessages = [...messages];
      
      // 添加用户消息到本地副本（不依赖状态更新）
      allCurrentMessages.push(userMessage);
      
      // 确保首条消息是system prompt
      const systemMessage = allCurrentMessages.find(msg => msg.role === 'system') || {
        role: 'system',
        content: systemPrompt
      };

      // 构建请求消息，支持多模态格式
      const requestMessages = [];
      
      // 添加系统消息
      requestMessages.push({
        role: systemMessage.role,
        content: systemMessage.content
      });
      
      // 处理非系统消息
      for (const msg of allCurrentMessages.filter(msg => msg.role !== 'system')) {
        // 如果消息包含图像，使用多模态格式
        if (msg.hasImage && msg.multimodalContent) {
          requestMessages.push({
            role: msg.role,
            content: msg.multimodalContent
          });
        } 
        // 否则，检查是否需要提取图像
        else if (msg.content && (msg.content.includes('<inline>') || 
                 msg.content.includes('data:image/') || 
                 msg.content.includes('<img'))) {
          
          const { multimodalContent, hasImage } = createMultimodalContent(msg.content);
          
          if (hasImage) {
            // 使用多模态格式
            requestMessages.push({
              role: msg.role,
              content: multimodalContent
            });
            
            // 更新消息对象以包含多模态内容
            msg.multimodalContent = multimodalContent;
            msg.hasImage = true;
          } else {
            // 没有图像，使用普通格式
            requestMessages.push({
              role: msg.role,
              content: msg.apiContent || cleanInlineContentForAPI(msg.content)
            });
          }
        } 
        // 普通文本消息
        else {
          requestMessages.push({
            role: msg.role,
            content: msg.content
          });
        }
      }

      // 构建请求对象
      const request = {
        model: model,
        messages: requestMessages,
        stream: true,
        user: userInfo?.userId
      };

      // 在发送请求前，更新UI状态
      setMessages([...allCurrentMessages]);

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
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');
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

                    // 使用处理后的内容（如果有）
                    const displayContent = msg.processedContent || msg.content || '';

                    return (
                      <MessageComponent
                        key={msg.id}
                        isUser={isUser}
                        content={displayContent}
                        reasoning_content={msg.reasoning_content}
                        error={msg.error}
                        isLoading={needsLoadingIndicator}
                        // 添加一个新的属性，表示内容包含HTML
                        dangerousHTML={!!msg.processedContent}
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
