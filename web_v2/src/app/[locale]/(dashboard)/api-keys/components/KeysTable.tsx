'use client'

import { Button } from "@/components/common/button";
import { Copy, MoreVertical, RotateCcw, UserPlus, Trash2, Key } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import Link from "next/link";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";

interface KeysTableProps {
    apiKeys: ApikeyInfo[];
    balances: Record<string, ApiKeyBalance>;
    loading: boolean;
    searchQuery?: string;
    onCopy: (text: string) => void;
    onReset: (akCode: string) => void;
    onTransfer: (apiKey: ApikeyInfo) => void;
    onDelete: (akCode: string) => void;
}

export function KeysTable({ apiKeys, balances, loading, searchQuery, onCopy, onReset, onTransfer, onDelete }: KeysTableProps) {
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
                <div className="flex items-center gap-2 mb-2">
                    <span>
                        <Key className="h-5 w-5" />
                    </span>
                    <h3 className="text-sm font-medium">您的 API Keys</h3>
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead >密钥代码</TableHead>
                        <TableHead >名称</TableHead>
                        <TableHead >服务名</TableHead>                      
                        <TableHead >月额度配置</TableHead>
                        <TableHead >安全等级</TableHead>
                        <TableHead >月额度使用</TableHead>
                        <TableHead >备注</TableHead>
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={7} />
                    ) : apiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={7} className="text-center py-8">
                                {searchQuery ? (
                                    <div className="flex flex-col items-center gap-2">
                                        <p className="text-muted-foreground">
                                            未找到匹配 <span className="font-semibold text-foreground">"{searchQuery}"</span> 的结果
                                        </p>
                                        <p className="text-sm text-muted-foreground">
                                            请尝试其他关键词或清空搜索查看所有数据
                                        </p>
                                    </div>
                                ) : (
                                    <p className="text-muted-foreground">暂无数据</p>
                                )}
                            </TableCell>
                        </TableRow>
                    ) : (
                        apiKeys.map((apiKey) => (
                            <TableRow key={apiKey.code}>
                                <TableCell className="text-xs">
                                    <div className="flex items-center gap-2">
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
                                    {apiKey.serviceId || '-'}
                                </TableCell>
                                <TableCell className="text-sm">
                                    {apiKey.monthQuota || '-'}
                                </TableCell>
                                <TableCell className="text-sm">
                                    {formatSafetyLevel(apiKey.safetyLevel)}
                                </TableCell>
                                <TableCell>
                                    <QuotaUsageDisplay balance={balances[apiKey.code]} />
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
                                                <Link
                                                    href={`/api-keys/sub-ak/${apiKey.code}`}
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                >
                                                    <Copy className="h-4 w-4" />
                                                    管理子AK
                                                </Link>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onReset(apiKey.code)}
                                                >
                                                    <RotateCcw className="h-4 w-4" />
                                                    重置
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onTransfer(apiKey)}
                                                >
                                                    <UserPlus className="h-4 w-4" />
                                                    转交
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
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
        </div>
    );
}
