'use client'

/**
 * SubAkTable — 子密钥列表表格
 *
 * 职责：展示指定父 AK 下的子密钥列表，根据 capability 控制列和操作按钮的显示。
 *
 * capability 由父组件 useSubAkCapability hook 计算后传入，本组件不感知 viewer 字符串：
 *   - capability.fetchMode：决定数据查询方式（user=getApiKeys, admin=getAdminApiKeys）
 *   - capability.canReset：控制重置按钮
 *   - capability.canDelete：控制删除按钮
 *   - capability.canSetManager：控制"设置管理人"按钮及"管理者"列的显示
 *     （owner/manager/admin 视角均为 true，在列中展示当前管理者，方便直接感知）
 *
 * 防 re-render：
 *   - capability 为稳定对象引用（由父组件 useMemo 产出），不会触发额外渲染
 *   - fetchSubApiKeys 依赖 capability.fetchMode，fetchMode 变化时重新请求
 *   - colSpan 由 capability.canSetManager 计算为常量，不引入额外 state
 */

import { Button } from "@/components/common/button";
import { Copy, MoreVertical, Info, RotateCcw, Trash2, Key, Pencil, Users } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import { TooltipProvider, TooltipTrigger } from "@/components/common/tooltip";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "../../../components/SearchInput";
import { getApiKeys, getAdminApiKeys, getApiKeyBalance } from "@/lib/api/apiKeys";
import { useState, useEffect, useCallback, forwardRef, useImperativeHandle } from "react";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";
import type { SubAkCapability } from "../hooks/useSubAkCapability";
import { Tooltip, TooltipContent } from "@/components/common/tooltip";

interface SubAkTableProps {
    ownerCode: string;
    parentCode: string;
    /** 操作能力对象，由父组件 useSubAkCapability 计算后传入 */
    capability: SubAkCapability;
    onCopy: (text: string) => void;
    onEdit: (apiKey: ApikeyInfo) => void;
    onReset: (akCode: string) => void;
    onDelete: (akCode: string) => void;
    /** 为子 AK 设置管理者（capability.canSetManager=true 时显示） */
    onSetManager: (apiKey: ApikeyInfo) => void;
}

export interface SubAkTableRef {
    refresh: () => void;
}

function formatSafetyLevel(level: number): string {
    const levels: Record<number, string> = {
        10: '极低',
        20: '低',
        30: '中',
        40: '高',
    };
    return levels[level] ?? level.toString();
}

