'use client'

/**
 * ManagerDialog — 设置 AK 管理人弹窗
 *
 * 职责：
 *   - 在普通用户的"我的AK"页面，提供为指定 AK 设置管理人的入口
 *   - 复用 UserSearchView 的搜索逻辑：useDeferredValue + 500ms 防抖 + searchUserInfo
 *   - 提交：点击用户行的"设置"按钮 → updateManager({ code, managerUserId }) → onSuccess + onClose
 *
 * 防 re-render：
 *   - onSuccess/onClose 由父组件 useCallback 传入，引用稳定
 *   - 内部搜索 state 独立，弹窗开关不向上传递 state，不影响父组件重渲染
 *   - submittingId 仅控制单行 loading 样式，不影响其他行
 */

import { useState, useEffect, useDeferredValue } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Textarea } from "@/components/common/textarea";
import { Switch } from "@/components/common/switch";
import { Search } from "lucide-react";
import { searchUserInfo, updateManager } from "@/lib/api/apiKeys";
import { UserSearchResult } from "@/lib/types/apikeys";
import { toast } from "sonner";

interface ManagerDialogProps {
    isOpen: boolean;
    onClose: () => void;
    akCode: string;
    akDisplay?: string;
    onSuccess: () => void;
    /** 搜索时是否排除自己，admin 场景可设置自己为管理人，默认 true */
    excludeSelf?: boolean;
    /** 是否展示管理者变更原因输入框 */
    showReason?: boolean;
    /** 是否要求提交前填写变更原因 */
    reasonRequired?: boolean;
    /** 是否展示同步子 AK 管理者的开关 */
    showSyncChildrenOption?: boolean;
    /** 同步子 AK 管理者开关的默认值 */
    defaultSyncChildren?: boolean;
}

export function ManagerDialog({
    isOpen,
    onClose,
    akCode,
    akDisplay,
    onSuccess,
    excludeSelf = true,
    showReason = false,
    reasonRequired = false,
    showSyncChildrenOption = false,
    defaultSyncChildren = true,
}: ManagerDialogProps) {
    const [searchQuery, setSearchQuery] = useState("");
    const [reason, setReason] = useState("");
    const shouldShowReason = showReason || reasonRequired;
    const [syncChildren, setSyncChildren] = useState(defaultSyncChildren);
    const [searchResults, setSearchResults] = useState<UserSearchResult[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [searchError, setSearchError] = useState("");
    // 记录当前正在提交的用户 ID，null 表示无提交中状态
    const [submittingId, setSubmittingId] = useState<number | null>(null);

    // useDeferredValue 降低高频输入时的渲染压力
    const deferredSearchQuery = useDeferredValue(searchQuery);

    // 关闭时重置搜索状态，避免下次打开残留上次数据
    useEffect(() => {
        if (!isOpen) {
            setSearchQuery("");
            setReason("");
            setSyncChildren(defaultSyncChildren);
            setSearchResults([]);
            setSearchError("");
            setSubmittingId(null);
        }
    }, [isOpen, defaultSyncChildren]);

    // 500ms 防抖搜索
    useEffect(() => {
        if (deferredSearchQuery.trim().length < 1) {
            setSearchResults([]);
            setIsSearching(false);
            return;
        }

        setIsSearching(true);
        setSearchError("");
        const timer = setTimeout(async () => {
            try {
                const results = await searchUserInfo(deferredSearchQuery, excludeSelf);
                setSearchResults(results);
            } catch {
                setSearchError("搜索失败，请重试");
            } finally {
                setIsSearching(false);
            }
        }, 500);

        return () => clearTimeout(timer);
    }, [deferredSearchQuery]);

    const handleSetManager = async (user: UserSearchResult) => {
        if (submittingId !== null) return;
        const trimmedReason = reason.trim();
        if (reasonRequired && !trimmedReason) {
            toast.error("请填写变更原因");
            return;
        }
        try {
            setSubmittingId(user.id);
            await updateManager({
                code: akCode,
                managerUserId: user.id,
                reason: trimmedReason || undefined,
                ...(showSyncChildrenOption ? { syncChildren } : {}),
            });
            toast.success(`已将 ${user.userName} 设置为管理人`);
            onSuccess();
            onClose();
        } catch (err) {
            const message = err instanceof Error ? err.message : '设置管理人失败';
            toast.error(message);
        } finally {
            setSubmittingId(null);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle>
                        设置管理人
                        {akDisplay && (
                            <span className="ml-2 text-sm font-normal text-muted-foreground">
                                {akDisplay}
                            </span>
                        )}
                    </DialogTitle>
                </DialogHeader>

                <div className="space-y-4">
                    {shouldShowReason && (
                        <div className="space-y-2">
                            <label className="text-sm font-medium">
                                变更原因{reasonRequired && <span className="ml-1 text-destructive">*</span>}
                            </label>
                            <Textarea
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                placeholder="请记录本次管理权变更原因"
                                className="min-h-[88px]"
                            />
                        </div>
                    )}

                    {showSyncChildrenOption && (
                        <div className="flex items-start justify-between gap-3 rounded-md border bg-muted/40 p-3">
                            <div className="space-y-1">
                                <label className="text-sm font-medium">同步更新子 AK 管理者</label>
                                <p className="text-xs text-muted-foreground">
                                    开启后，本次管理权变更会同步到该父 AK 下所有子 AK。
                                </p>
                            </div>
                            <Switch
                                checked={syncChildren}
                                onCheckedChange={setSyncChildren}
                                aria-label="同步更新子 AK 管理者"
                            />
                        </div>
                    )}

                    {/* 搜索输入框 */}
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="输入用户名、邮箱或ID"
                            className="w-full pl-10 pr-4 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            autoFocus
                        />
                    </div>

                    {/* 搜索结果区域 */}
                    <div className="min-h-[120px] max-h-[280px] overflow-y-auto border border-border rounded-md p-3">
                        {isSearching ? (
                            <div className="flex items-center justify-center h-20 text-sm text-muted-foreground">
                                搜索中...
                            </div>
                        ) : searchError ? (
                            <div className="flex items-center justify-center h-20 text-sm text-destructive">
                                {searchError}
                            </div>
                        ) : searchResults.length > 0 ? (
                            <div className="space-y-2">
                                {searchResults.map((user) => (
                                    <div
                                        key={user.id}
                                        className="flex justify-between items-center p-2 hover:bg-accent rounded cursor-pointer border border-border/50"
                                    >
                                        <div>
                                            <div className="text-sm font-medium">{user.userName}</div>
                                            <div className="text-xs text-muted-foreground">{user.email}</div>
                                            <div className="text-xs text-muted-foreground/70">ID: {user.sourceId}</div>
                                        </div>
                                        <button
                                            className="text-sm text-primary hover:underline disabled:opacity-50 disabled:cursor-not-allowed px-2"
                                            onClick={() => handleSetManager(user)}
                                            disabled={submittingId !== null || (reasonRequired && !reason.trim())}
                                        >
                                            {submittingId === user.id ? '设置中...' : '设置'}
                                        </button>
                                    </div>
                                ))}
                            </div>
                        ) : deferredSearchQuery.trim().length >= 1 ? (
                            <div className="flex items-center justify-center h-20 text-sm text-muted-foreground">
                                未找到匹配的用户
                            </div>
                        ) : searchQuery.trim().length > 0 ? (
                            <div className="flex items-center justify-center h-20 text-sm text-muted-foreground">
                                输入中...
                            </div>
                        ) : (
                            <div className="flex items-center justify-center h-20 text-sm text-muted-foreground">
                                请输入关键词开始搜索
                            </div>
                        )}
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}
