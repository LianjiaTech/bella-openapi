/**
 * Chat Completions 请求消息
 */
export interface ChatCompletionsMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

/**
 * Chat Completions 请求参数
 */
export interface ChatCompletionsRequest {
  model: string;
  messages: ChatCompletionsMessage[];
  stream?: boolean;
  temperature?: number;
  top_p?: number;
  max_tokens?: number;
  [key: string]: unknown;
}

/**
 * Chat Completions 响应中的 Delta 对象
 */
export interface ChatCompletionsDelta {
  role?: string;
  content?: string;
  reasoning_content?: string;
}

/**
 * Chat Completions 响应中的 Choice 对象
 */
export interface ChatCompletionsChoice {
  index: number;
  delta?: ChatCompletionsDelta;
  finish_reason?: string | null;
}

/**
 * Chat Completions 响应中的 Usage 对象
 */
export interface ChatCompletionsUsage {
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
}

/**
 * Chat Completions 响应
 */
export interface ChatCompletionsResponse {
  id?: string;
  object?: string;
  created?: number;
  model?: string;
  choices: ChatCompletionsChoice[];
  usage?: ChatCompletionsUsage;
}

/**
 * 处理器配置
 */
export interface ChatCompletionsConfig {
  url: string;
  headers?: Record<string, string>;
  timeoutMs?: number;
}

/**
 * 处理器状态
 */
export enum ChatCompletionsState {
  IDLE = 'idle',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  RESPONDING = 'responding',
  FINISHED = 'finished',
  ERROR = 'error',
  CANCELLED = 'cancelled'
}

/**
 * 事件类型
 */
export enum ChatCompletionsEventType {
  STATE_CHANGE = 'state_change',
  START = 'start',
  DELTA = 'delta',
  FINISH = 'finish',
  ERROR = 'error'
}

/**
 * 状态变更事件数据
 */
export interface StateChangeEventData {
  oldState: ChatCompletionsState;
  newState: ChatCompletionsState;
}

/**
 * 开始事件数据
 */
export interface StartEventData {
  // 可扩展
}

/**
 * Delta 事件数据
 */
export interface DeltaEventData {
  content?: string;
  reasoning_content?: string;
  role: string;
  isReasoningContent?: boolean;
  response: ChatCompletionsResponse;
}

/**
 * 完成事件数据
 */
export interface FinishEventData {
  reason?: string;
  usage?: ChatCompletionsUsage | null;
  cancelled?: boolean;
}

/**
 * 事件监听器类型映射
 */
export interface ChatCompletionsEventMap {
  [ChatCompletionsEventType.STATE_CHANGE]: (data: StateChangeEventData) => void;
  [ChatCompletionsEventType.START]: (data: StartEventData) => void;
  [ChatCompletionsEventType.DELTA]: (data: DeltaEventData) => void;
  [ChatCompletionsEventType.FINISH]: (data: FinishEventData) => void;
  [ChatCompletionsEventType.ERROR]: (error: string) => void;
}
