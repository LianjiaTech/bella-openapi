/**
 * 日志查询相关类型定义
 */

/**
 * 日志指标信息
 */
export interface LogMetrics {
  /** 总延迟时间 */
  ttlt: number;
  /** token数量 */
  token: number;
}

/**
 * 日志请求数据
 */
export interface LogRequestData {
  /** 用户ID */
  user: string;
  /** 模型名称 */
  model: string;
  /** 输入内容数组 */
  input: string[];
  /** 编码格式 */
  encoding_format: string;
}

/**
 * Embedding响应数据项
 */
export interface EmbeddingItem {
  /** 对象类型 */
  object: string;
  /** base64编码的向量数据 */
  embedding: string;
  /** 索引位置 */
  index: number;

}

/**
 * 日志响应数据
 */
export interface LogResponseData {
  /** 对象类型 */
  object: string;
  /** embedding数据列表 */
  data: EmbeddingItem[];
  /** 使用情况 */
  usage?: {
    prompt_tokens: number;
    total_tokens: number;
  };
  /** 模型名称 */
  model?: string;
}

/**
 * 单条日志记录
 */
export interface LogEntry {
  /** AK Code */
  data_info_msg_akCode?: string;
  /** 能力点 */
  data_info_msg_endpoint?: string;
  /** 指标信息 - JSON字符串 */
  data_info_msg_metrics: string;
  /** 请求ID */
  data_info_msg_requestId: string;
  /** 转发URL */
  data_info_msg_forwardUrl: string;
  /** Bella跟踪ID */
  data_info_msg_bellaTraceId: string;
  /** 请求内容 - JSON字符串 */
  data_info_msg_request: string;
  /** 用户ID */
  data_info_msg_user: string;
  /** 请求时间 - Unix时间戳(秒) */
  data_info_msg_requestTime: number;
  /** 响应内容 - JSON字符串 */
  data_info_msg_response: string;
  data_info_msg_model: string;
  data_info_msg_runId?: string;
  data_info_msg_threadId?: string;
  data_info_msg_userId?: string;
  data_info_msg_accountCode?: string;
  data_info_msg_channelCode?: string;
  data_info_msg_usage?: string;
}

/**
 * 日志查询API响应
 */
export interface LogsApiResponse {
  /** 总记录数 */
  total: number;
  /** HTTP状态码 */
  status: number;
  /** 日志数据列表 */
  data: LogEntry[];
}

/**
 * 解析后的日志记录(包含已解析的JSON字段)
 */
export interface ParsedLogEntry extends LogEntry {
  /** 解析后的指标信息 */
  metrics: LogMetrics;
  /** 解析后的请求数据 */
  request: LogRequestData;
  /** 解析后的响应数据 */
  response: LogResponseData;
  /** 格式化后的请求时间 */
  requestTime: Date;
}

export interface AssistantInfo {
  data_info_msg_totalSteps: number
  data_info_msg_accountType: string
  data_info_msg_accountCode: string
  data_info_msg_assistantId: string
  data_info_msg_akCode: string
  data_info_msg_akSha: string
  data_info_msg_toolFiles: string // JSON 字符串
  data_info_msg_threadId: string
  data_info_msg_duration: string
  data_info_msg_requestId: string
  data_info_msg_bellaTraceId: string
  data_info_msg_tools: string // JSON 字符串
  data_info_msg_user: string
  data_info_msg_runId: string
  data_info_msg_assistantMessageId: string
  data_info_msg_fileInfos: string // JSON 字符串
  data_info_msg_requestTime: string
  data_info_msg_model: string
  data_info_msg_parentAkCode: string
}

export interface KeRagInfo {
 
  data_cost: number;
  data_errorCode: string;
  data_info_msg_bellaTraceId: string;
  data_loglevel: string;
  data_params: string;
  data_step: string;
}
