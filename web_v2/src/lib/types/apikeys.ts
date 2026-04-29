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
    // 管理人信息（可选，后端返回）
    managerCode?: string;
    managerName?: string;
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

export interface ApikeyChangeLog {
    id: number;
    actionType: 'owner_transfer' | 'owner_change' | 'parent_change' | 'manager_change';
    akCode: string;
    affectedCodes: string;
    fromOwnerType: string;
    fromOwnerCode: string;
    fromOwnerName: string;
    toOwnerType: string;
    toOwnerCode: string;
    toOwnerName: string;
    fromParentCode: string;
    toParentCode: string;
    fromManagerCode: string;
    fromManagerName: string;
    toManagerCode: string;
    toManagerName: string;
    reason: string;
    status: string;
    operatorUid: number;
    operatorName: string;
    ctime: string;
    mtime: string;
}

export interface ChangeApiKeyOwnerRequest {
    code: string;
    targetOwnerType: 'org' | 'project';
    targetOwnerCode?: string;
    targetOwnerName?: string;
    reason?: string;
}

export interface ChangeApiKeyParentRequest {
    code: string;
    targetParentCode: string;
    reason?: string;
}

export interface ChangeApiKeyResult {
    code: string;
    action: 'owner_change' | 'parent_change';
    affectedCount: number;
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
    monthQuota?: number;
    name?: string;
    outEntityCode?: string;
    remark?: string;
    roleCode?: string;
    safetyLevel?: number;
}

// 更新安全等级的请求参数
export interface UpdateSafeLevelRequest {
    certifyCode: string;
    code: string;
}

// 设置管理人的请求参数
export interface UpdateManagerRequest {
    code: string;        // AK code，必填
    managerUserId: number; // 管理人用户 ID，后端自动推导 managerCode
    reason?: string;     // 管理者变更原因，用于审计历史
    syncChildren?: boolean; // 是否同步更新子 AK 管理者，未传时后端保持默认同步
}
