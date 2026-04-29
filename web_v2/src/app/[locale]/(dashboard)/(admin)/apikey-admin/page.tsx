'use client'

/**
 * API Key 管理员视图页面
 *
 * 职责：管理员查看并操作全量 API Key。
 *   - 数据源：getAdminApiKeys（不绑定 ownerCode，返回全量数据）
 *   - 搜索维度：ak（名称/服务名）/ owner（所有者名称/ID）/ code（AK Code 精确查找）可切换
 *   - 所有者类型筛选：全部 / 个人 / 组织
 *   - 操作权限：转交/删除仅 isSuperAdmin 可见（由 AdminKeysTable 内部控制）
 *   - 创建：管理员可创建 org/project 类型顶层 AK（支持指定管理人）
 *
 * 鉴权：已由父级 (admin)/layout.tsx 统一前置，本页无需重复判断 isAdmin。
 *
 * 防 re-render：
 *   - isSuperAdmin 通过 useMemo 计算，仅 user 变化时重算
 *   - handleAdminSearchTypeChange 声明在 useCallback 内，切换维度时清空搜索词防止旧词错位
 *   - fetchApiKeys 依赖 adminSearchType / adminOwnerTypeFilter，筛选变化时自动重新请求
 */

import { TopBar } from "@/components/layout/top-bar";
import { Button } from "@/components/common/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";
import { AlertCircle, Plus } from "lucide-react";
import { useState, useEffect, useCallback, useMemo } from "react";
import { getAdminApiKeys, getApiKeyBalance, getApiKeyByCode, deleteApiKey, renameApiKey, bindApiKeyService, updateApiKeyQuota } from "@/lib/api/apiKeys";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { AdminKeysTable } from "./components/AdminKeysTable";
import { Pagination } from "@/components/ui/pagination";
import { Input } from "@/components/common/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/common/dialog";
import { ApiKeyTransferDialog } from "@/app/[locale]/(dashboard)/apikey/components/apiKeyTransferDialog/ApiKeyTransferDialog";
import { ApiKeyDeleteDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyDeleteDialog";
import { UpdateSafeLevel } from "@/app/[locale]/(dashboard)/apikey/components/UpdateSafeLevel";
import { SearchInput } from "@/app/[locale]/(dashboard)/apikey/components/SearchInput";
import { ManagerDialog } from "@/app/[locale]/(dashboard)/apikey/components/ManagerDialog";
import { AdminCreateDialog } from "./components/AdminCreateDialog";
import { ApiKeyCreatedDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyCreatedDialog";
import { OwnerChangeDialog } from "./components/OwnerChangeDialog";
import { ParentChangeDialog } from "./components/ParentChangeDialog";
import { ApiKeyHistoryDialog } from "./components/ApiKeyHistoryDialog";
import { ApiKeyAdminAction } from "./components/apiKeyAdminAction";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { toast } from "sonner";
import { useAuth } from "@/components/providers/auth-provider";

export default function ApiKeyAdminPage() {
    const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
    const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(false);

    // 搜索相关
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
    const [isSearching, setIsSearching] = useState(false);

    /** 搜索维度：ak=名称/服务名，owner=所有者名称/ID，code=AK Code 精确查找，manager=管理人（待联调） */
    const [searchType, setSearchType] = useState<'ak' | 'owner' | 'code' | 'manager'>('ak');
    /** 所有者类型筛选 */
    const [ownerTypeFilter, setOwnerTypeFilter] = useState<'all' | 'person' | 'org' | 'project'>('all');

    // 删除对话框
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const [deletingAkCode, setDeletingAkCode] = useState<string>("");
    const [deleting, setDeleting] = useState(false);

    // 编辑名称 / 服务名弹窗
    const [editingField, setEditingField] = useState<'name' | 'service' | null>(null);
    const [editingCode, setEditingCode] = useState<string>("");
    const [editingValue, setEditingValue] = useState<string>("");
    const [editSubmitting, setEditSubmitting] = useState(false);

    // 安全等级编辑对话框
    const [showSafeLevelDialog, setShowSafeLevelDialog] = useState(false);
    const [editingAkCode, setEditingAkCode] = useState<string>("");

    // 月额度编辑对话框
    const [showQuotaDialog, setShowQuotaDialog] = useState(false);
    const [quotaEditingCode, setQuotaEditingCode] = useState<string>("");
    const [quotaEditingValue, setQuotaEditingValue] = useState<string>("");
    const [quotaSubmitting, setQuotaSubmitting] = useState(false);

    // 创建 AK 弹窗
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [showCreatedDialog, setShowCreatedDialog] = useState(false);
    const [newApiKey, setNewApiKey] = useState("");

    // admin 业务动作统一状态：页面只关心“当前处理哪个 AK、打开哪个动作”
    const [activeAction, setActiveAction] = useState<ApiKeyAdminAction | null>(null);
    const [activeApiKey, setActiveApiKey] = useState<ApikeyInfo | null>(null);

    const { user } = useAuth();

    /**
     * 超级管理员判断：roleCode=all 时转交/删除可见
     * 仅在 user 变化时重算，避免每次渲染执行
     */
    const isSuperAdmin = useMemo(
        () => user?.optionalInfo?.['roleCode'] === 'all',
        [user]
    );

    /**
     * 获取全量 API Keys
     * 依赖 searchType / ownerTypeFilter，筛选变化时 useCallback 重建，触发 useEffect 重新请求
     * activeAction=transfer 时暂停刷新，防止转交流程中数据抖动
     */
    const fetchApiKeys = useCallback(async (currentPage: number, search: string) => {
        if (activeAction === 'transfer') return;
        try {
            setLoading(true);
            setError(null);

            // code 维度：精确查找单条，不走分页接口
            if (searchType === 'code') {
                if (!search.trim()) {
                    setApiKeys([]);
                    setHasMore(false);
                    return;
                }
                const result = await getApiKeyByCode(search.trim());
                setApiKeys(result ? [result] : []);
                setHasMore(false);
                if (result) {
                    try {
                        const balance = await getApiKeyBalance(result.code);
                        setBalances(prev => ({ ...prev, [result.code]: balance }));
                    } catch (err) {
                        console.error(`Failed to fetch balance for ${result.code}:`, err);
                    }
                }
                return;
            }

            // TODO: 后端 /console/apikey/page 已支持 managerSearch 参数（模糊搜索管理人）
            // 需在 AdminApiKeyQueryParams 中追加 managerSearch?: string，并在此传参
            // 联调确认后激活以下逻辑：
            // if (searchType === 'manager') queryParams.managerSearch = search;

            const response = await getAdminApiKeys(currentPage, {
                searchType: searchType === 'manager' ? 'ak' : searchType,
                searchParam: searchType === 'ak' ? search : undefined,
                ownerSearch: searchType === 'owner' ? search : undefined,
                ownerType: ownerTypeFilter === 'all' ? undefined : ownerTypeFilter as 'person' | 'org' | 'project',
            });

            setApiKeys(response.data || []);
            setHasMore(response.has_more);

            // 并发拉取每条记录的余额信息，按 code key 存储，不会因分页/筛选变化而错配
            if (response.data && response.data.length > 0) {
                response.data.forEach(async (apiKey: ApikeyInfo) => {
                    try {
                        const balance = await getApiKeyBalance(apiKey.code);
                        setBalances(prev => ({ ...prev, [apiKey.code]: balance }));
                    } catch (err) {
                        console.error(`Failed to fetch balance for ${apiKey.code}:`, err);
                    }
                });
            }
        } catch (err) {
            setError(err instanceof Error ? err : new Error('获取 API Keys 失败'));
        } finally {
            setLoading(false);
        }
    }, [activeAction, searchType, ownerTypeFilter]);

    // 搜索防抖 500ms
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

    // 筛选/分页/搜索词变化时重新请求
    useEffect(() => {
        fetchApiKeys(page, debouncedSearchQuery);
    }, [fetchApiKeys, page, debouncedSearchQuery]);

    // 复制
    const handleCopy = useCallback(async (text: string) => {
        const success = await copyToClipboard(text);
        if (success) {
            toast.success('复制成功');
        } else {
            toast.error('复制失败，请手动复制');
        }
    }, []);

    // 分页
    const handleNextPage = useCallback(() => setPage(prev => prev + 1), []);
    const handlePrevPage = useCallback(() => setPage(prev => Math.max(1, prev - 1)), []);

    /**
     * 搜索维度切换：必须同步清空 searchQuery，防止旧词发送到错误参数位置
     * （ak 维度的词传给 ownerSearch 或反之，会导致语义错误的查询）
     */
    const handleSearchTypeChange = useCallback((value: string) => {
        setSearchType(value as 'ak' | 'owner' | 'code' | 'manager');
        setSearchQuery("");
        setDebouncedSearchQuery("");
        setPage(1);
    }, []);

    // 删除
    const handleDeleteClick = useCallback((akCode: string) => {
        setDeletingAkCode(akCode);
        setShowDeleteDialog(true);
    }, []);

    const handleDeleteConfirm = useCallback(async () => {
        if (!deletingAkCode) return;
        try {
            setDeleting(true);
            setError(null);
            await deleteApiKey(deletingAkCode);
            setShowDeleteDialog(false);
            fetchApiKeys(page, debouncedSearchQuery);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('删除 API Key 失败'));
            setShowDeleteDialog(false);
        } finally {
            setDeleting(false);
            setDeletingAkCode("");
        }
    }, [deletingAkCode, page, debouncedSearchQuery, fetchApiKeys]);

    // 编辑名称 / 服务名
    const handleEditName = useCallback((code: string, currentName: string) => {
        setEditingCode(code);
        setEditingValue(currentName);
        setEditingField('name');
    }, []);

    const handleEditService = useCallback((code: string, currentServiceId: string) => {
        setEditingCode(code);
        setEditingValue(currentServiceId);
        setEditingField('service');
    }, []);

    const handleEditClose = useCallback(() => {
        setEditingField(null);
        setEditingCode("");
        setEditingValue("");
    }, []);

    const handleEditConfirm = useCallback(async () => {
        if (!editingCode || !editingField) return;
        try {
            setEditSubmitting(true);
            if (editingField === 'name') {
                await renameApiKey(editingCode, editingValue);
            } else {
                await bindApiKeyService(editingCode, editingValue);
            }
            handleEditClose();
            fetchApiKeys(page, debouncedSearchQuery);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('修改失败'));
        } finally {
            setEditSubmitting(false);
        }
    }, [editingCode, editingField, editingValue, handleEditClose, fetchApiKeys, page, debouncedSearchQuery]);

    // 月额度
    const handleEditQuotaClick = useCallback((code: string, currentQuota: number) => {
        setQuotaEditingCode(code);
        setQuotaEditingValue(currentQuota.toString());
        setShowQuotaDialog(true);
    }, []);

    const handleQuotaClose = useCallback(() => {
        setShowQuotaDialog(false);
        setQuotaEditingCode("");
        setQuotaEditingValue("");
    }, []);

    const handleQuotaConfirm = useCallback(async () => {
        if (!quotaEditingCode) return;
        const quota = Number(quotaEditingValue);
        if (isNaN(quota) || quota <= 0) return;
        try {
            setQuotaSubmitting(true);
            await updateApiKeyQuota(quotaEditingCode, quota);
            handleQuotaClose();
            fetchApiKeys(page, debouncedSearchQuery);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('修改额度失败'));
        } finally {
            setQuotaSubmitting(false);
        }
    }, [quotaEditingCode, quotaEditingValue, handleQuotaClose, fetchApiKeys, page, debouncedSearchQuery]);

    // 安全等级
    const handleEditSafetyLevelClick = useCallback((akCode: string) => {
        setEditingAkCode(akCode);
        setShowSafeLevelDialog(true);
    }, []);

    const handleSafeLevelUpdateSuccess = useCallback(() => {
        fetchApiKeys(page, debouncedSearchQuery);
    }, [page, debouncedSearchQuery, fetchApiKeys]);

    // 创建 AK 成功：展示新密钥弹窗，关闭后刷新列表
    const handleCreateSuccess = useCallback((newCode: string) => {
        setNewApiKey(newCode);
        setShowCreatedDialog(true);
    }, []);

    const handleCreatedDialogClose = useCallback(() => {
        setShowCreatedDialog(false);
        setNewApiKey("");
        fetchApiKeys(page, debouncedSearchQuery);
    }, [fetchApiKeys, page, debouncedSearchQuery]);

    const handleOpenAction = useCallback((action: ApiKeyAdminAction, apiKey: ApikeyInfo) => {
        setActiveAction(action);
        setActiveApiKey(apiKey);
    }, []);

    const handleCloseAction = useCallback(() => {
        setActiveAction(null);
        setActiveApiKey(null);
    }, []);

    const handleGovernSuccess = useCallback(() => {
        fetchApiKeys(page, debouncedSearchQuery);
    }, [fetchApiKeys, page, debouncedSearchQuery]);

    return (
        <div>
            <TopBar title="API Key 管理（管理员）" description="查看并管理全量 API Keys" />
            <div className="p-8">
                {/* 顶部操作栏 */}
                <div className="flex items-center justify-between mb-4">
                    {/* 左侧：搜索维度切换 + 搜索框 + 所有者类型筛选 */}
                    <div className="flex items-center gap-2">
                        <Select value={searchType} onValueChange={handleSearchTypeChange}>
                            <SelectTrigger className="w-32">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ak">名称/服务名</SelectItem>
                                <SelectItem value="owner">所有者</SelectItem>
                                <SelectItem value="code">AK Code</SelectItem>
                                <SelectItem value="manager">管理人</SelectItem>
                            </SelectContent>
                        </Select>

                        <SearchInput
                            value={searchQuery}
                            onChange={(value) => {
                                setSearchQuery(value);
                                setPage(1);
                            }}
                            placeholder={
                                searchType === 'owner'   ? '搜索所有者名称或编码' :
                                searchType === 'code'    ? '输入完整 AK Code 精确查找' :
                                searchType === 'manager' ? '搜索管理人名称或编码' :
                                '搜索 API Key...'
                            }
                            isSearching={isSearching}
                        />

                        <Select value={ownerTypeFilter} onValueChange={(value) => {
                            setOwnerTypeFilter(value as 'all' | 'person' | 'org' | 'project');
                            setPage(1);
                        }}>
                            <SelectTrigger className="w-28">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="all">全部类型</SelectItem>
                                <SelectItem value="person">个人</SelectItem>
                                <SelectItem value="org">组织</SelectItem>
                                <SelectItem value="project">项目</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>

                    {/* 右侧：创建 AK 按钮 */}
                    <Button onClick={() => setShowCreateDialog(true)} className="flex items-center gap-2">
                        <Plus className="h-4 w-4" />
                        创建 AK
                    </Button>
                </div>

                {/* 错误提示 */}
                {error && (
                    <div className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 p-4">
                        <div className="flex items-center gap-2">
                            <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
                            <p className="text-sm text-red-500">{error.message}</p>
                        </div>
                    </div>
                )}

                {/* 表格容器 */}
                <div className="overflow-y-auto h-[calc(100vh-10rem)] [overscroll-behavior:contain]">
                    <AdminKeysTable
                        apiKeys={apiKeys}
                        balances={balances}
                        loading={loading}
                        searchQuery={debouncedSearchQuery}
                        isSuperAdmin={isSuperAdmin}
                        onCopy={handleCopy}
                        onOpenAction={handleOpenAction}
                        onDelete={handleDeleteClick}
                        onEditName={handleEditName}
                        onEditService={handleEditService}
                        onEditSafetyLevel={handleEditSafetyLevelClick}
                        onEditQuota={handleEditQuotaClick}
                    />

                    {!loading && apiKeys.length > 0 && (
                        <Pagination
                            currentPage={page}
                            hasMore={hasMore}
                            onPrevPage={handlePrevPage}
                            onNextPage={handleNextPage}
                            loading={loading}
                        />
                    )}
                </div>

                {/* 转交对话框 */}
                <ApiKeyTransferDialog
                    isOpen={activeAction === 'transfer'}
                    onClose={handleCloseAction}
                    apiKeyName={activeApiKey?.name}
                    apiKeyCode={activeApiKey?.code}
                    akDisplay={activeApiKey?.akDisplay}
                />

                {/* 删除对话框 */}
                <ApiKeyDeleteDialog
                    isOpen={showDeleteDialog}
                    onClose={() => setShowDeleteDialog(false)}
                    onConfirm={handleDeleteConfirm}
                    loading={deleting}
                />

                {/* 编辑名称 / 服务名弹窗 */}
                <Dialog open={!!editingField} onOpenChange={handleEditClose}>
                    <DialogContent className="max-w-sm">
                        <DialogHeader>
                            <DialogTitle>{editingField === 'name' ? '修改名称' : '修改服务名'}</DialogTitle>
                        </DialogHeader>
                        <Input
                            value={editingValue}
                            onChange={(e) => setEditingValue(e.target.value)}
                            placeholder={editingField === 'name' ? '请输入名称' : '请输入服务名'}
                            onKeyDown={(e) => e.key === 'Enter' && handleEditConfirm()}
                        />
                        <DialogFooter>
                            <Button variant="outline" onClick={handleEditClose} disabled={editSubmitting}>取消</Button>
                            <Button onClick={handleEditConfirm} disabled={editSubmitting}>
                                {editSubmitting ? '保存中...' : '确认'}
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>

                {/* 安全等级编辑对话框 */}
                <UpdateSafeLevel
                    isOpen={showSafeLevelDialog}
                    onClose={() => setShowSafeLevelDialog(false)}
                    akCode={editingAkCode}
                    onSuccess={handleSafeLevelUpdateSuccess}
                />

                {/* 设置管理人弹窗 */}
                <ManagerDialog
                    isOpen={activeAction === 'manager'}
                    onClose={handleCloseAction}
                    akCode={activeApiKey?.code ?? ""}
                    akDisplay={activeApiKey?.akDisplay}
                    onSuccess={handleGovernSuccess}
                    excludeSelf={false}
                    showSyncChildrenOption={!!activeApiKey && !activeApiKey.parentCode}
                />

                <OwnerChangeDialog
                    isOpen={activeAction === 'ownerChange'}
                    apiKey={activeApiKey}
                    onClose={handleCloseAction}
                    onSuccess={handleGovernSuccess}
                />

                <ParentChangeDialog
                    isOpen={activeAction === 'parentChange'}
                    apiKey={activeApiKey}
                    onClose={handleCloseAction}
                    onSuccess={handleGovernSuccess}
                />

                <ApiKeyHistoryDialog
                    isOpen={activeAction === 'history'}
                    apiKey={activeApiKey}
                    onClose={handleCloseAction}
                />

                {/* 创建 AK 弹窗 */}
                <AdminCreateDialog
                    isOpen={showCreateDialog}
                    onClose={() => setShowCreateDialog(false)}
                    onSuccess={handleCreateSuccess}
                />

                {/* 创建成功后展示新密钥 */}
                <ApiKeyCreatedDialog
                    apiKey={newApiKey}
                    isOpen={showCreatedDialog}
                    onClose={handleCreatedDialogClose}
                    onCopy={handleCopy}
                />

                {/* 月额度编辑弹窗 */}
                <Dialog open={showQuotaDialog} onOpenChange={handleQuotaClose}>
                    <DialogContent className="max-w-sm">
                        <DialogHeader>
                            <DialogTitle>修改月额度</DialogTitle>
                        </DialogHeader>
                        <Input
                            type="number"
                            min={1}
                            value={quotaEditingValue}
                            onChange={(e) => setQuotaEditingValue(e.target.value)}
                            placeholder="请输入月额度"
                            onKeyDown={(e) => e.key === 'Enter' && handleQuotaConfirm()}
                        />
                        <DialogFooter>
                            <Button variant="outline" onClick={handleQuotaClose} disabled={quotaSubmitting}>取消</Button>
                            <Button onClick={handleQuotaConfirm} disabled={quotaSubmitting || !quotaEditingValue || Number(quotaEditingValue) <= 0}>
                                {quotaSubmitting ? '保存中...' : '确认'}
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            </div>
        </div>
    );
}
