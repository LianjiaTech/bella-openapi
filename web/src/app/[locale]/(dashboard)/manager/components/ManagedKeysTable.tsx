'use client'

/**
 * ManagedKeysTable — 管理者视角 API Key 表格
 *
 * 职责：
 *   - Tab 切换展示两个独立区块：
 *     1. ManagedSection（我管理的）：顶层 AK，可重置密钥、进入子 AK 管理页
 *     2. AssignedSection（分配给我）：子AK，可重置密钥
 *   - Tab badge 显示各区块总数（由各 Section 加载完成后上报）
 *
 * 数据获取策略：
 *   - ManagedSection：getOwnedOrManagedApiKeys(page, managerCode, search)
 *     → 不传 includeChild，后端默认 parent_code='' 只返顶层AK
 *   - AssignedSection：getManagerApiKeys(page, managerCode, search, onlyChild=true)
 *     → onlyChild=true 由后端精确返回 manager_code 匹配的子 AK
 *
 * 防 re-render：
 *   - OWNER_TYPE_BADGE / formatSafetyLevel 均为模块级常量/纯函数
 *   - 两个 Section 始终挂载（CSS hidden 切换），避免切 Tab 时重新请求
 *   - onCopy / onReset / onCountChange 由 useCallback 包裹，引用稳定
 */

import { useState, useEffect, useCallback } from "react";
import { Button } from "@/components/common/button";
import { Badge } from "@/components/common/badge";
import { Copy, MoreVertical, Key, RotateCcw, Users, Pencil, UserCog, History, TrendingUp, Trash2 } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo, ApiKeyBalance, ParentQuotaApplyInfo } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import Link from "next/link";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";
import { SearchInput } from "@/app/[locale]/(dashboard)/apikey/components/SearchInput";
import { Pagination } from "@/components/ui/pagination";
import { getManagerApiKeys, getOwnedOrManagedApiKeys, getApiKeyBalance, getParentQuotaApplyInfo } from "@/lib/api/apiKeys";
import { cn } from "@/lib/utils";
import { buildChildQuotaApplyUrl, buildParentQuotaApplyUrl, isApiKeyQuotaApplyEnabled } from "@/lib/integrations/apiKeyQuotaApply";
import { toast } from "sonner";
import { TruncatedText } from "@/components/common/truncated-text";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";

/**
 * 所有者类型 Badge 映射
 * 优先级从高到低：system > org > project > person > console
 */
type OwnerTypeFilterValue = 'all' | 'person' | 'org' | 'project';

const OWNER_TYPE_OPTIONS: Array<{ value: OwnerTypeFilterValue; label: string }> = [
    { value: 'all', label: '全部类型' },
    { value: 'person', label: '个人' },
    { value: 'org', label: '组织' },
    { value: 'project', label: '项目' },
];

