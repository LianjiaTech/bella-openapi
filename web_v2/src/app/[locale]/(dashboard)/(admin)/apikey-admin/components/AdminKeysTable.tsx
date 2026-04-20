'use client'

/**
 * AdminKeysTable — 管理员专用 API Key 表格
 *
 * 与 KeysTable 的设计差异（单一职责，不含 isAdminView 分支）：
 *   - 所有者列（ownerType Badge + ownerName）：固定显示，无条件渲染
 *   - 重置操作：固定隐藏（管理员不应代替用户重置 key）
 *   - 转交 / 删除：由 isSuperAdmin prop 控制是否显示
 *   - colSpan 固定为 8（密钥代码 + 所有者 + 名称 + 服务名 + 月额度 + 安全等级 + 月额度使用 + 备注 + 操作 = 实际9列，空态用8不含操作列）
 *
 * 防 re-render：
 *   - isSuperAdmin 为原始布尔值，由父组件 useMemo 计算后传入，引用稳定
 *   - OWNER_TYPE_BADGE / formatSafetyLevel 均为模块级常量/纯函数，不会触发 re-render
 */

import { Button } from "@/components/common/button";
import { Badge } from "@/components/common/badge";
import { Copy, MoreVertical, UserPlus, Trash2, Key, Pencil, Users, Building2, Waypoints, History } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/common/table";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/common/popover";
import Link from "next/link";
import { TableLoadingRow } from "@/components/ui/table/TableLoadingRow";
import { QuotaUsageDisplay } from "@/components/ui/QuotaUsageDisplay";
import { ApiKeyAdminAction } from "./apiKeyAdminAction";

/** 表格固定列数：密钥代码、所有者、名称、服务名、月额度配置、安全等级、月额度使用、备注、管理者、操作 */
const COL_SPAN = 10;

/**
 * 所有者类型 Badge 映射（模块级常量，不参与渲染依赖）
 * 优先级从高到低：system > org > project > person > console
 * 用 className 覆盖颜色，体现层级差异
 */
const OWNER_TYPE_BADGE: Record<string, { label: string; className: string }> = {
    system:  { label: '系统',    className: 'border-transparent bg-red-500/15 text-red-600' },
    org:     { label: '组织',    className: 'border-transparent bg-blue-500/15 text-blue-600' },
    project: { label: '项目',    className: 'border-transparent bg-purple-500/15 text-purple-600' },
    person:  { label: '个人',    className: 'border-transparent bg-secondary text-secondary-foreground' },
    console: { label: 'Console', className: 'border-transparent bg-secondary text-secondary-foreground' },
};

/** 安全等级文本映射（纯函数，不含 state 依赖） */
function formatSafetyLevel(level: number): string {
    const levels: Record<number, string> = {
        10: '极低',
        20: '低',
        30: '中',
        40: '高',
    };
    return levels[level] ?? level.toString();
}

interface AdminKeysTableProps {
    apiKeys: ApikeyInfo[];
    balances: Record<string, ApiKeyBalance>;
    loading: boolean;
    searchQuery?: string;
    /** 是否为超级管理员：控制转交/删除操作的可见性 */
    isSuperAdmin: boolean;
    onCopy: (text: string) => void;
    onOpenAction: (action: ApiKeyAdminAction, apiKey: ApikeyInfo) => void;
    onDelete: (akCode: string) => void;
    onEditName: (code: string, currentName: string) => void;
    onEditService: (code: string, currentServiceId: string) => void;
    onEditSafetyLevel: (akCode: string) => void;
    onEditQuota: (code: string, currentQuota: number) => void;
}

