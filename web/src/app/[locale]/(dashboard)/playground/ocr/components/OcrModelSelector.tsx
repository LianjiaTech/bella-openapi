"use client";

import { useMemo, useEffect, useRef } from "react";
import { Combobox, ComboboxOption } from "@/components/ui/combobox";
import { useModelList } from "../../hooks/useModelList";

interface OcrModelSelectorProps {
  ocrType: string;
  value: string;
  onValueChange: (model: string) => void;
}

/**
 * OCR 模型选择器
 * 随 ocrType（endpoint）变化自动刷新模型列表
 */
export function OcrModelSelector({
  ocrType,
  value,
  onValueChange,
}: OcrModelSelectorProps) {
  const { models, loading, error } = useModelList(ocrType);

  // 使用 ref 追踪是否已经初始化过，避免重复调用
  const initializedRef = useRef(false);

  // 自动选择第一个模型（当模型列表加载完成且没有选中模型时）
  // 职责：提供默认模型选择，优化用户体验
  // 避免无限循环策略：使用 ref 标记是否已初始化，确保只执行一次
  useEffect(() => {
    if (models.length === 0 || initializedRef.current) return;

    // 如果没有选中模型且 URL 参数也没有指定模型，使用第一个模型作为默认值
    if (!value) {
      onValueChange(models[0].modelName);
      initializedRef.current = true;
    }
  }, [models, value, onValueChange]);

  // 当 ocrType 变化时，重置初始化标记，允许重新选择第一个模型
  useEffect(() => {
    initializedRef.current = false;
  }, [ocrType]);

  const options: ComboboxOption[] = useMemo(
    () => models.map((m) => ({ value: m.modelName, label: m.modelName })),
    [models]
  );

  const selectedModel = useMemo(
    () => models.find((m) => m.modelName === value),
    [models, value]
  );

  const placeholder = loading
    ? "加载中..."
    : error
    ? "加载失败"
    : models.length === 0
    ? "暂无可用模型"
    : "选择模型";

  const isDisabled = loading || !!error || models.length === 0;

  return (
    <div className="space-y-2">
      <Combobox
        options={options}
        value={value}
        onValueChange={onValueChange}
        placeholder={placeholder}
        searchPlaceholder="搜索模型..."
        emptyText="未找到匹配的模型"
        disabled={isDisabled}
        className="w-full"
      />
      {loading && (
        <p className="text-xs text-muted-foreground">正在获取模型列表...</p>
      )}
      {error && <p className="text-xs text-destructive">{error}</p>}
      {!loading && !error && models.length === 0 && (
        <p className="text-xs text-muted-foreground">当前类型暂无可用模型</p>
      )}
      {value && !loading && !error && selectedModel?.ownerName && (
        <p className="text-xs text-muted-foreground">
          提供方: {selectedModel.ownerName}
        </p>
      )}
    </div>
  );
}
