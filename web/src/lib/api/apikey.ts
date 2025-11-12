import {ApikeyInfo, CreateSubApikeyRequest, Page, UpdateSubApikeyRequest, TransferApikeyRequest, ApikeyTransferLog} from "@/lib/types/openapi";
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

export async function bindService(code: string, serviceId: string): Promise<boolean> {
    const response = await openapi.post<boolean>('/console/apikey/bindService', { code, serviceId });
    return response.data ?? false;
}

export async function getApiKeyBalance(akCode: string): Promise<ApiKeyBalance | null> {
    try {
        const response = await openapi.get<ApiKeyBalance>(`/console/apikey/balance/${akCode}`);
        return response.data;
    } catch (error) {
        console.error('Error fetching API key balance:', error);
        return null;
    }
}

// QPS limiter admin APIs
export interface QpsRankingItem {
    akCode: string;
    currentQps: number;
    currentRpm: number;
    limitQps: number;
}

export type QpsDetail = QpsRankingItem;

export async function getQpsRanking(top: number = 20): Promise<QpsRankingItem[]> {
    const response = await openapi.get<QpsRankingItem[]>('/console/limiter/qps/ranking', {
        params: { top }
    });
    return response.data || [];
}

export async function getQpsDetail(akCode: string): Promise<QpsDetail | null> {
    const response = await openapi.get<QpsDetail>(`/console/limiter/qps/${akCode}`);
    return response.data || null;
}

export async function updateQpsLimit(akCode: string, qpsLimit: number): Promise<boolean> {
    const response = await openapi.post<boolean>(`/console/limiter/qps/limit`, { akCode, qpsLimit });
    return response.data ?? false;
}

// QPS配置管理接口（独立于监控）
export interface QpsConfig {
    akCode: string;
    currentRpm: number;
    currentQps: number;
    limitQps: number;
    hasTraffic: boolean;
}

export async function getQpsConfig(akCode: string): Promise<QpsConfig | null> {
    const response = await openapi.get<QpsConfig>(`/console/limiter/qps/config/${akCode}`);
    return response.data || null;
}

export async function updateQpsConfig(akCode: string, qpsLimit: number): Promise<boolean> {
    const response = await openapi.post<boolean>('/console/limiter/qps/config', { akCode, qpsLimit });
    return response.data ?? false;
}

// 转交apikey
export async function transferApikey(request: TransferApikeyRequest): Promise<boolean> {
    try {
        const response = await openapi.post<boolean>('/console/apikey/owner/transfer', request);
        return response.data ?? false;
    } catch (error) {
        console.error('Error transferring apikey:', error);
        throw error;
    }
}

// 获取转交历史
export async function getTransferHistory(akCode: string): Promise<ApikeyTransferLog[]> {
    try {
        const response = await openapi.get<ApikeyTransferLog[]>('/console/apikey/transfer/history', {
            params: { akCode }
        });
        return response.data || [];
    } catch (error) {
        console.error('Error fetching transfer history:', error);
        throw error;
    }
}
