import { apiClient } from '@/lib/api/client';
import {  Page } from '../types/openapi';
import { ApikeyInfo, ApiKeyBalance,UserSearchResult, CreateSubApiKeyRequest, UpdateSubApiKeyRequest, TransferApikeyRequest } from '../types/apikeys';

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

export async function searchUserInfo(searchParam: string): Promise<UserSearchResult[]> {
    const response = await apiClient.get('/v1/userInfo/search', {
        params: { keyword: searchParam, limit: 20, excludeSelf: true }
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