function OwnerTypeFilter({ value, onChange, ariaLabel }: {
    value: OwnerTypeFilterValue;
    onChange: (value: OwnerTypeFilterValue) => void;
    ariaLabel: string;
}) {
    return (
        <Select value={value} onValueChange={(nextValue) => onChange(nextValue as OwnerTypeFilterValue)}>
            <SelectTrigger className="w-28" aria-label={ariaLabel}>
                <SelectValue />
            </SelectTrigger>
            <SelectContent>
                {OWNER_TYPE_OPTIONS.map(option => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}

const OWNER_TYPE_BADGE: Record<string, { label: string; className: string }> = {
    system:  { label: '系统',    className: 'border-transparent bg-red-500/15 text-red-600' },
    org:     { label: '组织',    className: 'border-transparent bg-blue-500/15 text-blue-600' },
    project: { label: '项目',    className: 'border-transparent bg-purple-500/15 text-purple-600' },
    person:  { label: '个人',    className: 'border-transparent bg-secondary text-secondary-foreground' },
    console: { label: 'Console', className: 'border-transparent bg-secondary text-secondary-foreground' },
};

/** 安全等级文本映射 */
function formatSafetyLevel(level: number): string {
    const levels: Record<number, string> = { 10: '极低', 20: '低', 30: '中', 40: '高' };
    return levels[level] ?? level.toString();
}

function openQuotaApplyUrl(url: string) {
    window.open(url, "_blank", "noopener,noreferrer");
}

function hasManagerInfo(apiKey: Pick<ParentQuotaApplyInfo, 'managerCode' | 'managerName'>): boolean {
    return !!apiKey.managerCode && !!apiKey.managerName;
}

function formatMonthlyQuota(quota: number | null | undefined): string {
    if (quota === null || quota === undefined || !Number.isFinite(Number(quota))) return "-";
    return Number(quota).toLocaleString("zh-CN");
}

function MonthlyQuotaCell({ quota, onApply }: { quota: number | null | undefined; onApply?: () => void }) {
    return (
        <div className="flex items-center gap-2">
            <div className="inline-flex min-w-[92px] flex-col rounded-md border border-amber-200 bg-amber-50 px-2.5 py-1 text-left text-amber-900">
                <span className="text-[11px] leading-3 text-amber-700">月配额</span>
                <span className="text-sm font-semibold tabular-nums">{formatMonthlyQuota(quota)}</span>
            </div>
            {onApply && (
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8 px-2 text-xs"
                    onClick={onApply}
                >
                    <TrendingUp className="h-3.5 w-3.5" />
                    提额
                </Button>
            )}
        </div>
    );
}

interface ManagedKeysTableProps {
    managerCode: string;
    onCopy: (text: string) => void;
    /** 重置 AK 密钥 */
    onReset: (akCode: string) => void;
    onEditSafetyLevel: (akCode: string) => void;
    onSetManager: (apiKey: ApikeyInfo) => void;
    onViewHistory: (apiKey: ApikeyInfo) => void;
    onEditName: (apiKey: ApikeyInfo) => void;
    onEditService: (apiKey: ApikeyInfo) => void;
    onDelete: (apiKey: ApikeyInfo) => void;
    /** 外部触发刷新（重置成功后由 page 层递增） */
    refreshToken?: number;
}

// ─── 委托管理区块（顶层AK，parentCode 为空） ───────────────────────────────

/** 委托管理区块列数：密钥代码、所有者、名称、服务名、月额度、安全等级、月额度使用、备注、管理者、操作 */
const DELEGATED_COL_SPAN = 10;

interface DelegatedSectionProps {
    managerCode: string;
    onCopy: (text: string) => void;
    onReset: (akCode: string) => void;
    onEditSafetyLevel: (akCode: string) => void;
    onSetManager: (apiKey: ApikeyInfo) => void;
    onViewHistory: (apiKey: ApikeyInfo) => void;
    onEditName: (apiKey: ApikeyInfo) => void;
    onEditService: (apiKey: ApikeyInfo) => void;
    onDelete: (apiKey: ApikeyInfo) => void;
    onCountChange: (count: number) => void;
    refreshToken?: number;
}

function DelegatedSection({
    managerCode,
    onCopy,
    onReset,
    onEditSafetyLevel,
    onSetManager,
    onViewHistory,
    onEditName,
    onEditService,
    onDelete,
    onCountChange,
    refreshToken,
}: DelegatedSectionProps) {
    const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
    const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);
    const [totalItems, setTotalItems] = useState(0);
    const [search, setSearch] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [isSearching, setIsSearching] = useState(false);
    const [ownerType, setOwnerType] = useState<OwnerTypeFilterValue>('all');

    // 搜索防抖 500ms
    useEffect(() => {
        if (search !== debouncedSearch) setIsSearching(true);
        const timer = setTimeout(() => {
            setDebouncedSearch(search);
            setIsSearching(false);
        }, 500);
        return () => clearTimeout(timer);
    }, [search, debouncedSearch]);

    const fetchData = useCallback(async () => {
        if (!managerCode) return;
        try {
            setLoading(true);
            // ownerOrManagerCode → 后端返回当前用户拥有或管理的顶层 AK（parent_code=''）
            const res = await getOwnedOrManagedApiKeys(
                page,
                managerCode,
                debouncedSearch || undefined,
                ownerType === 'all' ? undefined : ownerType
            );
            setApiKeys(res.data || []);
            setHasMore(res.has_more);
            const total = res.total ?? 0;
            setTotalItems(total);
            onCountChange(total);
            // 并发拉取余额，不阻塞列表渲染
            (res.data || []).forEach(async (ak: ApikeyInfo) => {
                try {
                    const bal = await getApiKeyBalance(ak.code);
                    setBalances(prev => ({ ...prev, [ak.code]: bal }));
                } catch { /* ignore */ }
            });
        } catch (err) {
            console.error('Failed to fetch delegated keys:', err);
        } finally {
            setLoading(false);
        }
    }, [managerCode, page, debouncedSearch, ownerType, onCountChange]);

    useEffect(() => { fetchData(); }, [fetchData]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { if (refreshToken) fetchData(); }, [refreshToken]);

    const handleSearch = (value: string) => { setSearch(value); setPage(1); };
    const handleOwnerTypeChange = (value: OwnerTypeFilterValue) => { setOwnerType(value); setPage(1); };
    const quotaApplyEnabled = isApiKeyQuotaApplyEnabled();

    const handleApplyQuota = (apiKey: ApikeyInfo) => {
        if (!quotaApplyEnabled) return;
        if (apiKey.ownerType === 'person') {
            toast.error("个人 AK 不支持提额申请");
            return;
        }
        if (!hasManagerInfo(apiKey)) {
            toast.error("当前 AK 缺少负责人信息，无法发起提额审批");
            return;
        }

        const url = buildParentQuotaApplyUrl(apiKey);
        if (!url) {
            toast.error("额度申请地址配置无效");
            return;
        }
        openQuotaApplyUrl(url);
    };

    return (
        <div className="rounded-md border bg-card">
            <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <Users className="h-5 w-5" />
                    <h3 className="text-sm font-medium">我管理的</h3>
                    <span className="text-xs text-muted-foreground">（由我负责管理的个人、组织和项目父 AK）</span>
                </div>
                <div className="flex items-center gap-2">
                    <OwnerTypeFilter value={ownerType} onChange={handleOwnerTypeChange} ariaLabel="我管理的类型筛选" />
                    <SearchInput value={search} onChange={handleSearch} placeholder="搜索..." isSearching={isSearching} />
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码</TableHead>
                        <TableHead>归属</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>服务名</TableHead>
                        <TableHead className="min-w-[180px]">月配额 / 提额</TableHead>
                        <TableHead>安全等级</TableHead>
                        <TableHead>已用额度</TableHead>
                        <TableHead>备注</TableHead>
                        <TableHead>负责人</TableHead>
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={DELEGATED_COL_SPAN} />
                    ) : apiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={DELEGATED_COL_SPAN} className="text-center py-6 text-muted-foreground">
                                {debouncedSearch ? `未找到匹配 "${debouncedSearch}" 的结果` : '暂无受托管理的密钥'}
                            </TableCell>
                        </TableRow>
                    ) : apiKeys.map((apiKey) => (
                        <TableRow key={apiKey.code}>
                            <TableCell className="text-xs">
                                <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>{apiKey.akDisplay}</span>
                            </TableCell>
                            <TableCell className="text-sm">
                                <div className="flex items-center gap-1">
                                    {OWNER_TYPE_BADGE[apiKey.ownerType] && (
                                        <Badge className={OWNER_TYPE_BADGE[apiKey.ownerType].className}>
                                            {OWNER_TYPE_BADGE[apiKey.ownerType].label}
                                        </Badge>
                                    )}
                                    <div className="min-w-0">
                                        <div className="truncate max-w-[120px]" title={apiKey.ownerName}>
                                            {apiKey.ownerName || '-'}
                                        </div>
                                        {apiKey.ownerCode && (
                                            <div className="truncate max-w-[120px] text-xs text-muted-foreground" title={apiKey.ownerCode}>
                                                {apiKey.ownerCode}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </TableCell>
                            <TableCell className="text-sm">
                                <div className="flex items-center gap-1">
                                    <TruncatedText value={apiKey.name} />
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-5 w-5 p-0 opacity-50 hover:opacity-100 shrink-0"
                                        onClick={() => onEditName(apiKey)}
                                        aria-label={`修改名称 ${apiKey.code}`}
                                    >
                                        <Pencil className="h-3 w-3" />
                                    </Button>
                                </div>
                            </TableCell>
                            <TableCell className="text-sm">
                                <div className="flex items-center gap-1">
                                    <TruncatedText value={apiKey.serviceId} />
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-5 w-5 p-0 opacity-50 hover:opacity-100 shrink-0"
                                        onClick={() => onEditService(apiKey)}
                                        aria-label={`修改服务名 ${apiKey.code}`}
                                    >
                                        <Pencil className="h-3 w-3" />
                                    </Button>
                                </div>
                            </TableCell>
                            <TableCell className="text-sm">
                                <MonthlyQuotaCell
                                    quota={apiKey.monthQuota}
                                    onApply={quotaApplyEnabled && apiKey.ownerType !== 'person' ? () => handleApplyQuota(apiKey) : undefined}
                                />
                            </TableCell>
                            <TableCell className="text-sm">
                                <div className="flex items-center gap-1">
                                    <span>{formatSafetyLevel(apiKey.safetyLevel)}</span>
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                        onClick={() => onEditSafetyLevel(apiKey.code)}
                                        aria-label={`修改安全等级 ${apiKey.code}`}
                                    >
                                        <Pencil className="h-3 w-3" />
                                    </Button>
                                </div>
                            </TableCell>
                            <TableCell><QuotaUsageDisplay balance={balances[apiKey.code]} /></TableCell>
                            <TableCell className="text-sm"><TruncatedText value={apiKey.remark} /></TableCell>
                            <TableCell className="text-sm">
                                <div className="truncate max-w-[120px]" title={apiKey.managerName || apiKey.managerCode}>
                                    {apiKey.managerName || apiKey.managerCode || '-'}
                                </div>
                            </TableCell>
                            <TableCell className="text-center">
                                <Popover>
                                    <PopoverTrigger asChild>
                                        <Button variant="ghost" size="sm" aria-label={`打开父 AK 操作菜单 ${apiKey.code}`}>
                                            <MoreVertical className="h-4 w-4" />
                                        </Button>
                                    </PopoverTrigger>
                                    <PopoverContent align="end" className="w-48 p-2">
                                        <div className="flex flex-col gap-1">
                                            <Link
                                                href={`/apikey/sub-ak/${apiKey.code}?viewer=manager`}
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                            >
                                                <Key className="h-4 w-4" />
                                                管理子密钥
                                            </Link>
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onSetManager(apiKey)}
                                            >
                                                <UserCog className="h-4 w-4" />
                                                变更负责人
                                            </button>
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onViewHistory(apiKey)}
                                            >
                                                <History className="h-4 w-4" />
                                                变更历史
                                            </button>
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onCopy(apiKey.code)}
                                            >
                                                <Copy className="h-4 w-4" />
                                                复制ak code
                                            </button>
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onReset(apiKey.code)}
                                            >
                                                <RotateCcw className="h-4 w-4" />
                                                重置
                                            </button>
                                            {apiKey.ownerType === 'person' && (
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer"
                                                    onClick={() => onDelete(apiKey)}
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                    删除
                                                </button>
                                            )}
                                        </div>
                                    </PopoverContent>
                                </Popover>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            <div className="p-4">
                <Pagination
                    currentPage={page}
                    hasMore={hasMore}
                    totalItems={totalItems}
                    onPrevPage={() => setPage(p => Math.max(1, p - 1))}
                    onNextPage={() => setPage(p => p + 1)}
                    loading={loading}
                />
            </div>
        </div>
    );
}

