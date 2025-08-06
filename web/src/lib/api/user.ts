import { UserSearchResult } from "@/lib/types/openapi";
import { openapi } from '@/lib/api/openapi';

// 搜索用户
export async function searchUsers(keyword: string, limit: number = 20, excludeSelf: boolean = false): Promise<UserSearchResult[]> {
    try {
        const response = await openapi.get<UserSearchResult[]>('/v1/userInfo/search', {
            params: { keyword, limit, excludeSelf }
        });
        return response.data || [];
    } catch (error) {
        console.error('Error searching users:', error);
        throw error;
    }
}
