"use client";

import { useState, useCallback } from "react";
import { useTranslations } from "next-intl";
import { TopBar } from "@/components/layout";
import { ConfigPanel } from "./components/configPanel";
import { Conversation } from "./components/conversation";
import { ChatConfig } from "./types";

/**
 * 职责：聊天 Playground 页面
 * - 从 URL 查询参数中读取 model 值（如 playground/chat?model=testModel99）
 * - 管理聊天配置状态并传递给子组件
 * - 协调配置面板和对话区域的交互
 */
export default function ChatPage() {
  const t = useTranslations("playground.chat");

  // 默认配置
  const [config, setConfig] = useState<ChatConfig>({
    model: "",
    temperature: 0.7,
    maxTokens: 2048,
    topP: 1.0,
    frequencyPenalty: 0,
    thinkingMode: false,
    streaming: true,
    systemPrompt: t("config.systemPrompt.defaultValue"),
  });

  // 更新配置
  // 使用 useCallback 确保函数引用稳定，避免子组件不必要的重新渲染
  const handleConfigChange = useCallback((newConfig: Partial<ChatConfig>) => {
    setConfig((prev) => ({ ...prev, ...newConfig }));
  }, []);


  return (
    <div className="flex flex-col h-full">
      <TopBar/>

      {/* 主内容区域：左右布局 */}
      <div className="flex flex-1 overflow-hidden">
        {/* 左侧对话区域 */}
        <div className="flex-1 flex flex-col p-8">
          <div className="bg-card rounded-lg border border-border flex-1 flex flex-col overflow-hidden">
            <Conversation config={config}/>
          </div>
        </div>

        {/* 右侧配置面板 */}
        <ConfigPanel config={config} onConfigChange={handleConfigChange} />
      </div>
    </div>
  );
}
