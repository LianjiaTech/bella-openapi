import React, { useEffect, useRef } from 'react';
import { ChatMessage } from "@/components/ui/ChatMessage";

interface Message {
  isUser: boolean;
  content: string;
}

interface AudioChatProps {
  messages: Message[];
  transcriptionText: string;
  llmResponseText: string;
  onMessagesUpdate: (updater: (prev: Message[]) => Message[]) => void;
}

/**
 * 音频聊天组件，负责显示对话消息和处理消息更新
 */
export const AudioChat: React.FC<AudioChatProps> = ({
  messages,
  transcriptionText,
  llmResponseText,
  onMessagesUpdate
}) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 滚动到最新消息
  const scrollToBottom = () => {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, 100);
  };

  // 当转写文本变化时，更新消息列表
  useEffect(() => {
    if (transcriptionText) {
      onMessagesUpdate((prev: Message[]) => {
        const newMessages = [...prev];
        // 如果最后一条是用户消息，则更新它
        if (newMessages.length > 0 && newMessages[newMessages.length - 1].isUser) {
          newMessages[newMessages.length - 1].content = transcriptionText;
        } else {
          // 否则添加新的用户消息
          newMessages.push({ isUser: true, content: transcriptionText });
        }
        return newMessages;
      });
      scrollToBottom();
    }
  }, [transcriptionText, onMessagesUpdate]);

  // 当LLM回复变化时，更新消息列表
  useEffect(() => {
    if (llmResponseText) {
      onMessagesUpdate((prev: Message[]) => {
        const newMessages = [...prev];
        // 如果最后一条是AI消息，则更新它
        if (newMessages.length > 0 && !newMessages[newMessages.length - 1].isUser) {
          newMessages[newMessages.length - 1].content = llmResponseText;
        } else {
          // 否则添加新的AI消息
          newMessages.push({ isUser: false, content: llmResponseText });
        }
        return newMessages;
      });
      scrollToBottom();
    }
  }, [llmResponseText, onMessagesUpdate]);

  return (
    <div className="flex flex-col gap-4 overflow-y-auto max-h-[70vh] p-4">
      {messages.map((message, index) => (
        <ChatMessage 
          key={index} 
          isUser={message.isUser} 
          content={message.content} 
        />
      ))}
      <div ref={messagesEndRef} />
    </div>
  );
};
