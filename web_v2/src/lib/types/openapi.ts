export interface ModelProperties {
    max_input_context?: number;
    max_output_context?: number;
    // 其他模型属性可以根据实际需求扩展
}

export interface Model {
    modelName: string;
    description?: string;
    documentUrl: string;
    properties: string;
    features: string;
    ownerType: string;
    ownerCode:string;
    ownerName: string;
    visibility: string;
    status: string;
    linkedTo: string;
    endpoints: string[];
    priceDetails?: PriceDetails;
    terminalModel?: string;
}
export interface MetadataFeature {
    code: string;
    name: string;
}
export interface PriceDetails {
    priceInfo?: {
        input: number;
        output: number;
        cachedRead?: number;
        cachedCreation?: number;
        unit: string;
    };
    displayPrice: Record<string, string>;
    unit: string;
}
export interface EndpointDetails {
    endpoint: string;
    models: Model[];
    features: MetadataFeature[];
    priceDetails: PriceDetails;
}
export interface Model {}

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

export interface ModelDetails {
    model: Model;
    channels: ChannelDetails[];
}
export interface ChannelDetails {
}
export interface Endpoint {
    endpoint: string;
    endpointCode: string;
    endpointName: string;
    maintainerCode: string;
    maintainerName: string;
    status: string;
    cuid: number;
    cuName: string;
    muid: number;
    muName: string;
    ctime: string;
    mtime: string;
    documentUrl?: string;
}