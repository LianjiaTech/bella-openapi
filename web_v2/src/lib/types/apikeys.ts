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
    // 余额信息（可选，从 getApiKeyBalance 接口获取后合并）
    balance?: ApiKeyBalance;
}

export interface RolePath {
    included: string[];
    excluded?: string[];
}
export interface ApiKeyBalance {
    akCode: string;
    month: string;
    cost: number;
    quota: number;
    balance: number;
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

// 创建子API Key的请求参数
export interface CreateSubApiKeyRequest {
    monthQuota: number;
    name: string;
    outEntityCode: string;
    parentCode: string;
    remark?: string;
    roleCode: string;
    safetyLevel: number;
}

// 更新子API Key的请求参数
export interface UpdateSubApiKeyRequest {
    code: string;
    monthQuota: number;
    name: string;
    outEntityCode: string;
    remark?: string;
    roleCode: string;
    safetyLevel: number;
}