// ─── 分配给我区块（子AK，parentCode 非空） ────────────────────────────────

/** 分配给我区块列数：密钥代码、父AK、名称、服务名、月额度、安全等级、月额度使用、备注、操作 */
const ASSIGNED_COL_SPAN = 9;

interface AssignedSectionProps {
    managerCode: string;
    onCopy: (text: string) => void;
    onReset: (akCode: string) => void;
    onSetManager: (apiKey: ApikeyInfo) => void;
    onCountChange: (count: number) => void;
    refreshToken?: number;
}

function AssignedSection({ managerCode, onCopy, onReset, onSetManager, onCountChange, refreshToken }: AssignedSectionProps) {
    const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
    const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);
    const [totalItems, setTotalItems] = useState(0);
    const [search, setSearch] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [isSearching, setIsSearching] = useState(false);
    const [ownerType, setOwnerType] = useState<OwnerTypeFilterValue>('all');

    // 搜索防抖 500ms
    useEffect(() => {
        if (search !== debouncedSearch) setIsSearching(true);
        const timer = setTimeout(() => {
            setDebouncedSearch(search);
            setIsSearching(false);
        }, 500);
        return () => clearTimeout(timer);
    }, [search, debouncedSearch]);

    const fetchData = useCallback(async () => {
        if (!managerCode) return;
        try {
            setLoading(true);
            // onlyChild=true → 后端直接过滤 parent_code != ''，分页计数准确
            const res = await getManagerApiKeys(
                page,
                managerCode,
                debouncedSearch || undefined,
                true,
                ownerType === 'all' ? undefined : ownerType
            );
            setApiKeys(res.data || []);
            setHasMore(res.has_more);
            const total = res.total ?? 0;
            setTotalItems(total);
            onCountChange(total);
            // 并发拉取余额
            (res.data || []).forEach(async (ak: ApikeyInfo) => {
                try {
                    const bal = await getApiKeyBalance(ak.code);
                    setBalances(prev => ({ ...prev, [ak.code]: bal }));
                } catch { /* ignore */ }
            });
        } catch (err) {
            console.error('Failed to fetch assigned keys:', err);
        } finally {
            setLoading(false);
        }
    }, [managerCode, page, debouncedSearch, ownerType, onCountChange]);

    useEffect(() => { fetchData(); }, [fetchData]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { if (refreshToken) fetchData(); }, [refreshToken]);

    const handleSearch = (value: string) => { setSearch(value); setPage(1); };
    const handleOwnerTypeChange = (value: OwnerTypeFilterValue) => { setOwnerType(value); setPage(1); };
    const quotaApplyEnabled = isApiKeyQuotaApplyEnabled();

    const handleApplyQuota = async (apiKey: ApikeyInfo) => {
        if (!quotaApplyEnabled) return;
        if (!apiKey.parentCode) {
            toast.error("当前子 AK 缺少父 AK 信息，无法发起提额审批");
            return;
        }

        try {
            const parentApiKey = await getParentQuotaApplyInfo(apiKey.code);
            if (!hasManagerInfo(parentApiKey)) {
                toast.error("父 AK 缺少负责人信息，无法发起提额审批");
                return;
            }

            const url = buildChildQuotaApplyUrl(apiKey, parentApiKey);
            if (!url) {
                toast.error("额度申请地址配置无效");
                return;
            }
            openQuotaApplyUrl(url);
        } catch (error) {
            console.error("Failed to fetch parent API key for quota apply:", error);
            toast.error("无法获取父 AK 信息，请稍后重试或联系管理员");
        }
    };

    return (
        <div className="rounded-md border bg-card">
            <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <Key className="h-5 w-5" />
                    <h3 className="text-sm font-medium">分配给我的</h3>
                    <span className="text-xs text-muted-foreground">（各类父 AK 分配并由我负责的子 AK）</span>
                </div>
                <div className="flex items-center gap-2">
                    <OwnerTypeFilter value={ownerType} onChange={handleOwnerTypeChange} ariaLabel="分配给我的类型筛选" />
                    <SearchInput value={search} onChange={handleSearch} placeholder="搜索..." isSearching={isSearching} />
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码 / 类型</TableHead>
                        <TableHead>密钥ID</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>服务名</TableHead>
                        <TableHead className="min-w-[180px]">月配额 / 提额</TableHead>
                        <TableHead>安全等级</TableHead>
                        <TableHead>已用额度</TableHead>
                        <TableHead>备注</TableHead>
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={ASSIGNED_COL_SPAN} />
                    ) : apiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={ASSIGNED_COL_SPAN} className="text-center py-6 text-muted-foreground">
                                {debouncedSearch ? `未找到匹配 "${debouncedSearch}" 的结果` : '暂无分配给我的密钥'}
                            </TableCell>
                        </TableRow>
                    ) : apiKeys.map((apiKey) => (
                        <TableRow key={apiKey.code}>
                            <TableCell className="text-xs">
                                <div className="flex items-center gap-2">
                                    <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>{apiKey.akDisplay}</span>
                                    {OWNER_TYPE_BADGE[apiKey.ownerType] && (
                                        <Badge className={OWNER_TYPE_BADGE[apiKey.ownerType].className}>
                                            {OWNER_TYPE_BADGE[apiKey.ownerType].label}
                                        </Badge>
                                    )}
                                </div>
                            </TableCell>
                            {/* AK code：帮助用户知道这个子AK的akcode */}
                            <TableCell className="text-xs">
                                {apiKey.code ? (
                                    <div className="inline-flex max-w-[160px] items-center gap-1.5 rounded-md border bg-muted/40 px-2 py-1 align-middle">
                                        <span
                                            className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap font-mono text-xs text-foreground [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
                                            title={apiKey.code}
                                        >
                                            {apiKey.code}
                                        </span>
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 shrink-0 p-0 text-muted-foreground hover:text-foreground"
                                            onClick={() => onCopy(apiKey.code)}
                                            aria-label={`复制密钥ID ${apiKey.code}`}
                                        >
                                            <Copy className="h-3.5 w-3.5" />
                                        </Button>
                                    </div>
                                ) : (
                                    <span className="text-muted-foreground">-</span>
                                )}
                            </TableCell>
                            <TableCell className="text-sm"><TruncatedText value={apiKey.name} /></TableCell>
                            <TableCell className="text-sm"><TruncatedText value={apiKey.serviceId} /></TableCell>
                            <TableCell className="text-sm">
                                <MonthlyQuotaCell
                                    quota={apiKey.monthQuota}
                                    onApply={quotaApplyEnabled ? () => handleApplyQuota(apiKey) : undefined}
                                />
                            </TableCell>
                            <TableCell className="text-sm">{formatSafetyLevel(apiKey.safetyLevel)}</TableCell>
                            <TableCell><QuotaUsageDisplay balance={balances[apiKey.code]} /></TableCell>
                            <TableCell className="text-sm"><TruncatedText value={apiKey.remark} /></TableCell>
                            <TableCell className="text-center">
                                <Popover>
                                    <PopoverTrigger asChild>
                                        <Button variant="ghost" size="sm"><MoreVertical className="h-4 w-4" /></Button>
                                    </PopoverTrigger>
                                    <PopoverContent align="end" className="w-48 p-2">
                                        <div className="flex flex-col gap-1">
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onSetManager(apiKey)}
                                            >
                                                <UserCog className="h-4 w-4" />
                                                转移管理权
                                            </button>
                                            {/* 重置：后端已保护不能转移/创建子AK */}
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onReset(apiKey.code)}
                                            >
                                                <RotateCcw className="h-4 w-4" />
                                                重置
                                            </button>
                                            <button
                                                className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                onClick={() => onCopy(apiKey.code)}
                                            >
                                                <Copy className="h-4 w-4" />
                                                复制密钥ID
                                            </button>
                                        </div>
                                    </PopoverContent>
                                </Popover>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            <div className="p-4">
                <Pagination
                    currentPage={page}
                    hasMore={hasMore}
                    totalItems={totalItems}
                    onPrevPage={() => setPage(p => Math.max(1, p - 1))}
                    onNextPage={() => setPage(p => p + 1)}
                    loading={loading}
                />
            </div>
        </div>
    );
}

