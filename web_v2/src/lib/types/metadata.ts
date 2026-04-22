/**
 * Metadata 模块专用类型定义
 * 仅包含 metadata 模块内部使用的类型
 * 与其他模块共用的类型请定义在 openapi.ts 中
 */

/**
 * 分类基础信息
 */
export interface Category {
    id: number;
    categoryCode: string;
    categoryName: string;
    parentCode: string;
    status: string;
    cuid: number;
    cuName: string;
    muid: number;
    muName: string;
    ctime: string;
    mtime: string;
}

/**
 * 端点列表响应
 */
export interface ListEndpointsResponse {
    costScript: string;
    ctime: string;
    cuName: string;
    cuid: number;
    documentUrl: string;
    endpoint: string;
    endpointCode: string;
    endpointName: string;
    id: number;
    maintainerCode: string;
    maintainerName: string;
    mtime: string;
    muName: string;
    muid: number;
    status: string;
}

/**
 * 类型模式定义
 */
export interface TypeSchema {
    code: string;
    name: string;
    valueType: string;
    selections: string[];
    child?: JsonSchema | null;
    description?: string;
}

/**
 * JSON 参数模式
 */
export interface JsonSchema {
    params: TypeSchema[];
}

/**
 * 监控数据
 */
export interface MonitorData {
    time: string;
    channel_code: string;
    metrics: Record<string, number>;
}

/**
 * 语音属性配置
 */
export interface VoiceProperties {
    voiceTypes: Record<string, string>;
}
export interface ChannelDetails {
    cuid: number;
    cuName: string;
    muid: number;
    muName: string;
    ctime: string | null;
    mtime: string | null;
    channelCode: string;
    channelInfo: string;
    dataDestination: string;
    entityCode: string;
    entityType: string;
    priceInfo: string;
    priority: string;
    protocol: string;
    queueMode: number;
    queueName: string;
    status: string;
    supplier: string;
    trialEnabled: number;
    url: string;
}

// Channel 类型用于创建和更新操作
export interface Channel {
    channelCode?: string;
    entityType?: string;
    entityCode?: string;
    url?: string;
    protocol?: string;
    supplier?: string;
    dataDestination?: string;
    channelInfo?: string;
    priceInfo?: string;
    priority?: string;
    trialEnabled?: number;
    queueMode?: number;
    queueName?: string;
    ownerType?: string;
    ownerCode?: string;
    ownerName?: string;
    visibility?: string;
    status?: string;
}