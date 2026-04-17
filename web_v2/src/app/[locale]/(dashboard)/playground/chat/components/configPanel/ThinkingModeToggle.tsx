"use client";

import { Switch } from "@/components/common/switch";
import { Label } from "@/components/common/label";
import { useTranslations } from "next-intl";

interface ThinkingModeToggleProps {
  value: boolean;
  onChange: (value: boolean) => void;
}

/**
 * 思考模式开关组件
 * 控制是否启用 AI 的深度思考功能
 */
export function ThinkingModeToggle({ value, onChange }: ThinkingModeToggleProps) {
  const t = useTranslations("playground.chat.config");

  return (
    <div className="flex items-center justify-between">
      <div className="flex-1 space-y-1">
        <Label className="text-sm font-medium leading-none">
          {t("thinkingMode.title")}
        </Label>
        <p className="text-xs text-muted-foreground">
          {t("thinkingMode.description")}
        </p>
      </div>
      <Switch
        checked={value}
        onCheckedChange={onChange}
      />
    </div>
  );
}
