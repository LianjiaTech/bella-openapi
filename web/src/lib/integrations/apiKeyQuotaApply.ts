import type { ApikeyInfo, ParentQuotaApplyInfo } from "@/lib/types/apikeys";

/**
 * Optional external quota-application integration.
 *
 * Open-source deployments can leave NEXT_PUBLIC_APIKEY_QUOTA_APPLY_URL unset; the
 * UI will then render quota values without an application entry. Internal
 * deployments may configure the URL to point at their approval system. The query
 * parameter names below are intentionally contained in this integration adapter
 * so product components only depend on a generic "quota apply" capability.
 */
const QUOTA_APPLY_URL = process.env.NEXT_PUBLIC_APIKEY_QUOTA_APPLY_URL?.trim();

const REQUEST_TYPE = {
    parent: "PARENT_AK_QUOTA_INCREASE",
    child: "SUB_AK_QUOTA_INCREASE",
} as const;

type QueryValue = string | number | null | undefined;

function appendParam(url: URL, key: string, value: QueryValue) {
    if (value === null || value === undefined) return;
    const normalized = String(value).trim();
    if (!normalized) return;
    url.searchParams.set(key, normalized);
}

function buildUrl(params: Record<string, QueryValue>): string | null {
    if (!QUOTA_APPLY_URL) return null;

    let url: URL;
    try {
        url = new URL(QUOTA_APPLY_URL);
    } catch {
        return null;
    }

    url.searchParams.set("prefill", "1");
    Object.entries(params).forEach(([key, value]) => appendParam(url, key, value));

    return url.toString();
}

function toBpmManagerCode(managerCode: QueryValue): QueryValue {
    if (managerCode === null || managerCode === undefined) return managerCode;
    const normalized = String(managerCode).trim();
    if (!normalized) return normalized;

    return normalized.replace(/^10{1,7}/, "").replace(/^LW/i, "");
}

export function isApiKeyQuotaApplyEnabled(): boolean {
    return !!QUOTA_APPLY_URL;
}

export function buildParentQuotaApplyUrl(apiKey: ApikeyInfo): string | null {
    return buildUrl({
        REQUEST_TYPE: REQUEST_TYPE.parent,
        AK_CODE: apiKey.code,
        AK_NAME: apiKey.name,
        CURRENT_QUOTA: apiKey.monthQuota,
        PARENT_MANAGER_CODE: toBpmManagerCode(apiKey.managerCode),
        PARENT_MANAGER_NAME: apiKey.managerName,
        OWNER_TYPE: apiKey.ownerType,
        OWNER_CODE: apiKey.ownerCode,
        OWNER_NAME: apiKey.ownerName,
    });
}

export function buildChildQuotaApplyUrl(childApiKey: ApikeyInfo, parentApiKey: ParentQuotaApplyInfo): string | null {
    return buildUrl({
        REQUEST_TYPE: REQUEST_TYPE.child,
        AK_CODE: childApiKey.code,
        AK_NAME: childApiKey.name,
        TARGET_PARENT_AK_CODE: parentApiKey.code || childApiKey.parentCode,
        TARGET_PARENT_AK_NAME: parentApiKey.name,
        CURRENT_QUOTA: childApiKey.monthQuota,
        PARENT_MANAGER_CODE: toBpmManagerCode(parentApiKey.managerCode),
        PARENT_MANAGER_NAME: parentApiKey.managerName,
    });
}