export function AdminKeysTable({
    apiKeys,
    balances,
    loading,
    searchQuery,
    isSuperAdmin,
    onCopy,
    onOpenAction,
    onDelete,
    onEditName,
    onEditService,
    onEditSafetyLevel,
    onEditQuota,
}: AdminKeysTableProps) {
    return (
        <div className="rounded-md border bg-card">
            <div className="p-4">
                <div className="flex items-center gap-2 mb-2">
                    <Key className="h-5 w-5" />
                    <h3 className="text-sm font-medium">全量 API Keys</h3>
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>密钥代码</TableHead>
                        <TableHead>所有者</TableHead>
                        <TableHead>名称</TableHead>
                        <TableHead>服务名</TableHead>
                        <TableHead>月额度配置</TableHead>
                        <TableHead>安全等级</TableHead>
                        <TableHead>月额度使用</TableHead>
                        <TableHead>备注</TableHead>
                        <TableHead>管理者</TableHead>
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
                                {/* 密钥代码 */}
                                <TableCell className="text-xs">
                                    <span className="truncate max-w-[150px] block" title={apiKey.akDisplay}>
                                        {apiKey.akDisplay}
                                    </span>
                                </TableCell>

                                {/* 所有者：ownerType Badge + ownerName + ownerCode（重名时可区分） */}
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

                                {/* 名称（可编辑） */}
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <span>{apiKey.name || '-'}</span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => onEditName(apiKey.code, apiKey.name || '')}
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
                                </TableCell>

                                {/* 服务名（可编辑） */}
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <span>{apiKey.serviceId || '-'}</span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => onEditService(apiKey.code, apiKey.serviceId || '')}
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
                                </TableCell>

                                {/* 月额度配置（可编辑） */}
                                <TableCell className="text-sm">
                                    <div className="flex items-center gap-1">
                                        <span>{apiKey.monthQuota || '-'}</span>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="h-5 w-5 p-0 opacity-50 hover:opacity-100"
                                            onClick={() => onEditQuota(apiKey.code, apiKey.monthQuota || 0)}
                                        >
                                            <Pencil className="h-3 w-3" />
                                        </Button>
                                    </div>
                                </TableCell>

                                {/* 安全等级（可编辑） */}
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

                                {/* 月额度使用 */}
                                <TableCell>
                                    <QuotaUsageDisplay balance={balances[apiKey.code]} />
                                </TableCell>

                                {/* 备注 */}
                                <TableCell className="text-sm">
                                    <div className="truncate max-w-[150px]" title={apiKey.remark}>
                                        {apiKey.remark || '-'}
                                    </div>
                                </TableCell>

                                {/* 管理者：名称 + managerCode 副行，重名时可区分 */}
                                <TableCell className="text-sm">
                                    <div className="min-w-0">
                                        <div className="truncate max-w-[120px]" title={apiKey.managerName}>
                                            {apiKey.managerName || '-'}
                                        </div>
                                        {apiKey.managerCode && (
                                            <div className="truncate max-w-[120px] text-xs text-muted-foreground" title={apiKey.managerCode}>
                                                {apiKey.managerCode}
                                            </div>
                                        )}
                                    </div>
                                </TableCell>

                                {/* 操作菜单：无重置；转交/删除仅超级管理员可见 */}
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
                                                    href={`/apikey/sub-ak/${apiKey.code}?viewer=admin`}
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                >
                                                    <Copy className="h-4 w-4" />
                                                    管理子AK
                                                </Link>
                                                {/* 设置管理人：管理员可为任意 AK 设置管理者 */}
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onOpenAction('manager', apiKey)}
                                                >
                                                    <Users className="h-4 w-4" />
                                                    设置管理人
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onOpenAction('ownerChange', apiKey)}
                                                >
                                                    <Building2 className="h-4 w-4" />
                                                    变更所有者
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onOpenAction('parentChange', apiKey)}
                                                >
                                                    <Waypoints className="h-4 w-4" />
                                                    迁移父级
                                                </button>
                                                <button
                                                    className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                    onClick={() => onOpenAction('history', apiKey)}
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
                                                {/* 转交 / 删除：仅超级管理员可见 */}
                                                {isSuperAdmin && (
                                                    <>
                                                        <button
                                                            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent rounded cursor-pointer"
                                                            onClick={() => onOpenAction('transfer', apiKey)}
                                                        >
                                                            <UserPlus className="h-4 w-4" />
                                                            转交
                                                        </button>
                                                        <button
                                                            className="flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer"
                                                            onClick={() => onDelete(apiKey.code)}
                                                        >
                                                            <Trash2 className="h-4 w-4" />
                                                            删除
                                                        </button>
                                                    </>
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
        </div>
    );
}
