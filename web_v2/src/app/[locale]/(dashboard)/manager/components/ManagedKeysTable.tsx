'use client'

/**
 * ManagedKeysTable — 管理者视角 API Key 表格
 *
 * 职责：
 *   - Tab 切换展示两个独立区块：
 *     1. DelegatedSection（委托管理）：顶层AK，可进入子AK管理页
 *     2. AssignedSection（分配给我）：子AK，可重置密钥
 *   - Tab badge 显示各区块总数（由各 Section 加载完成后上报）
 *
 * 数据获取策略：
 *   - DelegatedSection：getManagerApiKeys(page, managerCode, search)
 *     → 不传 includeChild，后端默认 parent_code='' 只返顶层AK
 *   - AssignedSection：getManagerApiKeys(page, managerCode, search, includeChild=true)
 *     → includeChild=true 后端返全量，前端按 parentCode 非空过滤出子AK
 *
 * 防 re-render：
 *   - OWNER_TYPE_BADGE / formatSafetyLevel 均为模块级常量/纯函数
 *   - 两个 Section 始终挂载（CSS hidden 切换），避免切 Tab 时重新请求
 *   - onCopy / onReset / onCountChange 由 useCallback 包裹，引用稳定
 */

import { useState, useEffect, useCallback } from "react";
import { Button } from "@/components/common/button";
import { Badge } from "@/components/common/badge";
import { Copy, MoreVertical, Key, RotateCcw, Users } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import Link from "next/link";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";
import { SearchInput } from "@/app/[locale]/(dashboard)/apikey/components/SearchInput";
import { Pagination } from "@/components/ui/pagination";
import { getManagerApiKeys, getApiKeyBalance } from "@/lib/api/apiKeys";
import { cn } from "@/lib/utils";

/**
 * 所有者类型 Badge 映射
 * 优先级从高到低：system > org > project > person > console
 */
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

interface ManagedKeysTableProps {
    managerCode: string;
    onCopy: (text: string) => void;
    /** 重置子AK密钥（仅分配给我的子AK可触发） */
    onReset: (akCode: string) => void;
    /** 外部触发刷新（重置成功后由 page 层递增） */
    refreshToken?: number;
}

// ─── 委托管理区块（顶层AK，parentCode 为空） ───────────────────────────────

/** 委托管理区块列数：密钥代码、所有者、名称、服务名、月额度、安全等级、月额度使用、备注、管理者、操作 */
const DELEGATED_COL_SPAN = 10;

interface DelegatedSectionProps {
    managerCode: string;
    onCopy: (text: string) => void;
    onCountChange: (count: number) => void;
    refreshToken?: number;
}

