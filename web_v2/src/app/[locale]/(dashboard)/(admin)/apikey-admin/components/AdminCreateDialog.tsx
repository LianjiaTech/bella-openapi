'use client'

/**
 * AdminCreateDialog — 管理员创建顶层 AK 弹窗
 *
 * 职责：
 *   - 支持创建 org / project 类型的顶层 AK（个人AK由普通用户自行申请）
 *   - 表单字段：所有者类型、所有者名称、AK名称、月额度、备注
 *   - 管理人：复用用户搜索逻辑（useDeferredValue + 500ms防抖 + searchUserInfo）
 *   - 提交：adminApplyApiKey → onSuccess(newAkCode) → 父组件展示新密钥并刷新列表
 *
 * 防 re-render：
 *   - onSuccess/onClose 由父组件 useCallback 传入，引用稳定
 *   - 内部 state 全部在弹窗关闭时重置，不污染下次打开
 *   - 管理人搜索 state 独立于表单 state，互不影响
 */

import { useState, useEffect, useDeferredValue, useCallback } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Search, X } from "lucide-react";
import { adminApplyApiKey, searchUserInfo } from "@/lib/api/apiKeys";
import { UserSearchResult } from "@/lib/types/apikeys";
import { toast } from "sonner";

/** 所有者类型选项：管理员可创建 org / project 两种顶层 AK */
const OWNER_TYPE_OPTIONS = [
    { value: 'org', label: '组织' },
    { value: 'project', label: '项目' },
];

interface AdminCreateDialogProps {
    isOpen: boolean;
    onClose: () => void;
    /** 创建成功回调，传入新生成的 AK Code，父组件可弹窗展示并刷新列表 */
    onSuccess: (newAkCode: string) => void;
}

