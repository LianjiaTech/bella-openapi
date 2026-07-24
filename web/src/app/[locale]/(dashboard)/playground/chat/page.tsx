"use client";

import { useState, useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { TopBar } from "@/components/layout";
import { ConfigPanel } from "./components/configPanel";
import { Conversation } from "./components/conversation";
import { ChatConfig, ModelFeatures } from "./types";
import { MessageSquare } from "lucide-react";
import type { Model } from "@/lib/types/openapi";

/**
 * 职责：聊天 Playground 页面
 * - 从 URL 查询参数中读取 model 值（如 playground/chat?model=testModel99）
 * - 管理聊天配置状态并传递给子组件
 * - 协调配置面板和对话区域的交互
 * - 管理当前选中模型的能力特性（vision, video）
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

  // 当前选中模型的完整对象
  const [selectedModel, setSelectedModel] = useState<Model | undefined>(undefined);

  // 更新配置
  // 使用 useCallback 确保函数引用稳定，避免子组件不必要的重新渲染
  const handleConfigChange = useCallback((newConfig: Partial<ChatConfig>) => {
    setConfig((prev) => ({ ...prev, ...newConfig }));
  }, []);

  /**
   * 处理模型对象变更
   * 职责：接收 ModelSelector 传递的完整模型对象，更新状态
   */
  const handleModelObjectChange = useCallback((model: Model | undefined) => {
    setSelectedModel(model);
  }, []);

  /**
   * 解析模型的 features 字段
   * 职责：将 features 字符串解析为 ModelFeatures 对象
   * 性能优化：使用 useMemo 缓存解析结果，只在 selectedModel 变化时重新计算
   */
  const modelFeatures = useMemo<ModelFeatures>(() => {
    if (!selectedModel?.features) {
      return { vision: false, video: false };
    }

    try {
      const parsed = JSON.parse(selectedModel.features);
      return {
        vision: parsed.vision === true,
        video: parsed.video === true,
      };
    } catch (error) {
      console.warn('Failed to parse model features:', error);
      return { vision: false, video: false };
    }
  }, [selectedModel]);


  return (
    <div className="flex flex-col h-full">
      <TopBar title={t("title")} description={t("description")} />

      {/* 主内容区域：左右布局 */}
      <div className="flex flex-1 overflow-hidden">
        {/* 左侧对话区域：mx-auto + max-w-4xl 实现居中留白，与向量化页面视觉比例一致 */}
        <div className="flex-1 overflow-hidden p-6">
          <div className="mx-auto max-w-6xl h-full bg-card rounded-lg border border-border flex flex-col overflow-hidden">
            {/* 小框标题栏：图标 + 标题，与向量化页面 Card 头部风格对齐，无分割线保持整框感 */}
            <div className="flex items-center gap-2 px-6 pt-5 pb-1 flex-shrink-0">
              <MessageSquare className="h-5 w-5 text-primary" />
              <h3 className="font-semibold">智能问答</h3>
            </div>
            <div className="flex-1 overflow-hidden">
              <Conversation config={config} modelFeatures={modelFeatures} />
            </div>
          </div>
        </div>

        {/* 右侧配置面板 */}
        <ConfigPanel
          config={config}
          onConfigChange={handleConfigChange}
          onModelObjectChange={handleModelObjectChange}
        />
      </div>
    </div>
  );
}
