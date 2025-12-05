import { Dispatch, SetStateAction, useEffect, useCallback } from 'react';
import { v4 as uuidv4 } from 'uuid';

/**
 * 消息类型枚举
 */
export enum MessageType {
  TEXT = 'TEXT',
  AUDIO = 'AUDIO',
  IMAGE = 'IMAGE',
  STREAM_ASR = 'STREAM_ASR',
  STREAM_TTS = 'STREAM_TTS'
}

/**
 * 消息来源枚举
 */
export enum MessageSource {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT',
  SYSTEM = 'SYSTEM'
}

/**
 * 消息接口
 */
export interface Message {
  isUser: boolean;
  content: string;
}

/**
 * 增强消息接口
 */
export interface EnhancedMessage extends Message {
  id?: string;
  timestamp?: number;
  type?: MessageType;
  source?: MessageSource;
  metadata?: Record<string, any>;
}
/**
 * 消息处理钩子，支持消息类型、来源、ID和时间戳
 */
export function useMessageHandler(
  messages: EnhancedMessage[],
  setMessages: Dispatch<SetStateAction<EnhancedMessage[]>>,
  scrollToBottom: () => void
) {
  /**
   * 添加新消息
   */
  const addMessage = useCallback((
    content: string,
    isUser: boolean,
    type: MessageType = MessageType.TEXT,
    source: MessageSource = isUser ? MessageSource.USER : MessageSource.ASSISTANT,
    metadata?: Record<string, any>
  ) => {
    const newMessage: EnhancedMessage = {
      id: uuidv4(),
      isUser,
      content,
      timestamp: Date.now(),
      type,
      source,
      metadata
    };
    
    setMessages(prev => [...prev, newMessage]);
    scrollToBottom();
    return newMessage;
  }, [setMessages, scrollToBottom]);

  /**
   * 更新现有消息
   */
  const updateMessage = useCallback((
    id: string,
    updates: Partial<EnhancedMessage>
  ) => {
    setMessages(prev => 
      prev.map(msg => 
        msg.id === id ? { ...msg, ...updates } : msg
      )
    );
    scrollToBottom();
  }, [setMessages, scrollToBottom]);

  /**
   * 更新或创建一个特定来源的最新消息
   */
  const updateLatestMessageBySource = useCallback((
    source: MessageSource,
    content: string,
    type?: MessageType,
    metadata?: Record<string, any>
  ) => {
    setMessages(prev => {
      const newMessages = [...prev];
      const lastMessageIndex = newMessages.findIndex(
        msg => msg.source === source
      );
      
      if (lastMessageIndex >= 0) {
        // 更新现有消息
        newMessages[lastMessageIndex] = {
          ...newMessages[lastMessageIndex],
          content,
          ...(type && { type }),
          ...(metadata && { metadata })
        };
      } else {
        // 添加新消息
        const isUser = source === MessageSource.USER;
        newMessages.push({
          id: uuidv4(),
          isUser,
          content,
          timestamp: Date.now(),
          type: type || MessageType.TEXT,
          source,
          metadata
        });
      }
      
      return newMessages;
    });
    
    scrollToBottom();
  }, [setMessages, scrollToBottom]);

  /**
   * 更新转写文本（兼容原有用法，但添加类型和元数据）
   */
  const updateTranscription = useCallback((
    transcriptionText: string,
    type: MessageType = MessageType.STREAM_ASR,
    metadata?: Record<string, any>
  ) => {
    if (!transcriptionText) return;
    
    setMessages(prev => {
      const newMessages = [...prev];
      // 检查最后一条消息是否是用户消息
      if (newMessages.length > 0 && newMessages[newMessages.length - 1].isUser) {
        // 更新现有用户消息
        const lastMsg = newMessages[newMessages.length - 1];
        newMessages[newMessages.length - 1] = {
          ...lastMsg,
          content: transcriptionText,
          type: type || lastMsg.type || MessageType.TEXT,
          source: MessageSource.USER,
          ...(metadata && { metadata: { ...lastMsg.metadata, ...metadata } })
        };
      } else {
        // 添加新的用户消息
        newMessages.push({
          id: uuidv4(),
          isUser: true,
          content: transcriptionText,
          timestamp: Date.now(),
          type,
          source: MessageSource.USER,
          metadata
        });
      }
      return newMessages;
    });
    
    scrollToBottom();
  }, [setMessages, scrollToBottom]);

  /**
   * 更新LLM响应（兼容原有用法，但添加类型和元数据）
   */
  const updateLlmResponse = useCallback((
    llmResponseText: string,
    type: MessageType = MessageType.TEXT,
    metadata?: Record<string, any>
  ) => {
    if (!llmResponseText) return;
    
    setMessages(prev => {
      const newMessages = [...prev];
      // 检查最后一条消息是否是AI消息
      if (newMessages.length > 0 && !newMessages[newMessages.length - 1].isUser) {
        // 更新现有AI消息
        const lastMsg = newMessages[newMessages.length - 1];
        newMessages[newMessages.length - 1] = {
          ...lastMsg,
          content: llmResponseText,
          type: type || lastMsg.type || MessageType.TEXT,
          source: MessageSource.ASSISTANT,
          ...(metadata && { metadata: { ...lastMsg.metadata, ...metadata } })
        };
      } else {
        // 添加新的AI消息
        newMessages.push({
          id: uuidv4(),
          isUser: false,
          content: llmResponseText,
          timestamp: Date.now(),
          type,
          source: MessageSource.ASSISTANT,
          metadata
        });
      }
      return newMessages;
    });
    
    scrollToBottom();
  }, [setMessages, scrollToBottom]);

  return {
    addMessage,
    updateMessage,
    updateLatestMessageBySource,
    updateTranscription,
    updateLlmResponse
  };
}
