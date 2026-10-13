"use client";

import { Slider } from "@/components/common/slider";
import { useTranslations } from "next-intl";

interface MaxTokensSliderProps {
  value: number;
  onChange: (value: number) => void;
}

/**
 * Max Tokens 滑块组件
 * 用于控制 AI 模型生成的最大长度
 * 范围: 0-4096, 步进: 256
 */
export function MaxTokensSlider({
  value,
  onChange,
}: MaxTokensSliderProps) {
  const t = useTranslations("playground.chat");

  const handleValueChange = (values: number[]) => {
    onChange(values[0]);
  };

  return (
    <div className="space-y-3">
      {/* 标题和当前值 */}
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">{t("maxTokens")}</span>
        <span className="text-sm font-medium text-primary">{value}</span>
      </div>

      {/* 滑块控件 */}
      <Slider
        value={[value]}
        onValueChange={handleValueChange}
        min={0}
        max={4096}
        step={256}
        className="w-full"
      />

      {/* 说明文字 */}
      <p className="text-xs text-muted-foreground">
        {t("maxTokensDescription")}
      </p>
    </div>
  );
}
