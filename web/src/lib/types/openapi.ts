export interface BellaResponse<T> {
    code?: number;
    message?: string;
    timestamp?: number;
    data?: T;
    stacktrace?: string;
}


export interface Page<T> {
    data?: T[];
    has_more: boolean;
    page: number;
    limit: number;
    total: number;
}

export interface UserInfo {
    userId: number;
    userName: string;
    image?: string;
    optionalInfo: Record<string, any>
}

export interface ApikeyInfo {
    code: string;
    serviceId: string;
    akSha: string;
    akDisplay: string;
    name: string;
    outEntityCode: string;
    parentCode: string;
    ownerType: string;
    ownerCode: string;
    ownerName: string;
    roleCode: string;
    safetyLevel: number;
    monthQuota: number;
    rolePath?: RolePath;
    status: string;
    remark: string;
    userId: number;
}

export interface RolePath {
    included: string[];
    excluded?: string[];
}


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

export interface CategoryTree {
    categoryCode: string;
    categoryName: string;
    endpoints: Endpoint[] | null;
    children: CategoryTree[] | null;
}

export interface MetadataFeature {
    code: string;
    name: string;
}

export interface Model {
    modelName: string;
    documentUrl: string;
    properties: string | Record<string, any>;
    features: string | string[];
    ownerType: string;
    ownerCode:string;
    ownerName: string;
    visibility: string;
    status: string;
    linkedTo: string;
    endpoints: string[];
    priceDetails: PriceDetails;
    terminalModel: string;
}

export interface Channel {
    entityType: string;
    entityCode: string;
    channelCode: string;
    status: string;
    trialEnabled: number;
    dataDestination: string;
    priority: string;
    protocol: string;
    supplier: string;
    url: string;
    channelInfo: string;
    priceInfo: string;
    ownerType?: string;
    ownerCode?: string;
    ownerName?: string;
    visibility?: string;
    queueMode?: number;
    queueName?: string;
}

export interface ModelDetails {
    model: Model;
    channels: Channel[];
}

export interface EndpointDetails {
    endpoint: string;
    models: Model[];
    features: MetadataFeature[];
    priceDetails?: PriceDetails;
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

export interface TypeSchema {
    code: string;
    name: string;
    valueType: string;
    selections: string[]
    child?: JsonSchema;
}

export interface JsonSchema {
    params: TypeSchema[];
}

export interface MonitorData {
    time: string;
    channel_code: string;
    metrics: Record<string, number>;
}

export interface VoiceProperties {
    voiceTypes: Record<string, string>;
}

export interface ApiKeyBalance {
    akCode: string;
    month: string;
    cost: number;
    quota: number;
    balance: number;
}

export interface CreateSubApikeyRequest {
    parentCode: string;
    name: string;
    outEntityCode: string;
    safetyLevel: number;
    monthQuota: number;
    remark: string;
    roleCode: string;
}


export interface UpdateSubApikeyRequest {
    code: string;
    name: string;
    outEntityCode: string;
    safetyLevel: number;
    monthQuota: number;
    remark: string;
    roleCode: string;
}

// 用户搜索相关类型
export interface UserSearchResult {
    id: number;
    userName: string;
    email: string;
    source: string;
    sourceId: string;
}

// API Key转交相关类型
export interface TransferApikeyRequest {
    akCode: string;
    targetUserId?: number;
    targetUserSource?: string;
    targetUserSourceId?: string;
    targetUserEmail?: string;
    transferReason: string;
}

export interface ApikeyTransferLog {
    id: number;
    akCode: string;
    fromOwnerType: string;
    fromOwnerCode: string;
    fromOwnerName: string;
    toOwnerType: string;
    toOwnerCode: string;
    toOwnerName: string;
    transferReason: string;
    status: string;
    operatorUid: number;
    operatorName: string;
    ctime: string;
    mtime: string;
}
