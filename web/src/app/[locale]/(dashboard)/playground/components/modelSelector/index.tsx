"use client";

import { Combobox, ComboboxOption } from "@/components/ui/combobox";
import { ModelSelectorProps } from "./types";
import { useModelList } from "../../hooks/useModelList";
import { useMemo, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";

/**
 * 模型选择器组件
 * 只负责展示模型列表和触发选择，不关心模型信息如何展示。
 * 调用方通过 onModelChange 获取完整 Model 对象，自行决定展示逻辑。
 */
export function ModelSelector({
  value,
  onValueChange,
  onModelChange,
  disabled = false,
  endpoint,
}: ModelSelectorProps) {
  const { models, loading, error } = useModelList(endpoint);
  const searchParams = useSearchParams();

  const modelNames = useMemo(
    () => new Set(models.map((model) => model.modelName)),
    [models]
  );
  const initializedRef = useRef(false);

  useEffect(() => {
    initializedRef.current = false;
  }, [endpoint]);

  // 初始化或修正选中模型：仅接受当前能力点返回的可用模型。
  useEffect(() => {
    if (models.length === 0) return;

    let nextValue = value;

    if (!initializedRef.current) {
      const modelFromUrl = searchParams.get("model");
      nextValue = modelFromUrl && modelNames.has(modelFromUrl)
        ? modelFromUrl
        : modelNames.has(value)
        ? value
        : models[0].modelName;
      initializedRef.current = true;
    } else if (!modelNames.has(value)) {
      nextValue = models[0].modelName;
    }

    if (nextValue !== value) {
      onValueChange(nextValue);
    }
  }, [modelNames, models, onValueChange, searchParams, value]);

  // 将模型数据转换为 Combobox 需要的格式
  const modelOptions: ComboboxOption[] = useMemo(() => {
    return models.map((model) => ({
      value: model.modelName,
      label: model.modelName,
    }));
  }, [models]);

  // 缓存当前选中的模型对象(避免重复查找)
  const selectedModel = useMemo(() => {
    return models.find((m) => m.modelName === value);
  }, [models, value]);

  const handleValueChange = useCallback((modelName: string) => {
    if (modelNames.has(modelName)) {
      onValueChange(modelName);
    }
  }, [modelNames, onValueChange]);

  // 每当选中模型变化时，回传完整 Model 对象给调用方
  useEffect(() => {
    onModelChange?.(selectedModel);
  }, [selectedModel]); // eslint-disable-line react-hooks/exhaustive-deps
  
  // 根据状态设置占位符和禁用状态
  const placeholder = loading
    ? "加载中..."
    : error
    ? "加载失败"
    : models.length === 0
    ? "暂无可用模型"
    : "选择模型";

  const isDisabled = disabled || loading || !!error || models.length === 0;

  return (
    <div className="space-y-2">
      <Combobox
        options={modelOptions}
        value={value}
        onValueChange={handleValueChange}
        placeholder={placeholder}
        searchPlaceholder="搜索模型..."
        emptyText="未找到匹配的模型"
        disabled={isDisabled}
        className="w-full"
      />

      {/* 状态提示信息 */}
      {loading && (
        <p className="text-xs text-muted-foreground">正在获取模型列表...</p>
      )}
      {error && <p className="text-xs text-destructive">{error}</p>}
      {!loading && !error && models.length === 0 && (
        <p className="text-xs text-muted-foreground">当前没有可用的聊天模型</p>
      )}

      {/* 显示当前选中模型的详细信息 */}
      {value && !loading && !error && selectedModel?.ownerName && (
        <div className="text-xs text-muted-foreground">
          <span>
            提供方: {selectedModel.ownerName}
          </span>
        </div>
      )}
    </div>
  );
}
