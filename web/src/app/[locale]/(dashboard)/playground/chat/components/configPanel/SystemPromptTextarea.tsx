"use client";

import { Textarea } from "@/components/common/textarea";
import { Label } from "@/components/common/label";
import { useTranslations } from "next-intl";

interface SystemPromptTextareaProps {
  value?: string;
  onChange: (value: string) => void;
}

/**
 * 系统提示词输入组件
 * 职责：提供多行文本输入框用于设置 AI 的系统角色和行为规则
 *
 * 设计说明：
 * 1. 使用受控组件模式，状态由父组件管理，避免内部 re-render
 * 2. 通过 onChange 回调传递值变化，保持组件无状态
 * 3. 使用 shadcn/ui Textarea 组件保持 UI 一致性
 *
 * 避免 re-render 策略：
 * - 组件本身不维护状态，完全受控于父组件
 * - onChange 回调由父组件提供，不使用内联函数
 * - Props 类型精确，只接收必要的 value 和 onChange
 */
export function SystemPromptTextarea({ value = "", onChange }: SystemPromptTextareaProps) {
  const t = useTranslations("playground.chat.config");

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-medium leading-none">
          {t("systemPrompt.title")}
        </Label>
        <span className="text-xs text-muted-foreground">
          {value.length} 字符
        </span>
      </div>
      <p className="text-xs text-muted-foreground">
        {t("systemPrompt.description")}
      </p>
      <Textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="min-h-[120px] resize-y"
        rows={5}
      />
    </div>
  );
}
