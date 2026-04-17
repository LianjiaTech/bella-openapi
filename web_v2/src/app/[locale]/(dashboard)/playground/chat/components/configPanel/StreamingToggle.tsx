"use client";

import { Switch } from "@/components/common/switch";
import { Label } from "@/components/common/label";
import { useTranslations } from "next-intl";

interface StreamingToggleProps {
  value: boolean;
  onChange: (value: boolean) => void;
}

/**
 * 流式输出开关组件
 * 控制是否启用流式响应模式
 */
export function StreamingToggle({ value, onChange }: StreamingToggleProps) {
  const t = useTranslations("playground.chat.config");

  return (
    <div className="flex items-center justify-between">
      <div className="flex-1 space-y-1">
        <Label className="text-sm font-medium leading-none">
          {t("streaming.title")}
        </Label>
        <p className="text-xs text-muted-foreground">
          {t("streaming.description")}
        </p>
      </div>
      <Switch
        checked={value}
        onCheckedChange={onChange}
      />
    </div>
  );
}
