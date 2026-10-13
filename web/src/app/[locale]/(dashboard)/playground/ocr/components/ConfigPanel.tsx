"use client";

import { useMemo } from "react";
import { Label } from "@/components/common/label";
import { Separator } from "@/components/common/separator";
import { Combobox, ComboboxOption } from "@/components/ui/combobox";
import { OcrModelSelector } from "./OcrModelSelector";
import { ConfigPanelProps, OCR_TYPES } from "../types";

/**
 * OCR 配置面板（右侧）
 * 包含：识别类型选择 + 模型选择
 */
export function ConfigPanel({ config, onConfigChange }: ConfigPanelProps) {
  const typeOptions: ComboboxOption[] = useMemo(
    () => OCR_TYPES.map((t) => ({ value: t.value, label: t.label })),
    []
  );

  const handleTypeChange = (ocrType: string) => {
    // 切换类型时同步清空模型，避免上一类型的模型残留
    onConfigChange({ ocrType, model: "" });
  };

  const selectedType = OCR_TYPES.find((t) => t.value === config.ocrType);

  return (
    <div className="w-80 border-l border-border bg-card flex flex-col h-full">
      {/* 面板标题 */}
      <div className="p-4 border-b border-border">
        <h2 className="text-lg font-semibold">配置参数</h2>
        <p className="text-sm text-muted-foreground mt-1">
          选择识别类型和对应模型
        </p>
      </div>

      {/* 内容区 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* 识别类型 */}
        <section className="space-y-2">
          <Label className="font-medium block">识别类型</Label>
          <Combobox
            options={typeOptions}
            value={config.ocrType}
            onValueChange={handleTypeChange}
            placeholder="选择识别类型"
            searchPlaceholder="搜索类型..."
            emptyText="未找到匹配类型"
            className="w-full"
          />
          {selectedType && (
            <p className="text-xs text-muted-foreground">
              端点: {selectedType.value}
            </p>
          )}
        </section>

        <Separator />

        {/* 模型选择 */}
        <section className="space-y-2">
          <Label className="font-medium block">模型选择</Label>
          <OcrModelSelector
            ocrType={config.ocrType}
            value={config.model}
            onValueChange={(model) => onConfigChange({ model })}
          />
        </section>
      </div>
    </div>
  );
}
