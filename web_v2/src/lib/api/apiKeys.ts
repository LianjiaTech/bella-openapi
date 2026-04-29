import { apiClient } from '@/lib/api/client';
import {  Page } from '../types/openapi';
import { ApikeyInfo, ApiKeyBalance,UserSearchResult, CreateSubApiKeyRequest, UpdateSubApiKeyRequest, TransferApikeyRequest, UpdateManagerRequest, ChangeApiKeyOwnerRequest, ChangeApiKeyParentRequest, ChangeApiKeyResult, ApikeyChangeLog } from '../types/apikeys';

/**
 * 管理员专用查询参数接口
 * 与普通查询的区别：不固定 ownerType='person'，支持按所有者维度搜索
 */
export interface AdminApiKeyQueryParams {
    /** 搜索维度：ak=按名称/服务名，owner=按所有者 */
    searchType: 'ak' | 'owner'
    /** ak 维度的搜索词（searchType='ak' 时生效） */
    searchParam?: string
    /** owner 维度的搜索词（searchType='owner' 时生效） */
    ownerSearch?: string
    /** 所有者类型过滤，传 undefined 返回全部类型 */
    ownerType?: 'person' | 'org' | 'project'
    /** 父级 ak code，用于筛选子 ak */
    parentCode?: string
    // TODO: managerSearch?: string  — 管理员视角模糊搜索管理人，待后端联调后开放
}

export async function getApiKeys(ownerCode: string, search: string, page: number, parentCode: string): Promise<Page<ApikeyInfo>> {
    // apiClient 拦截器会自动解包 { code, data } 格式，直接返回 data
    const response = await apiClient.get('/console/apikey/page', {
        params: { status: 'active', ownerType:'person', ownerCode: ownerCode, searchParam: search, page, parentCode: parentCode, includeChild: !!parentCode }
    });
    return response as unknown as Page<ApikeyInfo>;
}

export async function getApiKeyBalance(akCode: string): Promise<ApiKeyBalance> {
    const response = await apiClient.get(`/console/apikey/balance/${akCode}`);
    return response as unknown as ApiKeyBalance;
}

export async function applyApiKey(params: {
    ownerCode: string;
    ownerName: string;
}): Promise<string> {
    const response = await apiClient.post<string>('/console/apikey/apply', {
        ownerType: 'person',
        monthQuota: 50,
        ...params
    });
    return (response as unknown as string) || '';
}

/**
 * 管理员创建顶层 AK（支持 org/project 等非个人类型）
 * 对应后端 ApplyOp，managerCode/managerName 由搜索用户后填入
 */
export async function adminApplyApiKey(params: {
    ownerType: string;
    ownerCode: string;
    ownerName: string;
    name?: string;
    monthQuota?: number;
    remark?: string;
    // 临时补丁：managerCode 由前端强制设为 user.id，与 updateManager 存储值保持一致
    // TODO: 待后端 ApplyOp 支持 managerUserId 自动推导后，改为 managerUserId?: number，移除 managerCode/managerName
    managerCode?: string;
    managerName?: string;
}): Promise<string> {
    const response = await apiClient.post<string>('/console/apikey/apply', {
        monthQuota: 50,
        ...params,
    });
    return (response as unknown as string) || '';
}

export async function resetApiKey(akCode: string): Promise<string> {
    const response = await apiClient.post<string>('/console/apikey/reset', {
        code:akCode
    });
    return (response as unknown as string) || '';
}

export async function deleteApiKey(akCode: string): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/inactivate', {
        code: akCode
    });
    return (response as unknown as boolean) || false;
}

export async function searchUserInfo(searchParam: string, excludeSelf = true): Promise<UserSearchResult[]> {
    const response = await apiClient.get('/v1/userInfo/search', {
        params: { keyword: searchParam, limit: 20, excludeSelf }
    });
    return response as unknown as UserSearchResult[];
}
// 创建子密钥
export async function createSubApiKey(params: CreateSubApiKeyRequest): Promise<string> {
    const response = await apiClient.post<string>('/v1/apikey/create', params);
    return (response as unknown as string) || '';
}

// 更新子密钥
export async function updateSubApiKey(params: UpdateSubApiKeyRequest): Promise<boolean> {
    const response = await apiClient.post<boolean>('/v1/apikey/update', params);
    return (response as unknown as boolean) || false;
}
// 重命名 API Key（名称）
export async function renameApiKey(code: string, name: string): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/rename', { code, name });
    return (response as unknown as boolean) || false;
}

// 绑定服务名
export async function bindApiKeyService(code: string, serviceId: string): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/bindService', { code, serviceId });
    return (response as unknown as boolean) || false;
}

