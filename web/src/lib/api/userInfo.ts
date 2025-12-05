import {UserInfo} from "@/lib/types/openapi";
import {openapi} from '@/lib/api/openapi';
import {AntPathMatcher} from "@/lib/ant-path-matcher";

export async function getUserInfo() : Promise<UserInfo> {
    try {
        const response = await openapi.get<UserInfo>('/console/userInfo');
        return response.data || {userId: 0, userName: ""};
    } catch (err) {
        return {userId: 0, userName: "", optionalInfo: {}};
    }
}

export function hasPermission(user: UserInfo | null, url: string): boolean {
    if (user?.optionalInfo == null) {
        return false;
    }
    const included: string[] = user.optionalInfo['roles'] as string[] || [];
    const excluded: string[] = user.optionalInfo['excludes'] as string[] || [];
    return (
        included.some(pattern => AntPathMatcher.match(pattern, url)) &&
        !excluded.some(pattern => AntPathMatcher.match(pattern, url))
    );
}
