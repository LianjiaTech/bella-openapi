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
    selections: string[]
    child?: JsonSchema;
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