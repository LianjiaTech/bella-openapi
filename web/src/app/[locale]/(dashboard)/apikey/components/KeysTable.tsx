'use client'

import { useEffect, useState } from "react";
import { Button } from "@/components/common/button";
import { Check, Copy, MoreVertical, RotateCcw, UserPlus, Trash2, Key, Pencil, ScrollText } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import Link from "next/link";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";
import { copyToClipboard } from "@/lib/utils/clipboard";

/** 普通用户视图固定列数：密钥(ak)、ak-code、名称、服务名、月额度配置、安全等级、月额度使用、备注、操作 */
const COL_SPAN = 9;

interface KeysTableProps {
    apiKeys: ApikeyInfo[];
    balances: Record<string, ApiKeyBalance>;
    loading: boolean;
    searchQuery?: string;
    onCopy: (text: string) => void;
    onReset: (akCode: string) => void;
    onTransfer: (apiKey: ApikeyInfo) => void;
    onDelete: (akCode: string) => void;
    onEditName: (code: string, currentName: string) => void;
    onEditService: (code: string, currentServiceId: string) => void;
    onEditSafetyLevel: (akCode: string) => void;
    title?: string;
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

export function KeysTable({
    apiKeys, balances, loading, searchQuery,
    onCopy, onReset, onTransfer, onDelete,
    onEditSafetyLevel, onEditName, onEditService,
    title = "您的 API Keys",
}: KeysTableProps) {
    const [copiedCode, setCopiedCode] = useState<string | null>(null);

    useEffect(() => {
        if (!copiedCode) {
            return;
        }

        const timer = window.setTimeout(() => setCopiedCode(null), 1500);
        return () => window.clearTimeout(timer);
    }, [copiedCode]);

    const handleCopyCode = async (code: string) => {
        const success = await copyToClipboard(code);
        if (success) {
            setCopiedCode(code);
        }
    };

    return (
        <div className="rounded-md border bg-card w-min-[1000px]">
            <div className="p-4">
                <div className="flex items-center gap-2 mb-2">
                    <Key className="h-5 w-5" />
                    <h3 className="text-sm font-medium">{title}</h3>
                </div>
            </div>
            <Table className="w-full">
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥（ak）</TableHead>
                        <TableHead className="min-w-[280px]">密钥编码（ak-code）</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead className="whitespace-nowrap">服务名</TableHead>
                        <TableHead className="whitespace-nowrap">月额度配置</TableHead>
                        <TableHead className="whitespace-nowrap">安全等级</TableHead>
                        <TableHead className="whitespace-nowrap">月额度使用</TableHead>
                        <TableHead className="whitespace-nowrap">备注</TableHead>
                        <TableHead className="w-[100px] text-center">操作</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {loading ? (
                        <TableLoadingRow colSpan={COL_SPAN} />
                    ) : apiKeys.length === 0 ? (
                        <TableRow>
                            <TableCell colSpan={COL_SPAN} className="text-center py-8">
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
                                <TableCell className="text-xs ">
                                    <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>
                                        {apiKey.akDisplay}
                                    </span>
                                </TableCell>
                                <TableCell className="text-xs min-w-[280px]">
                                    <div className="flex items-center gap-1 whitespace-nowrap">
                                        <span title={apiKey.code}>
                                            {apiKey.code}
                                        </span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => handleCopyCode(apiKey.code)}
                                            aria-label={`复制 ${apiKey.code}`}
                                            title={copiedCode === apiKey.code ? "已复制" : "复制ak-code"}
                                        >
                                            {copiedCode === apiKey.code ? (
                                                <Check className="h-3.5 w-3.5 text-green-500" />
                                            ) : (
                                                <Copy className="h-3.5 w-3.5 text-muted-foreground" />
                                            )}
                                        </Button>
                                    </div>
                                </TableCell>
                                <TableCell className="text-sm w-1/5 whitespace-nowrap truncate">
                                    <div className="flex items-center gap-1">
                                        <span>{apiKey.name || '-'}</span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => onEditName(apiKey.code, apiKey.name || '')}
                                            aria-label={`编辑名称 ${apiKey.code}`}
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
                                </TableCell>
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <div className="whitespace-nowrap">{apiKey.serviceId || '-'}</div>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => onEditService(apiKey.code, apiKey.serviceId || '')}
                                            aria-label={`编辑服务名 ${apiKey.code}`}
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
                                </TableCell>
                                <TableCell className="text-sm">
                                    {apiKey.monthQuota || '-'}
                                </TableCell>
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-2">
                                        <span>{formatSafetyLevel(apiKey.safetyLevel)}</span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => onEditSafetyLevel(apiKey.code)}
                                            className="h-6 w-6 p-0 hover:bg-accent"
                                            title="编辑安全等级"
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
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
                                            <Button variant="ghost" size="sm" aria-label={`打开操作菜单 ${apiKey.code}`}>
                                                <MoreVertical className="h-4 w-4" />
                                            </Button>
                                        </PopoverTrigger>
                                        <PopoverContent align="end" className="w-48 p-2">
                                            <div className="flex flex-col gap-1">
                                                <Link
                                                    href={`/apikey/sub-ak/${apiKey.code}`}
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
                                                <Link
                                                    href={`/logs?queryType=${encodeURIComponent('AK Code')}&queryValue=${encodeURIComponent(apiKey.code)}`}
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                >
                                                    <ScrollText className="h-4 w-4" />
                                                    日志查询
                                                </Link>
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
