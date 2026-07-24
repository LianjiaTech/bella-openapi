"use client";

import { Combobox, ComboboxOption } from "@/components/ui/combobox";
import { ModelSelectorProps } from "./types";
import { useModelList } from "../../hooks/useModelList";
import { useMemo, useEffect, useRef } from "react";
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

  // 使用 ref 追踪是否已经初始化过，避免重复调用
  const initializedRef = useRef(false);

  // 初始化选中模型：URL 参数优先，否则使用第一个模型
  // 职责：统一处理模型初始化逻辑，避免多个 effect 相互触发造成 re-render
  // 优先级：URL 参数 > 默认第一个模型
  //
  // 避免无限循环策略：
  // 1. 使用 ref 标记是否已初始化，确保只执行一次
  // 2. 移除 onValueChange 依赖，避免父组件重新渲染时触发循环
  useEffect(() => {
    if (models.length === 0 || initializedRef.current) return;

    const modelFromUrl = searchParams.get("model");

    // 如果 URL 有指定模型，使用 URL 的值
    if (modelFromUrl) {
      onValueChange(modelFromUrl);
      initializedRef.current = true;
      return;
    }

    // 如果没有选中模型，使用第一个模型作为默认值
    if (!value) {
      onValueChange(models[0].modelName);
      initializedRef.current = true;
    }
  }, [models, searchParams, value]); // 移除 onValueChange 依赖

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
        onValueChange={onValueChange}
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
