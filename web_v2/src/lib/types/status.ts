/**
 * 状态监控相关的类型定义
 */

/**
 * 渠道信息接口
 */
export interface ChannelInfo {
  channelCode: string;
  channel_code: string;
  endpoint?: string;
  entity_code?: string;
  metrics?: MetricsData;
  [key: string]: any; // 支持其他可能的字段
}

/**
 * 指标数据接口
 */
export interface MetricsData {
  status: 0 | 1;                // 状态：0=不可用, 1=可用
  completed: number;            // 完成请求数
  errors: number;               // 错误数
  request_too_many: number;     // 429限流数
  ttft?: number;                // 首token耗时 (chat completions)
  ttlt?: number;                // 末token耗时
  input_token?: number;         // 输入token数
  output_token?: number;        // 输出token数
  token?: number;               // token数 (embeddings)
  duration?: number;            // 音频时长 (speaker embedding)
}

/**
 * 单个渠道在某时间点的数据
 */
export interface ChannelMetricsAtTime {
  time: string;
  endpoint: string;
  entity_code: string;
  channel_code: string;
  channelCode: string;
  metrics: MetricsData;
}

/**
 * 某个时间点的汇总数据（包含所有渠道）
 */
export interface MetricsAtTime {
  time: string;
  endpoint: string;
  entity_code?: string;
  channel_code: string;
  metrics: MetricsData;  // 汇总的指标
  [key: number]: ChannelMetricsAtTime;  // 各个渠道的数据（数字索引）
}

/**
 * 指标查询结果接口
 */
export interface MetricsQueryResult {
  channelCode: string;          // 渠道代码
  entityCode: string;           // 实体代码（模型名或端点名）
  endpoint: string;             // 端点路径
  metrics: MetricsData;         // 指标数据
}

/**
 * 汇总数据接口
 */
export interface SummaryData {
  totalRequests: number;        // 总请求数
  totalRequestTooMany: number;  // 总限流次数
  totalErrors: number;          // 总错误次数
  avgTtft: number;              // 平均首token耗时
}

/**
 * 图表数据点接口
 */
export interface ChartDataPoint {
  time: string;                 // 时间(格式化后, 如 "17:59")
  completed: number;            // 完成数
  request_too_many: number;     // 限流数
  errors: number;               // 错误数
  input_token: number;          // 输入token数
  output_token: number;         // 输出token数
  token: number;                // token总数
  ttft: number;                 // 首token耗时
  ttlt: number;                 // 末token耗时
}

/**
 * useChannels Hook 参数接口
 */
export interface UseChannelsParams {
  /** 模型名称 */
  model?: string;
  /** 端点类型 */
  endpoint?: string;
  /** 渠道代码 */
  channelCode?: string;
  /** 开始时间 (YYYYMMDDHHmm 格式) */
  start?: string;
  /** 结束时间 (YYYYMMDDHHmm 格式) */
  end?: string;
}

/**
 * useChannels Hook 返回值接口
 */
export interface UseChannelsResult {
  /** 渠道列表数据 */
  channels: ChannelInfo[];
  /** 是否正在加载 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
  /** 汇总数据 */
  summary: SummaryData;
  /** 图表数据 */
  chartData?: ChartDataPoint[];
  /** 原始指标数据 */
  metricsData: MetricsAtTime[];
}

/**
 * 筛选条件接口
 */
export interface FilterValues {
  dateRange?: any;              // 日期范围 (使用 react-day-picker 的 DateRange)
  modelName?: string;           // 模型名称
  endpoint?: string;            // 端点类型
  channelCode?: string;         // 渠道代码
}

/**
 * 时间间隔类型
 */
export type TimeInterval = '5' | '15' | '30';