export function AdminCreateDialog({ isOpen, onClose, onSuccess }: AdminCreateDialogProps) {
    // ── 表单字段 ──────────────────────────────────────────────────────────
    const [ownerType, setOwnerType] = useState<string>('org');
    const [ownerCode, setOwnerCode] = useState('');
    const [ownerName, setOwnerName] = useState('');
    const [name, setName] = useState('');
    const [monthQuota, setMonthQuota] = useState<string>('50');
    const [remark, setRemark] = useState('');

    // ── 管理人搜索 ────────────────────────────────────────────────────────
    const [managerSearchQuery, setManagerSearchQuery] = useState('');
    const [managerSearchResults, setManagerSearchResults] = useState<UserSearchResult[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [searchError, setSearchError] = useState('');
    /** 已选中的管理人，选中后显示 tag，可清除 */
    const [selectedManager, setSelectedManager] = useState<UserSearchResult | null>(null);

    // ── 提交状态 ──────────────────────────────────────────────────────────
    const [submitting, setSubmitting] = useState(false);

    const deferredManagerSearch = useDeferredValue(managerSearchQuery);

    // 弹窗关闭时重置所有状态
    useEffect(() => {
        if (!isOpen) {
            setOwnerType('org');
            setOwnerCode('');
            setOwnerName('');
            setName('');
            setMonthQuota('50');
            setRemark('');
            setManagerSearchQuery('');
            setManagerSearchResults([]);
            setSearchError('');
            setSelectedManager(null);
            setSubmitting(false);
        }
    }, [isOpen]);

    // 管理人搜索防抖 500ms
    useEffect(() => {
        if (deferredManagerSearch.trim().length < 1) {
            setManagerSearchResults([]);
            setIsSearching(false);
            return;
        }
        setIsSearching(true);
        setSearchError('');
        const timer = setTimeout(async () => {
            try {
                const results = await searchUserInfo(deferredManagerSearch, false);
                setManagerSearchResults(results);
            } catch {
                setSearchError('搜索失败，请重试');
            } finally {
                setIsSearching(false);
            }
        }, 500);
        return () => clearTimeout(timer);
    }, [deferredManagerSearch]);

    const handleSelectManager = useCallback((user: UserSearchResult) => {
        setSelectedManager(user);
        setManagerSearchQuery('');
        setManagerSearchResults([]);
    }, []);

    const handleClearManager = useCallback(() => {
        setSelectedManager(null);
    }, []);

    const handleSubmit = useCallback(async () => {
        if (!ownerCode.trim()) {
            toast.error('请填写所有者编码');
            return;
        }
        if (!ownerName.trim()) {
            toast.error('请填写所有者名称');
            return;
        }
        const quota = Number(monthQuota);
        if (isNaN(quota) || quota <= 0) {
            toast.error('月额度须为正数');
            return;
        }
        try {
            setSubmitting(true);
            const newCode = await adminApplyApiKey({
                ownerType,
                ownerCode: ownerCode.trim(),
                ownerName: ownerName.trim(),
                ...(name.trim() ? { name: name.trim() } : {}),
                monthQuota: quota,
                ...(remark.trim() ? { remark: remark.trim() } : {}),
                // 临时补丁：创建接口使用 sourceId 作为 managerCode（apply 接口直接存值，无推导逻辑）
                // TODO: 待后端 ApplyOp 支持 managerUserId 自动推导后，统一改为 user.id，与 updateManager 对齐，移除此补丁
                ...(selectedManager ? {
                    managerCode: selectedManager.sourceId,
                    managerName: selectedManager.userName,
                } : {}),
            });
            toast.success('AK 创建成功');
            onSuccess(newCode);
            onClose();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : '创建失败，请重试');
        } finally {
            setSubmitting(false);
        }
    }, [ownerType, ownerName, name, monthQuota, remark, selectedManager, onSuccess, onClose]);

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-lg flex flex-col max-h-[90vh]">
                <DialogHeader>
                    <DialogTitle>创建 AK</DialogTitle>
                </DialogHeader>

                <div className="space-y-4 py-2 overflow-y-auto flex-1 min-h-0">
                    {/* 所有者类型 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">所有者类型 <span className="text-destructive">*</span></label>
                        <div className="flex gap-2">
                            {OWNER_TYPE_OPTIONS.map(opt => (
                                <button
                                    key={opt.value}
                                    type="button"
                                    onClick={() => setOwnerType(opt.value)}
                                    className={`px-4 py-1.5 rounded-md text-sm border transition-colors ${
                                        ownerType === opt.value
                                            ? 'bg-primary text-primary-foreground border-primary'
                                            : 'bg-background border-input text-foreground hover:bg-accent'
                                    }`}
                                >
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* 所有者编码 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">
                            所有者编码 <span className="text-destructive">*</span>
                        </label>
                        <input
                            type="text"
                            placeholder={ownerType === 'org' ? '输入组织编码（唯一标识）' : '输入项目编码（唯一标识）'}
                            className="w-full px-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={ownerCode}
                            onChange={e => setOwnerCode(e.target.value)}
                        />
                    </div>

                    {/* 所有者名称 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">
                            所有者名称 <span className="text-destructive">*</span>
                        </label>
                        <input
                            type="text"
                            placeholder={ownerType === 'org' ? '输入组织名称' : '输入项目名称'}
                            className="w-full px-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={ownerName}
                            onChange={e => setOwnerName(e.target.value)}
                        />
                    </div>

                    {/* AK 名称 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">AK 名称</label>
                        <input
                            type="text"
                            placeholder="选填"
                            className="w-full px-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={name}
                            onChange={e => setName(e.target.value)}
                        />
                    </div>

                    {/* 月额度 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">月额度</label>
                        <input
                            type="number"
                            min={1}
                            placeholder="默认 50"
                            className="w-full px-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={monthQuota}
                            onChange={e => setMonthQuota(e.target.value)}
                        />
                    </div>

                    {/* 备注 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">备注</label>
                        <input
                            type="text"
                            placeholder="选填"
                            className="w-full px-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                            value={remark}
                            onChange={e => setRemark(e.target.value)}
                        />
                    </div>

                    {/* 管理人 */}
                    <div className="space-y-1">
                        <label className="text-sm font-medium">管理人</label>
                        {selectedManager ? (
                            // 已选中：展示 tag，可清除
                            <div className="flex items-center gap-2 px-3 py-2 border border-input rounded-md bg-accent/40">
                                <div className="flex-1 min-w-0">
                                    <span className="text-sm font-medium">{selectedManager.userName}</span>
                                    <span className="ml-2 text-xs text-muted-foreground">{selectedManager.email}</span>
                                </div>
                                <button
                                    type="button"
                                    onClick={handleClearManager}
                                    className="shrink-0 text-muted-foreground hover:text-foreground"
                                >
                                    <X className="h-4 w-4" />
                                </button>
                            </div>
                        ) : (
                            // 未选中：搜索框
                            <div className="space-y-2">
                                <div className="relative">
                                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                    <input
                                        type="text"
                                        placeholder="搜索用户名、邮箱或ID"
                                        className="w-full pl-10 pr-4 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-ring bg-background"
                                        value={managerSearchQuery}
                                        onChange={e => setManagerSearchQuery(e.target.value)}
                                    />
                                </div>
                                {/* 搜索结果下拉 */}
                                {(managerSearchQuery.trim().length >= 1) && (
                                    <div className="border border-border rounded-md overflow-hidden">
                                        {isSearching ? (
                                            <div className="px-3 py-4 text-sm text-muted-foreground text-center">搜索中...</div>
                                        ) : searchError ? (
                                            <div className="px-3 py-4 text-sm text-destructive text-center">{searchError}</div>
                                        ) : managerSearchResults.length > 0 ? (
                                            <div className="max-h-[160px] overflow-y-auto">
                                                {managerSearchResults.map(user => (
                                                    <div
                                                        key={user.id}
                                                        className="flex justify-between items-center px-3 py-2 hover:bg-accent cursor-pointer"
                                                        onClick={() => handleSelectManager(user)}
                                                    >
                                                        <div>
                                                            <div className="text-sm font-medium">{user.userName}</div>
                                                            <div className="text-xs text-muted-foreground">{user.email}</div>
                                                        </div>
                                                        <span className="text-xs text-primary">选择</span>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="px-3 py-4 text-sm text-muted-foreground text-center">未找到匹配用户</div>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>

                {/* 底部操作按钮 */}
                <div className="flex justify-end gap-2 pt-2 border-t">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={submitting}
                        className="px-4 py-2 text-sm border border-input rounded-md hover:bg-accent disabled:opacity-50"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        onClick={handleSubmit}
                        disabled={submitting || !ownerCode.trim() || !ownerName.trim()}
                        className="px-4 py-2 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {submitting ? '创建中...' : '创建'}
                    </button>
                </div>
            </DialogContent>
        </Dialog>
    );
}
