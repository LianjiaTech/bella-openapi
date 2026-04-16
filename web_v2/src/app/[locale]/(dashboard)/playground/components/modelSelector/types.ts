import { Model } from "@/lib/types/openapi";

/**
 * 模型选择器 Props
 */
export interface ModelSelectorProps {
  /** 当前选中的模型名称 */
  value: string;
  /** 模型名称变更回调 */
  onValueChange: (modelName: string) => void;
  /** 完整 Model 对象变更回调，调用方按需使用 */
  onModelChange?: (model: Model | undefined) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** API 端点路径，用于获取对应的模型列表 */
  endpoint?: string;
}
