'use client'

/**
 * ManagerPage — 统一 AK 管理页面
 *
 * 职责：
 *   - 提供 managerCode（当前登录用户ID）给 ManagedKeysTable
 *   - 管理重置 AK 的弹窗状态
 *   - 重置成功后通过递增 refreshToken 通知各 Section 刷新
 *   - 数据获取/搜索/分页完全由 ManagedKeysTable 内部各 Section 自治
 *
 * 防 re-render：
 *   - handleCopy / handleResetClick / handleResetConfirm / handleCreatedDialogClose 均 useCallback 包裹
 *   - refreshToken 仅在重置成功后递增，不影响其他渲染路径
 */

import { TopBar } from "@/components/layout/top-bar";
import { AlertCircle } from "lucide-react";
import { useState, useCallback } from "react";
import { bindApiKeyService, deleteApiKey, renameApiKey, resetApiKey } from "@/lib/api/apiKeys";
import { Button } from "@/components/common/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Input } from "@/components/common/input";
import { ManagedKeysTable } from "./components/ManagedKeysTable";
import { CreateApiKeyApplyButton } from "./components/CreateApiKeyApplyButton";
import { ApiKeyResetDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyResetDialog";
import { ApiKeyCreatedDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyCreatedDialog";
import { UpdateSafeLevel } from "@/app/[locale]/(dashboard)/apikey/components/UpdateSafeLevel";
import { ManagerDialog } from "@/app/[locale]/(dashboard)/apikey/components/ManagerDialog";
import { ApiKeyDeleteDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyDeleteDialog";
import { ApiKeyHistoryDialog } from "@/app/[locale]/(dashboard)/(admin)/apikey-admin/components/ApiKeyHistoryDialog";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { toast } from "sonner";
import { useAuth } from "@/components/providers/auth-provider";

type ManagedEditableField = 'name' | 'serviceId';

const MANAGED_FIELD_LABELS: Record<ManagedEditableField, string> = {
    name: '名称',
    serviceId: '服务名',
};

export default function ManagerPage() {
    const { user } = useAuth();
    const managerCode = user?.userId?.toString() ?? "";

    // 重置 AK 相关（我管理的 AK / 分配给我的子 AK 共用）
    const [showResetDialog, setShowResetDialog] = useState(false);
    const [resetingAkCode, setResetingAkCode] = useState<string>("");
    const [resetting, setResetting] = useState(false);
    const [showCreatedDialog, setShowCreatedDialog] = useState(false);
    const [newApiKey, setNewApiKey] = useState("");
    const [showSafeLevelDialog, setShowSafeLevelDialog] = useState(false);
    const [editingAkCode, setEditingAkCode] = useState<string>("");
    const [showManagerDialog, setShowManagerDialog] = useState(false);
    const [managerTargetAk, setManagerTargetAk] = useState<ApikeyInfo | null>(null);
    const [showHistoryDialog, setShowHistoryDialog] = useState(false);
    const [historyApiKey, setHistoryApiKey] = useState<ApikeyInfo | null>(null);
    const [editingApiKey, setEditingApiKey] = useState<ApikeyInfo | null>(null);
    const [editingField, setEditingField] = useState<ManagedEditableField | null>(null);
    const [editingValue, setEditingValue] = useState("");
    const [editingError, setEditingError] = useState("");
    const [editingSubmitting, setEditingSubmitting] = useState(false);
    const [deletingApiKey, setDeletingApiKey] = useState<ApikeyInfo | null>(null);
    const [deleting, setDeleting] = useState(false);

    // 递增此值通知 Section 刷新，避免将整个 fetchData 提升到 page 层
    const [refreshToken, setRefreshToken] = useState(0);

    const [error, setError] = useState<Error | null>(null);

    const handleCopy = useCallback(async (text: string) => {
        const success = await copyToClipboard(text);
        if (success) {
            toast.success('复制成功');
        } else {
            toast.error('复制失败，请手动复制');
        }
    }, []);

    // 处理重置点击
    const handleResetClick = useCallback((akCode: string) => {
        setResetingAkCode(akCode);
        setShowResetDialog(true);
    }, []);

    // 处理重置确认
    const handleResetConfirm = useCallback(async () => {
        if (!resetingAkCode) return;
        try {
            setResetting(true);
            setError(null);
            const newKey = await resetApiKey(resetingAkCode);
            setShowResetDialog(false);
            if (newKey) {
                setNewApiKey(newKey);
                setShowCreatedDialog(true);
            }
        } catch (err) {
            setError(err instanceof Error ? err : new Error('重置失败，请重试'));
            toast.error('重置失败，请重试');
            setShowResetDialog(false);
        } finally {
            setResetting(false);
            setResetingAkCode("");
        }
    }, [resetingAkCode]);

    const handlePersonalCreated = useCallback((apiKey: string) => {
        setNewApiKey(apiKey);
        setShowCreatedDialog(true);
    }, []);

    // 创建/重置成功弹窗关闭：递增 refreshToken 触发各 Section 刷新
    const handleCreatedDialogClose = useCallback(() => {
        setShowCreatedDialog(false);
        setNewApiKey("");
        setRefreshToken(t => t + 1);
    }, []);

    const handleEditSafetyLevelClick = useCallback((akCode: string) => {
        setEditingAkCode(akCode);
        setShowSafeLevelDialog(true);
    }, []);

    const handleSafeLevelUpdateSuccess = useCallback(() => {
        setRefreshToken(t => t + 1);
    }, []);

    const handleSetManagerClick = useCallback((apiKey: ApikeyInfo) => {
        setManagerTargetAk(apiKey);
        setShowManagerDialog(true);
    }, []);

    const handleManagerDialogClose = useCallback(() => {
        setShowManagerDialog(false);
        setManagerTargetAk(null);
    }, []);

    const handleManagerUpdateSuccess = useCallback(() => {
        setRefreshToken(t => t + 1);
    }, []);

    const handleViewHistory = useCallback((apiKey: ApikeyInfo) => {
        setHistoryApiKey(apiKey);
        setShowHistoryDialog(true);
    }, []);

    const handleEditField = useCallback((apiKey: ApikeyInfo, field: ManagedEditableField) => {
        setEditingApiKey(apiKey);
        setEditingField(field);
        setEditingValue(field === 'name' ? apiKey.name || '' : apiKey.serviceId || '');
        setEditingError('');
    }, []);

    const handleEditClose = useCallback(() => {
        setEditingApiKey(null);
        setEditingField(null);
        setEditingValue('');
        setEditingError('');
    }, []);

    const handleEditConfirm = useCallback(async () => {
        if (!editingApiKey || !editingField) return;
        const value = editingValue.trim();
        if (!value) {
            setEditingError(`请输入${MANAGED_FIELD_LABELS[editingField]}`);
            return;
        }

        try {
            setEditingSubmitting(true);
            setEditingError('');
            if (editingField === 'name') {
                await renameApiKey(editingApiKey.code, value);
            } else {
                await bindApiKeyService(editingApiKey.code, value);
            }
            handleEditClose();
            setRefreshToken(token => token + 1);
            toast.success(`${MANAGED_FIELD_LABELS[editingField]}修改成功`);
        } catch (err) {
            setEditingError(err instanceof Error ? err.message : '修改失败，请重试');
        } finally {
            setEditingSubmitting(false);
        }
    }, [editingApiKey, editingField, editingValue, handleEditClose]);

    const handleDeleteConfirm = useCallback(async () => {
        if (!deletingApiKey) return;

        try {
            setDeleting(true);
            setError(null);
            await deleteApiKey(deletingApiKey.code);
            setDeletingApiKey(null);
            setRefreshToken(token => token + 1);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('删除 API Key 失败'));
            setDeletingApiKey(null);
        } finally {
            setDeleting(false);
        }
    }, [deletingApiKey]);

    return (
        <div>
            <TopBar
                title="AK 管理"
                description="统一查看并管理由我负责的父 AK，以及分配给我负责的子 AK"
                action={<CreateApiKeyApplyButton onPersonalCreated={handlePersonalCreated} />}
            />
            <div className="p-8">
                {/* 错误提示 */}
                {error && (
                    <div className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 p-4">
                        <div className="flex items-center gap-2">
                            <AlertCircle className="h-5 w-5 shrink-0 text-red-500" />
                            <p className="text-sm text-red-500">{error.message}</p>
                        </div>
                    </div>
                )}


                {/* 表格容器：各 Section 内部自带搜索 + 后端分页 */}
                <div className="overflow-y-auto h-[calc(100vh-14rem)] overscroll-contain">
                    <ManagedKeysTable
                        managerCode={managerCode}
                        onCopy={handleCopy}
                        onReset={handleResetClick}
                        onEditSafetyLevel={handleEditSafetyLevelClick}
                        onSetManager={handleSetManagerClick}
                        onViewHistory={handleViewHistory}
                        onEditName={(apiKey) => handleEditField(apiKey, 'name')}
                        onEditService={(apiKey) => handleEditField(apiKey, 'serviceId')}
                        onDelete={setDeletingApiKey}
                        refreshToken={refreshToken}
                    />
                </div>

                {/* 重置确认对话框 */}
                <ApiKeyResetDialog
                    isOpen={showResetDialog}
                    onClose={() => setShowResetDialog(false)}
                    onConfirm={handleResetConfirm}
                    loading={resetting}
                />

                {/* 重置成功展示新密钥 */}
                <ApiKeyCreatedDialog
                    apiKey={newApiKey}
                    isOpen={showCreatedDialog}
                    onClose={handleCreatedDialogClose}
                    onCopy={handleCopy}
                />

                {/* 安全等级编辑对话框 */}
                <UpdateSafeLevel
                    isOpen={showSafeLevelDialog}
                    onClose={() => setShowSafeLevelDialog(false)}
                    akCode={editingAkCode}
                    onSuccess={handleSafeLevelUpdateSuccess}
                />

                {/* 管理权转移 */}
                <ManagerDialog
                    isOpen={showManagerDialog}
                    onClose={handleManagerDialogClose}
                    akCode={managerTargetAk?.code ?? ""}
                    akDisplay={managerTargetAk?.akDisplay}
                    onSuccess={handleManagerUpdateSuccess}
                    excludeSelf={false}
                    showReason
                    reasonRequired
                    showSyncChildrenOption={!!managerTargetAk && !managerTargetAk.parentCode}
                />

                <Dialog open={!!editingApiKey && !!editingField} onOpenChange={(open) => !open && handleEditClose()}>
                    <DialogContent className="max-w-sm">
                        <DialogHeader>
                            <DialogTitle>修改{editingField ? MANAGED_FIELD_LABELS[editingField] : ''}</DialogTitle>
                        </DialogHeader>
                        <Input
                            value={editingValue}
                            onChange={(event) => {
                                setEditingValue(event.target.value);
                                setEditingError('');
                            }}
                            placeholder={editingField ? `请输入${MANAGED_FIELD_LABELS[editingField]}` : ''}
                            onKeyDown={(event) => event.key === 'Enter' && handleEditConfirm()}
                            maxLength={64}
                            autoFocus
                        />
                        {editingError && <p className="text-xs text-red-500">{editingError}</p>}
                        <DialogFooter>
                            <Button variant="outline" onClick={handleEditClose} disabled={editingSubmitting}>取消</Button>
                            <Button onClick={handleEditConfirm} disabled={editingSubmitting || !editingValue.trim()}>
                                {editingSubmitting ? '保存中...' : '确认'}
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>

                <ApiKeyDeleteDialog
                    isOpen={!!deletingApiKey}
                    onClose={() => setDeletingApiKey(null)}
                    onConfirm={handleDeleteConfirm}
                    loading={deleting}
                />

                {/* 受托父 AK 变更历史 */}
                <ApiKeyHistoryDialog
                    isOpen={showHistoryDialog}
                    apiKey={historyApiKey}
                    onClose={() => setShowHistoryDialog(false)}
                />
            </div>
        </div>
    );
}