export const SubAkTable = forwardRef<SubAkTableRef, SubAkTableProps>(({
    ownerCode,
    parentCode,
    capability,
    onCopy,
    onEdit,
    onReset,
    onDelete,
    onSetManager,
}, ref) => {
    // 内部状态管理
    const [subApiKeys, setSubApiKeys] = useState<ApikeyInfo[]>([]);
    const [loading, setLoading] = useState(true);
    const [currentPage, setCurrentPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);
    const [totalItems, setTotalItems] = useState(0);
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
    const [isSearching, setIsSearching] = useState(false);

    // 搜索防抖 - 500ms 延迟
    useEffect(() => {
        if (searchQuery !== debouncedSearchQuery) setIsSearching(true);
        const timer = setTimeout(() => {
            setDebouncedSearchQuery(searchQuery);
            setIsSearching(false);
        }, 500);

        return () => clearTimeout(timer);
    }, [searchQuery, debouncedSearchQuery]);

    /**
     * 查询子 AK 列表：
     *   - fetchMode=admin：getAdminApiKeys（不绑定 ownerCode，管理员/管理者视角）
     *   - fetchMode=user：getApiKeys（ownerCode = 当前用户，普通用户视角）
     */
    const fetchSubApiKeys = useCallback(async () => {
        try {
            setLoading(true);
            let response;
            if (capability.fetchMode === 'admin') {
                response = await getAdminApiKeys(currentPage, {
                    searchType: 'ak',
                    searchParam: debouncedSearchQuery || undefined,
                    parentCode,
                });
            } else {
                response = await getApiKeys(ownerCode, debouncedSearchQuery, currentPage, parentCode);
            }

            setHasMore(response.has_more);
            setTotalItems(response.total);

            // 获取每个子密钥的余额信息
            if (response.data && response.data.length > 0) {
                const apiKeysWithBalances = await Promise.all(
                    response.data.map(async (apiKey: ApikeyInfo) => {
                        try {
                            const balance = await getApiKeyBalance(apiKey.code);
                            return { ...apiKey, balance };
                        } catch {
                            return apiKey;
                        }
                    })
                );
                setSubApiKeys(apiKeysWithBalances as ApikeyInfo[]);
            } else {
                setSubApiKeys([]);
            }
        } catch (err) {
            console.error('Failed to fetch sub API keys:', err);
        } finally {
            setLoading(false);
        }
    }, [ownerCode, parentCode, debouncedSearchQuery, currentPage, capability.fetchMode]);

    useEffect(() => { fetchSubApiKeys(); }, [fetchSubApiKeys]);

    useImperativeHandle(ref, () => ({ refresh: fetchSubApiKeys }));

    // 处理搜索变化
    const handleSearchChange = useCallback((value: string) => {
        setSearchQuery(value);
        setCurrentPage(1);
    }, []);

    // 处理翻页
    const handlePrevPage = useCallback(() => {
        if (currentPage > 1) setCurrentPage(currentPage - 1);
    }, [currentPage]);

    const handleNextPage = useCallback(() => {
        if (hasMore) setCurrentPage(currentPage + 1);
    }, [hasMore, currentPage]);

    // canSetManager=true 时额外显示"管理者"列，colSpan 对应 +1
    const colSpan = capability.canSetManager ? 9 : 8;

    return (
        <div className="rounded-md border bg-card">
            <div className="p-4">
                <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                        <Key className="h-5 w-5" />
                        <h3 className="text-sm font-medium">子密钥列表</h3>
                    </div>

                    {/* 搜索框 */}
                    <SearchInput
                        value={searchQuery}
                        onChange={handleSearchChange}
                        placeholder="搜索子密钥..."
                        isSearching={isSearching}
                    />
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码</TableHead>
                        <TableHead className="whitespace-nowrap">名称</TableHead>
                        <TableHead className="whitespace-nowrap">用途标识</TableHead>
                        <TableHead className="whitespace-nowrap">安全等级</TableHead>
                        <TableHead className="whitespace-nowrap">月额度配置</TableHead>
                        <TableHead className="whitespace-nowrap">月额度使用</TableHead>
                        <TableHead className="whitespace-nowrap">备注</TableHead>
                        {/* 管理者列：canSetManager=true 时显示，帮助直观感知当前管理者 */}
                        {capability.canSetManager && <TableHead className="whitespace-nowrap">管理者</TableHead>}
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={colSpan} />
                    ) : subApiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={colSpan} className="text-center py-8 text-muted-foreground">
                                暂无数据
                            </TableCell>
                        </TableRow>
                    ) : (
                        subApiKeys.map((apiKey) => (
                            <TableRow key={apiKey.code}>
                                <TableCell className="font-mono text-xs">
                                    <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>
                                        {apiKey.akDisplay}
                                    </span>
                                </TableCell>
                                <TableCell className="text-sm">{apiKey.name || '-'}</TableCell>
                                <TableCell className="text-sm">{apiKey.outEntityCode || '-'}</TableCell>
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <span>{formatSafetyLevel(apiKey.safetyLevel)}</span>
                                        {/* 安全等级内联编辑：由 canEditQuota 控制 */}
                                        {capability.canEditQuota && (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                                onClick={() => onEdit(apiKey)}
                                            >
                                                <Pencil className="h-3 w-3" />
                                            </Button>
                                        )}
                                    </div>
                                </TableCell>
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <span>{apiKey.monthQuota || '-'}</span>
                                        {/* 月额度内联编辑：由 canEditQuota 控制 */}
                                        {capability.canEditQuota && (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                                onClick={() => onEdit(apiKey)}
                                            >
                                                <Pencil className="h-3 w-3" />
                                            </Button>
                                        )}
                                    </div>
                                </TableCell>
                                <TableCell>
                                    <QuotaUsageDisplay balance={apiKey.balance} />
                                </TableCell>
                                <TableCell className="text-sm">
                                    <TooltipProvider>
                                        <Tooltip><TooltipTrigger asChild><div className="truncate max-w-[150px]">
                                        {apiKey.remark || '-'}
                                        </div></TooltipTrigger>
                                    <TooltipContent>{apiKey.remark || '-'}</TooltipContent>
                                    </Tooltip>
                                    </TooltipProvider>
                                </TableCell>
                                {/* 管理者列：展示当前管理者，点击"设置管理人"可更换 */}
                                {capability.canSetManager && (
                                    <TableCell className="text-sm">
                                        <div className="flex items-center gap-1">
                                            <span className="truncate max-w-[100px] text-muted-foreground" title={apiKey.managerName || apiKey.managerCode}>
                                                {apiKey.managerName || apiKey.managerCode || '-'}
                                            </span>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="h-5 w-5 p-0 opacity-50 hover:opacity-100 shrink-0"
                                                onClick={() => onSetManager(apiKey)}
                                            >
                                                <Pencil className="h-3 w-3" />
                                            </Button>
                                        </div>
                                    </TableCell>
                                )}
                                <TableCell className="text-center">
                                    <Popover>
                                        <PopoverTrigger asChild>
                                            <Button variant="ghost" size="sm">
                                                <MoreVertical className="h-4 w-4" />
                                            </Button>
                                        </PopoverTrigger>
                                        <PopoverContent align="end" className="w-48 p-2">
                                            <div className="flex flex-col gap-1">
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                    onClick={() => onEdit(apiKey)}
                                                >
                                                    <Info className="h-4 w-4" />
                                                    编辑
                                                </button>
                                                {/* 重置：由 canReset 控制 */}
                                                {capability.canReset && (
                                                    <button
                                                        className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                        onClick={() => onReset(apiKey.code)}
                                                    >
                                                        <RotateCcw className="h-4 w-4" />
                                                        重置
                                                    </button>
                                                )}
                                                {/* 设置管理人：由 canSetManager 控制，owner/manager/admin 均可为子AK指派管理者 */}
                                                {capability.canSetManager && (
                                                    <button
                                                        className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                        onClick={() => onSetManager(apiKey)}
                                                    >
                                                        <Users className="h-4 w-4" />
                                                        设置管理人
                                                    </button>
                                                )}
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                    onClick={() => onCopy(apiKey.code)}
                                                >
                                                    <Copy className="h-4 w-4" />
                                                    复制ak code
                                                </button>
                                                {/* 删除：由 canDelete 控制 */}
                                                {capability.canDelete && (
                                                    <button
                                                        className="flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer"
                                                        onClick={() => onDelete(apiKey.code)}
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
                        ))
                    )}
                </TableBody>
            </Table>
            <div className="p-4">
                <Pagination
                    currentPage={currentPage}
                    hasMore={hasMore}
                    totalItems={totalItems}
                    onPrevPage={handlePrevPage}
                    onNextPage={handleNextPage}
                    loading={loading}
                />
            </div>
        </div>
    );
});

SubAkTable.displayName = 'SubAkTable';