// ─── 主组件：Tab 切换入口 ──────────────────────────────────────────────────

type ActiveTab = 'delegated' | 'assigned';

export function ManagedKeysTable({
    managerCode,
    onCopy,
    onReset,
    onEditSafetyLevel,
    onSetManager,
    onViewHistory,
    onEditName,
    onEditService,
    onDelete,
    refreshToken,
}: ManagedKeysTableProps) {
    const [activeTab, setActiveTab] = useState<ActiveTab>('delegated');
    const [delegatedCount, setDelegatedCount] = useState<number | null>(null);
    const [assignedCount, setAssignedCount] = useState<number | null>(null);

    const handleDelegatedCount = useCallback((count: number) => setDelegatedCount(count), []);
    const handleAssignedCount = useCallback((count: number) => setAssignedCount(count), []);

    return (
        <div>
            {/* Tab 导航栏 */}
            <div className="flex gap-1 mb-4 border-b">
                <button
                    onClick={() => setActiveTab('delegated')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors",
                        activeTab === 'delegated'
                            ? "border-primary text-primary"
                            : "border-transparent text-muted-foreground hover:text-foreground"
                    )}
                >
                    <Users className="h-4 w-4" />
                    我管理的
                    {delegatedCount !== null && (
                        <span className={cn(
                            "inline-flex items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium min-w-[20px]",
                            activeTab === 'delegated' ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                        )}>
                            {delegatedCount}
                        </span>
                    )}
                </button>
                <button
                    onClick={() => setActiveTab('assigned')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors",
                        activeTab === 'assigned'
                            ? "border-primary text-primary"
                            : "border-transparent text-muted-foreground hover:text-foreground"
                    )}
                >
                    <Key className="h-4 w-4" />
                    分配给我的
                    {assignedCount !== null && (
                        <span className={cn(
                            "inline-flex items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium min-w-[20px]",
                            activeTab === 'assigned' ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                        )}>
                            {assignedCount}
                        </span>
                    )}
                </button>
            </div>

            {/* 两个 Section 始终挂载，CSS 控制显隐，避免切 Tab 时重新请求 */}
            <div className={activeTab === 'delegated' ? undefined : 'hidden'}>
                <DelegatedSection
                    managerCode={managerCode}
                    onCopy={onCopy}
                    onReset={onReset}
                    onEditSafetyLevel={onEditSafetyLevel}
                    onSetManager={onSetManager}
                    onViewHistory={onViewHistory}
                    onEditName={onEditName}
                    onEditService={onEditService}
                    onDelete={onDelete}
                    onCountChange={handleDelegatedCount}
                    refreshToken={refreshToken}
                />
            </div>
            <div className={activeTab === 'assigned' ? undefined : 'hidden'}>
                <AssignedSection
                    managerCode={managerCode}
                    onCopy={onCopy}
                    onReset={onReset}
                    onSetManager={onSetManager}
                    onCountChange={handleAssignedCount}
                    refreshToken={refreshToken}
                />
            </div>
        </div>
    );
}
