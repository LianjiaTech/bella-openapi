import {ApikeyInfo, CreateSubApikeyRequest, Page, UpdateSubApikeyRequest} from "@/lib/types/openapi";
import { openapi } from '@/lib/api/openapi';
import { ApiKeyBalance } from "@/lib/types/openapi";

export function getSafetyLevel(level: number) : string {
    switch (level) {
        case 10:
            return "极低";
        case 20:
            return "低";
        case 30:
            return "中";
        case 40:
            return "高";
        default:
            return "N/A";
    }
}

export async function getApikeyInfos(page: number, ownerCode: number | null, search: string | null, parentCode?: string): Promise<Page<ApikeyInfo> | null> {
    try {
        const response = await openapi.get<Page<ApikeyInfo>>(`/console/apikey/page`, {
            params: { status: 'active', ownerType:'person', ownerCode: ownerCode, searchParam: search, page, parentCode: parentCode, includeChild: !!parentCode }
        });
        return response.data;
    } catch (error) {
        console.error('Error fetching api:', error);
        throw error;
    }
}

export async function applyApikey(ownerCode: string, ownerName: string): Promise<string> {
    const response = await openapi.post<string>(`/console/apikey/apply`,
        {ownerType:'person', ownerCode: ownerCode, ownerName: ownerName, monthQuota: 50});
    return response.data;
}

export async function deleteApikey(code: string): Promise<boolean> {
    const response = await openapi.post<boolean>(`/console/apikey/inactivate`, { code });
    return response.data ?? false;
}

export async function resetApikey(code: string): Promise<string | null> {
    const response = await openapi.post<string>(`/console/apikey/reset`, { code });
    return response.data || null;
}

export async function updateCertify(code: string, certifyCode: string): Promise<boolean> {
    const response = await openapi.post<boolean>('/console/apikey/certify', { code, certifyCode });
    return response.data ?? false;
}

export async function updateQuota(code: string, monthQuota: number): Promise<boolean> {
    const response = await openapi.post<boolean>('/console/apikey/quota/update', { code, monthQuota });
    return response.data ?? false;
}

export async function rename(code: string, name: string): Promise<boolean> {
    const response = await openapi.post<boolean>('/console/apikey/rename', { code, name });
    return response.data ?? false;
}

export async function getApikeyByCode(code: string): Promise<ApikeyInfo | null> {
    try {
        const response = await openapi.get<ApikeyInfo>(`/console/apikey/fetchByCode`, {
            params: { code, onlyActive: true }
        });
        return response.data;
    } catch (error) {
        console.error('Error fetching apikey by code:', error);
        throw error;
    }
}

export async function createSubApikey(request: CreateSubApikeyRequest): Promise<string | null> {
    try {
        const response = await openapi.post<string>(`/v1/apikey/create`, request);
        return response.data;
    } catch (error) {
        console.error('Error creating sub apikey:', error);
        throw error;
    }
}

export async function updateSubApikey(request: UpdateSubApikeyRequest): Promise<boolean | null> {
    try {
        const response = await openapi.post<boolean>(`/v1/apikey/update`, request);
        return response.data;
    } catch (error) {
        console.error('Error update sub apikey:', error);
        throw error;
    }
}

export async function getApiKeyBalance(akCode: string): Promise<ApiKeyBalance | null> {
    try {
        const response = await openapi.get<ApiKeyBalance>(`/console/apikey/balance/${akCode}`);
        return response.data;
    } catch (error) {
        console.error('Error fetching api key balance:', error);
        throw error;
    }
}
