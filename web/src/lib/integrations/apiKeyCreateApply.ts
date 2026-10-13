"use client";

/**
 * Optional external parent-AK creation application integration.
 *
 * Open-source deployments can leave NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL unset; the
 * manager page will then hide the creation entry. Internal deployments may
 * configure the URL to point at their approval system. Query parameter names are
 * intentionally encapsulated here so product components only depend on a generic
 * "create apply" capability.
 */

const CREATE_APPLY_URL = process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL?.trim();
const REQUEST_TYPE = "CREATE_PARENT_AK";

export type ApiKeyCreateOwnerType = "org" | "project";

type QueryValue = string | number | null | undefined;

function appendParam(url: URL, key: string, value: QueryValue) {
    if (value === null || value === undefined) return;

    const normalized = String(value).trim();
    if (!normalized) return;

    url.searchParams.set(key, normalized);
}

function buildUrl(params: Record<string, QueryValue>): string | null {
    if (!CREATE_APPLY_URL) return null;

    let url: URL;
    try {
        url = new URL(CREATE_APPLY_URL);
    } catch {
        return null;
    }

    url.searchParams.set("prefill", "1");
    Object.entries(params).forEach(([key, value]) => appendParam(url, key, value));

    return url.toString();
}

export function isApiKeyCreateApplyEnabled(): boolean {
    return !!CREATE_APPLY_URL;
}

export function buildApiKeyCreateApplyUrl(ownerType?: ApiKeyCreateOwnerType): string | null {
    return buildUrl({
        REQUEST_TYPE,
        OWNER_TYPE: ownerType,
    });
}
