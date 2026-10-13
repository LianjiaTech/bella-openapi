/**
 * 模型能力特性
 */
export interface ModelFeatures {
  /** 是否支持图片输入 */
  vision?: boolean;
  /** 是否支持视频输入 */
  video?: boolean;
}

/**
 * 聊天配置参数类型定义
 */
export interface ChatConfig {
  /** 选择的模型 */
  model: string;
  /** 温度参数 (0-2) */
  temperature: number;
  /** 最大输出 Token 数 */
  maxTokens: number;
  /** Top P 采样参数 (0-1) */
  topP: number;
  /** 频率惩罚 (-2 to 2) */
  frequencyPenalty: number;
  /** 是否启用思考模式 */
  thinkingMode: boolean;
  /** 是否启用流式输出 */
  streaming: boolean;
  /** 系统提示词 (可选) */
  systemPrompt?: string;
}

/**
 * 消息角色类型
 */
export type MessageRole = 'user' | 'assistant' | 'system';

/**
 * 消息类型定义
 */
export interface Message {
  /** 消息唯一标识 */
  id: string;
  /** 消息角色 */
  role: MessageRole;
  /** 消息内容 */
  content: string;
  /** 思考过程(仅 assistant 消息) */
  thinking?: string;
  /** 消息时间戳 */
  timestamp: number;
}

/**
 * 配置面板 Props
 */
export interface ConfigPanelProps {
  /** 当前配置 */
  config: ChatConfig;
  /** 配置更新回调 */
  onConfigChange: (config: Partial<ChatConfig>) => void;
  /** 模型对象变更回调（用于获取模型的完整信息） */
  onModelObjectChange?: (model: import("@/lib/types/openapi").Model | undefined) => void;
}
