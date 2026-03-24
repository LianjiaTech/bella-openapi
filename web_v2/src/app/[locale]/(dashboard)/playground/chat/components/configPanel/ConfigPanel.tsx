"use client";

import type { ConfigPanelProps } from "../../types";
import { Label } from "@/components/common/label";
import { Separator } from "@/components/common/separator";
import { ModelSelector, TemperatureSlider } from "../../../components";
import { MaxTokensSlider } from "./MaxTokensSlider";
import { ThinkingModeToggle } from "./ThinkingModeToggle";
import { StreamingToggle } from "./StreamingToggle";
import { SystemPromptTextarea } from "./SystemPromptTextarea";
import { useTranslations } from "next-intl";
import { useCallback } from "react";

/**
 * 配置面板组件
 * 右侧参数配置区域,包含模型选择、参数调节和开关控制
 */
export function ConfigPanel({ config, onConfigChange }: ConfigPanelProps) {
  const t = useTranslations("playground.chat.configPanel");

  // 使用 useCallback 创建稳定的模型变更回调，避免每次渲染都创建新函数
  const handleModelChange = useCallback((model: string) => {
    onConfigChange({ model });
  }, [onConfigChange]);
  return (
    <div className="w-80 border-l border-border bg-card flex flex-col h-full">
      {/* 面板标题 */}
      <div className="p-4 border-b border-border">
        <h2 className="text-lg font-semibold">{t("title")}</h2>
        <p className="text-sm text-muted-foreground mt-1">
          {t("description")}
        </p>
      </div>

      {/* 滚动内容区域 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* 模型选择区域 */}
        <section>
          <Label className="font-medium mb-3 block">{t("modelSelection")}</Label>
          <ModelSelector
            value={config.model}
            onValueChange={handleModelChange}
          />
        </section>

        <Separator />

        {/* 参数控制区域 */}
        <section>
          <Label className="font-medium mb-3 block">{t("parameterSettings")}</Label>
          <div className="space-y-4">
            {/* Temperature 滑块 */}
            <div className="bg-muted/30 rounded-md p-4 border border-border">
              <TemperatureSlider
                value={config.temperature}
                onChange={(temperature) => onConfigChange({ temperature })}
              />
            </div>

            {/* Max Tokens 滑块 */}
            <div className="bg-muted/30 rounded-md p-4 border border-border">
              <MaxTokensSlider
                value={config.maxTokens}
                onChange={(maxTokens) => onConfigChange({ maxTokens })}
              />
            </div>
          </div>
        </section>

        <Separator />

        {/* 开关控制区域 */}
        <section>
          <Label className="font-medium mb-3 block">{t("advancedOptions")}</Label>
          <div className="space-y-4">
            {/* System Prompt Textarea */}
            <div className="bg-muted/30 rounded-md p-4 border border-border">
              <SystemPromptTextarea
                value={config.systemPrompt}
                onChange={(systemPrompt) => onConfigChange({ systemPrompt })}
              />
            </div>

            {/* Thinking Mode Toggle */}
            <div className="bg-muted/30 rounded-md p-4 border border-border">
              <ThinkingModeToggle
                value={config.thinkingMode}
                onChange={(thinkingMode) => onConfigChange({ thinkingMode })}
              />
            </div>

            {/* Streaming Toggle */}
            <div className="bg-muted/30 rounded-md p-4 border border-border">
              <StreamingToggle
                value={config.streaming}
                onChange={(streaming) => onConfigChange({ streaming })}
              />
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
