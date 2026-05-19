import { ChannelDetails } from "./metadata";

export type ImageParameterOption = string | {
    value: string;
    label?: string;
};

export interface ImageParameterProperties {
    enabled?: boolean;
    default?: string;
    options?: ImageParameterOption[];
}

export interface ImageEndpointProperties {
    parameters?: {
        size?: ImageParameterProperties;
        quality?: ImageParameterProperties;
        style?: ImageParameterProperties;
    };
}

export interface ModelProperties {
    max_input_context?: number;
    max_output_context?: number;
    image?: {
        generations?: ImageEndpointProperties;
        edits?: ImageEndpointProperties;
    };
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

    ctime?: string;             // 创建时间，格式 "YYYY-MM-DD HH:mm:ss"
    cuName?: string;            // 创建人名称
    cuid?: number;              // 创建人ID

    mtime?: string;             // 修改时间，格式 "YYYY-MM-DD HH:mm:ss"
    muName?: string;            // 修改人名称
    muid?: number;              // 修改人ID
   
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

export interface ModelDetails {
    model: Model;
    channels: ChannelDetails[];
}

export interface Page<T> {
    data?: T[];
    has_more: boolean;
    page: number;
    limit: number;
    total: number;
}