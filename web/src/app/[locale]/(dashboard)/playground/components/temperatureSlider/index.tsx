"use client";

import { Slider } from "@/components/common/slider";
import { useTranslations } from "next-intl";
import type { TemperatureSliderProps } from "./types";

/**
 * Temperature 滑块组件
 * 用于控制 AI 模型输出的随机性
 * 范围: 0-2, 步进: 0.1
 */
export function TemperatureSlider({
  value,
  onChange,
}: TemperatureSliderProps) {
  const t = useTranslations("playground.chat");

  const handleValueChange = (values: number[]) => {
    onChange(values[0]);
  };

  return (
    <div className="space-y-3">
      {/* 标题和当前值 */}
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">{t("temperature")}</span>
        <span className="text-sm font-medium text-primary">{value.toFixed(1)}</span>
      </div>

      {/* 滑块控件 */}
      <Slider
        value={[value]}
        onValueChange={handleValueChange}
        min={0}
        max={2}
        step={0.1}
        className="w-full"
      />

      {/* 说明文字 */}
      <p className="text-xs text-muted-foreground">
        {t("temperatureDescription")}
      </p>
    </div>
  );
}
