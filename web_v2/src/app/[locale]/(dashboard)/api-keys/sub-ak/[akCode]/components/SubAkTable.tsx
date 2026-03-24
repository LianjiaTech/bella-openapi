'use client'

import { Button } from "@/components/common/button";
import { Copy, MoreVertical, Info, RotateCcw, Trash2, Key } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "../../../components/SearchInput";
import { getApiKeys, getApiKeyBalance } from "@/lib/api/apiKeys";
import { useState, useEffect, useCallback, forwardRef, useImperativeHandle } from "react";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";

interface SubAkTableProps {
    ownerCode: string;
    parentCode: string;
    onCopy: (text: string) => void;
    onEdit: (apiKey: ApikeyInfo) => void;
    onReset: (akCode: string) => void;
    onDelete: (akCode: string) => void;
}

export interface SubAkTableRef {
    refresh: () => void;
}

export const SubAkTable = forwardRef<SubAkTableRef, SubAkTableProps>(({
    ownerCode,
    parentCode,
    onCopy,
    onEdit,
    onReset,
    onDelete
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
        if (searchQuery !== debouncedSearchQuery) {
            setIsSearching(true);
        }

        const timer = setTimeout(() => {
            setDebouncedSearchQuery(searchQuery);
            setIsSearching(false);
        }, 500);

        return () => clearTimeout(timer);
    }, [searchQuery, debouncedSearchQuery]);

    // 获取子密钥列表
    const fetchSubApiKeys = useCallback(async () => {
        try {
            setLoading(true);
            const response = await getApiKeys(ownerCode, debouncedSearchQuery, currentPage, parentCode);
            setSubApiKeys(response.data || []);
            setHasMore(response.has_more);
            setTotalItems(response.total);

            // 获取每个子密钥的余额信息
            if (response.data && response.data.length > 0) {
                const balancePromises = response.data.map(async (apiKey: ApikeyInfo) => {
                    try {
                        const balance = await getApiKeyBalance(apiKey.code);
                        // 将balance的值添加到apiKey中
                        return { ...apiKey, balance };
                    } catch (err) {
                        console.error(`Failed to fetch balance for ${apiKey.code}:`, err);
                        // 获取余额失败时，返回原始apiKey（不含balance字段）
                        return apiKey;
                    }
                });
                const apiKeysWithBalances = await Promise.all(balancePromises);
                setSubApiKeys(apiKeysWithBalances as ApikeyInfo[]);
            }
        } catch (err) {
            console.error('Failed to fetch sub API keys:', err);
        } finally {
            setLoading(false);
        }
    }, [ownerCode, parentCode, debouncedSearchQuery, currentPage]);

    // 获取子密钥列表 - 使用防抖后的搜索值
    useEffect(() => {
        fetchSubApiKeys();
    }, [fetchSubApiKeys]);

    // 暴露 refresh 方法给父组件
    useImperativeHandle(ref, () => ({
        refresh: fetchSubApiKeys
    }));

    // 处理搜索变化
    const handleSearchChange = useCallback((value: string) => {
        setSearchQuery(value);
        setCurrentPage(1); // 重置到第一页
    }, []);

    // 处理翻页
    const handlePrevPage = useCallback(() => {
        if (currentPage > 1) {
            setCurrentPage(currentPage - 1);
        }
    }, [currentPage]);

    const handleNextPage = useCallback(() => {
        if (hasMore) {
            setCurrentPage(currentPage + 1);
        }
    }, [hasMore, currentPage]);

    // 格式化安全等级
    const formatSafetyLevel = (level: number) => {
        const levels: Record<number, string> = {
            10: '极低',
            20: '低',
            30: '中',
            40: '高',
            // 50: '高'
        };
        return levels[level] || level.toString();
    };


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
                        <TableHead className="">密钥代码</TableHead>
                        <TableHead className=" ">名称</TableHead>
                        <TableHead className="">用途标识</TableHead>
                        <TableHead className="">安全等级</TableHead>
                        <TableHead className="w-[200px]">月额度配置</TableHead>
                        <TableHead className="w-[200px]">月额度使用</TableHead>
                        <TableHead className="">备注</TableHead>
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={7} />
                    ) : subApiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                                暂无数据
                            </TableCell>
                        </TableRow>
                    ) : (
                        subApiKeys.map((apiKey) => (
                            <TableRow key={apiKey.code}>
                                <TableCell className="font-mono text-xs">
                                    <div className="flex items-center gap-2 ">
                                        <span className="truncate max-w-[150px]" title={apiKey.akDisplay}>
                                            {apiKey.akDisplay}
                                        </span>
                                        {/* <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => onCopy(apiKey.code)}
                                            className="h-6 w-6 p-0"
                                        >
                                            <Copy className="h-3 w-3" />
                                        </Button> */}
                                    </div>
                                </TableCell>
                                <TableCell className="text-sm">
                                    {apiKey.name || '-'}
                                </TableCell>
                                <TableCell className="text-sm">
                                    {apiKey.outEntityCode || '-'}
                                </TableCell>
                                <TableCell className="text-sm">
                                    {formatSafetyLevel(apiKey.safetyLevel)}
                                </TableCell>
                                <TableCell className="text-sm">
                                    {apiKey.monthQuota || '-'}
                                </TableCell>
                                <TableCell>
                                    <QuotaUsageDisplay balance={apiKey.balance} />
                                </TableCell>
                                <TableCell className="text-sm">
                                    <div className="truncate max-w-[150px]" title={apiKey.remark}>
                                        {apiKey.remark || '-'}
                                    </div>
                                </TableCell>
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
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                    onClick={() => onReset(apiKey.code)}
                                                >
                                                    <RotateCcw className="h-4 w-4" />
                                                    重置
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer hover:text-white"
                                                    onClick={() => onCopy(apiKey.code)}
                                                >
                                                    <Copy className="h-4 w-4" />
                                                    复制ak code
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer"
                                                    onClick={() => onDelete(apiKey.code)}
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                    删除
                                                </button>
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