function DelegatedSection({ managerCode, onCopy, onCountChange, refreshToken }: DelegatedSectionProps) {
    const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
    const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);
    const [totalItems, setTotalItems] = useState(0);
    const [search, setSearch] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [isSearching, setIsSearching] = useState(false);

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
            // 不传 includeChild → 后端只返顶层AK（parent_code=''）
            // excludeOwnerType=person → 过滤掉个人AK，只显示组织/项目AK
            const res = await getManagerApiKeys(page, managerCode, debouncedSearch || undefined, undefined, 'person');
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
    }, [managerCode, page, debouncedSearch, onCountChange]);

    useEffect(() => { fetchData(); }, [fetchData]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { if (refreshToken) fetchData(); }, [refreshToken]);

    const handleSearch = (value: string) => { setSearch(value); setPage(1); };

    return (
        <div className="rounded-md border bg-card">
            <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <Users className="h-5 w-5" />
                    <h3 className="text-sm font-medium">我管理的</h3>
                    <span className="text-xs text-muted-foreground">（组织/项目委托我管理的密钥）</span>
                </div>
                <SearchInput value={search} onChange={handleSearch} placeholder="搜索..." isSearching={isSearching} />
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码</TableHead>
                        <TableHead>所有者</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>服务名</TableHead>
                        <TableHead>月配额</TableHead>
                        <TableHead>安全等级</TableHead>
                        <TableHead>已用额度</TableHead>
                        <TableHead>备注</TableHead>
                        <TableHead>管理者</TableHead>
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
                            <TableCell className="text-sm">{apiKey.name || '-'}</TableCell>
                            <TableCell className="text-sm">{apiKey.serviceId || '-'}</TableCell>
                            <TableCell className="text-sm">{apiKey.monthQuota || '-'}</TableCell>
                            <TableCell className="text-sm">{formatSafetyLevel(apiKey.safetyLevel)}</TableCell>
                            <TableCell><QuotaUsageDisplay balance={balances[apiKey.code]} /></TableCell>
                            <TableCell className="text-sm">
                                <div className="truncate max-w-[150px]" title={apiKey.remark}>{apiKey.remark || '-'}</div>
                            </TableCell>
                            <TableCell className="text-sm">
                                <div className="truncate max-w-[120px]" title={apiKey.managerName || apiKey.managerCode}>
                                    {apiKey.managerName || apiKey.managerCode || '-'}
                                </div>
                            </TableCell>
                            <TableCell className="text-center">
                                <Popover>
                                    <PopoverTrigger asChild>
                                        <Button variant="ghost" size="sm"><MoreVertical className="h-4 w-4" /></Button>
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
                                                onClick={() => onCopy(apiKey.code)}
                                            >
                                                <Copy className="h-4 w-4" />
                                                复制ak code
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

// ─── 分配给我区块（子AK，parentCode 非空） ────────────────────────────────

/** 分配给我区块列数：密钥代码、父AK、名称、服务名、月额度、安全等级、月额度使用、备注、操作 */
const ASSIGNED_COL_SPAN = 9;

interface AssignedSectionProps {
    managerCode: string;
    onCopy: (text: string) => void;
    onReset: (akCode: string) => void;
    onCountChange: (count: number) => void;
    refreshToken?: number;
}

function AssignedSection({ managerCode, onCopy, onReset, onCountChange, refreshToken }: AssignedSectionProps) {
    const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
    const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);
    const [totalItems, setTotalItems] = useState(0);
    const [search, setSearch] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [isSearching, setIsSearching] = useState(false);

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
            const res = await getManagerApiKeys(page, managerCode, debouncedSearch || undefined, true);
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
    }, [managerCode, page, debouncedSearch, onCountChange]);

    useEffect(() => { fetchData(); }, [fetchData]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { if (refreshToken) fetchData(); }, [refreshToken]);

    const handleSearch = (value: string) => { setSearch(value); setPage(1); };

    return (
        <div className="rounded-md border bg-card">
            <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <Key className="h-5 w-5" />
                    <h3 className="text-sm font-medium">分配给我的</h3>
                    <span className="text-xs text-muted-foreground">（组织/项目分配给我使用的子密钥）</span>
                </div>
                <SearchInput value={search} onChange={handleSearch} placeholder="搜索..." isSearching={isSearching} />
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码</TableHead>
                        <TableHead>所属密钥</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>服务名</TableHead>
                        <TableHead>月配额</TableHead>
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
                                <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>{apiKey.akDisplay}</span>
                            </TableCell>
                            {/* 父AK code：帮助用户知道这个子AK属于哪个组织 */}
                            <TableCell className="text-xs">
                                <span className="truncate max-w-[120px] block text-muted-foreground" title={apiKey.parentCode}>
                                    {apiKey.parentCode || '-'}
                                </span>
                            </TableCell>
                            <TableCell className="text-sm">{apiKey.name || '-'}</TableCell>
                            <TableCell className="text-sm">{apiKey.serviceId || '-'}</TableCell>
                            <TableCell className="text-sm">{apiKey.monthQuota || '-'}</TableCell>
                            <TableCell className="text-sm">{formatSafetyLevel(apiKey.safetyLevel)}</TableCell>
                            <TableCell><QuotaUsageDisplay balance={balances[apiKey.code]} /></TableCell>
                            <TableCell className="text-sm">
                                <div className="truncate max-w-[150px]" title={apiKey.remark}>{apiKey.remark || '-'}</div>
                            </TableCell>
                            <TableCell className="text-center">
                                <Popover>
                                    <PopoverTrigger asChild>
                                        <Button variant="ghost" size="sm"><MoreVertical className="h-4 w-4" /></Button>
                                    </PopoverTrigger>
                                    <PopoverContent align="end" className="w-48 p-2">
                                        <div className="flex flex-col gap-1">
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
                                                复制密钥编码
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

export function ManagedKeysTable({ managerCode, onCopy, onReset, refreshToken }: ManagedKeysTableProps) {
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
                    onCountChange={handleDelegatedCount}
                    refreshToken={refreshToken}
                />
            </div>
            <div className={activeTab === 'assigned' ? undefined : 'hidden'}>
                <AssignedSection
                    managerCode={managerCode}
                    onCopy={onCopy}
                    onReset={onReset}
                    onCountChange={handleAssignedCount}
                    refreshToken={refreshToken}
                />
            </div>
        </div>
    );
}