// 转交apikey
export async function transferApikey(request: TransferApikeyRequest): Promise<boolean> {
    try {
        const response = await apiClient.post<boolean>('/console/apikey/owner/transfer', request);
        console.log('---response ====', response)
        return response as unknown as boolean;
    } catch (error) {
        console.error('Error transferring apikey:', error);
        throw error;
    }
}

export async function changeApiKeyOwner(request: ChangeApiKeyOwnerRequest): Promise<ChangeApiKeyResult> {
    const response = await apiClient.post<ChangeApiKeyResult>('/console/apikey/owner/change', request);
    return response as unknown as ChangeApiKeyResult;
}

export async function changeApiKeyParent(request: ChangeApiKeyParentRequest): Promise<ChangeApiKeyResult> {
    const response = await apiClient.post<ChangeApiKeyResult>('/console/apikey/parent/change', request);
    return response as unknown as ChangeApiKeyResult;
}

export async function getApiKeyChangeHistory(akCode: string): Promise<ApikeyChangeLog[]> {
    const response = await apiClient.get('/console/apikey/change/history', { params: { akCode } });
    return response as unknown as ApikeyChangeLog[];
}

/**
 * 管理员全量查询接口
 * 与 getApiKeys 的区别：不固定 ownerType='person'，支持 ownerSearch 参数按所有者筛选
 * 不传 ownerCode，让后端返回全量数据
 */
export async function getAdminApiKeys(page: number, params: AdminApiKeyQueryParams): Promise<Page<ApikeyInfo>> {
    const queryParams: Record<string, unknown> = {
        status: 'active',
        page,
        ...(params.parentCode ? { parentCode: params.parentCode, includeChild: true } : {}),
        ...(params.ownerType ? { ownerType: params.ownerType } : {}),
    };
    // ak 维度：searchParam 传名称/服务名关键词
    if (params.searchType === 'ak' && params.searchParam) {
        queryParams.searchParam = params.searchParam;
    }
    // owner 维度：ownerSearch 传所有者名称/ID 关键词
    if (params.searchType === 'owner' && params.ownerSearch) {
        queryParams.ownerSearch = params.ownerSearch;
    }
    const response = await apiClient.get('/console/apikey/page', { params: queryParams });
    return response as unknown as Page<ApikeyInfo>;
}

/**
 * 按 code 精确查询单条 AK 信息（管理员接口，不受 ownerCode 限制）
 */
export async function getApiKeyByCode(code: string): Promise<ApikeyInfo> {
    const response = await apiClient.get('/console/apikey/fetchByCode', { params: { code, onlyActive: false } });
    return response as unknown as ApikeyInfo;
}

// 更新月额度（管理员接口）
export async function updateApiKeyQuota(code: string, monthQuota: number): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/quota/update', { code, monthQuota });
    return (response as unknown as boolean) || false;
}

// 更新子 AK 月额度
export async function updateSubApiKeyQuota(code: string, monthQuota: number): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/sub/quota/update', { code, monthQuota });
    return (response as unknown as boolean) || false;
}

// 更新安全等级
export async function updateSafeLevel(params: { certifyCode: string; code: string }): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/certify', params);
    return (response as unknown as boolean) || false;
}

/**
 * 管理者视角：查询当前用户管理的 AK 列表
 * GET /console/apikey/page?managerCode=userId&status=active&page=n&searchParam=...
 *
 * @param onlyChild - true：onlyChild=true（后端只返回子AK，parent_code != ''，分页计数准确）
 *                    false/undefined：不传，后端默认只返回顶层AK（parent_code=''）
 */
export async function getManagerApiKeys(
    page: number,
    managerCode: string,
    search?: string,
    onlyChild?: boolean,
    excludeOwnerType?: string
): Promise<Page<ApikeyInfo>> {
    const response = await apiClient.get('/console/apikey/page', {
        params: {
            status: 'active',
            managerCode,
            page,
            ...(search ? { searchParam: search } : {}),
            ...(onlyChild ? { onlyChild: true } : {}),
            ...(excludeOwnerType ? { excludeOwnerType } : {}),
        }
    });
    return response as unknown as Page<ApikeyInfo>;
}

/**
 * 设置 AK 的管理人
 * POST /console/apikey/manager/update
 * 只传 code + managerUserId，后端自动关联 managerCode/managerName
 */
export async function updateManager(params: UpdateManagerRequest): Promise<boolean> {
    const response = await apiClient.post<boolean>('/console/apikey/manager/update', params);
    return (response as unknown as boolean) || false;
}
