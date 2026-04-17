'use client'

/**
 * ManagerPage — "我管理的 AK" 页面
 *
 * 职责：
 *   - 提供 managerCode（当前登录用户ID）给 ManagedKeysTable
 *   - 管理重置子AK的弹窗状态（唯一写操作）
 *   - 重置成功后通过递增 refreshToken 通知 AssignedSection 刷新
 *   - 数据获取/搜索/分页完全由 ManagedKeysTable 内部各 Section 自治
 *
 * 防 re-render：
 *   - handleCopy / handleResetClick / handleResetConfirm / handleCreatedDialogClose 均 useCallback 包裹
 *   - refreshToken 仅在重置成功后递增，不影响其他渲染路径
 */

import { TopBar } from "@/components/layout/top-bar";
import { AlertCircle } from "lucide-react";
import { useState, useCallback } from "react";
import { resetApiKey } from "@/lib/api/apiKeys";
import { ManagedKeysTable } from "./components/ManagedKeysTable";
import { ApiKeyResetDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyResetDialog";
import { ApiKeyCreatedDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyCreatedDialog";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { toast } from "sonner";
import { useAuth } from "@/components/providers/auth-provider";

export default function ManagerPage() {
    const { user } = useAuth();
    const managerCode = user?.userId?.toString() ?? "";

    // 重置子AK相关（子AK分配给自己使用时的唯一写操作）
    const [showResetDialog, setShowResetDialog] = useState(false);
    const [resetingAkCode, setResetingAkCode] = useState<string>("");
    const [resetting, setResetting] = useState(false);
    const [showCreatedDialog, setShowCreatedDialog] = useState(false);
    const [newApiKey, setNewApiKey] = useState("");

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

    // 处理重置点击：仅对分配给自己的子AK生效
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

    // 重置成功弹窗关闭：递增 refreshToken 触发 AssignedSection 刷新
    const handleCreatedDialogClose = useCallback(() => {
        setShowCreatedDialog(false);
        setNewApiKey("");
        setRefreshToken(t => t + 1);
    }, []);

    return (
        <div>
            <TopBar title="组织/项目密钥" description="查看并管理组织或项目相关的密钥，包括受托管理的密钥和分配给我使用的子密钥" />
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
                <div className="overflow-y-auto h-[calc(100vh-10rem)] overscroll-contain">
                    <ManagedKeysTable
                        managerCode={managerCode}
                        onCopy={handleCopy}
                        onReset={handleResetClick}
                        refreshToken={refreshToken}
                    />
                </div>

                {/* 重置确认对话框（子AK重置） */}
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
            </div>
        </div>
    );
}
