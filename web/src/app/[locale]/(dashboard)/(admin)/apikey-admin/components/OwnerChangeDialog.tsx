'use client'

import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Input } from "@/components/common/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";
import { Textarea } from "@/components/common/textarea";
import { ApikeyInfo, UserSearchResult } from "@/lib/types/apikeys";
import { changeApiKeyOwner, searchUserInfo } from "@/lib/api/apiKeys";
import { toast } from "sonner";

interface OwnerChangeDialogProps {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
    onClose: () => void;
    onSuccess: () => void;
}

type TargetOwnerType = 'person' | 'org' | 'project';

export function OwnerChangeDialog({ isOpen, apiKey, onClose, onSuccess }: OwnerChangeDialogProps) {
    const [targetOwnerType, setTargetOwnerType] = useState<TargetOwnerType>('org');
    const [targetOwnerCode, setTargetOwnerCode] = useState('');
    const [targetOwnerName, setTargetOwnerName] = useState('');
    const [ownerSearchQuery, setOwnerSearchQuery] = useState('');
    const [ownerSearchResults, setOwnerSearchResults] = useState<UserSearchResult[]>([]);
    const [selectedOwnerUser, setSelectedOwnerUser] = useState<UserSearchResult | null>(null);
    const [isSearchingOwner, setIsSearchingOwner] = useState(false);
    const [ownerSearchError, setOwnerSearchError] = useState('');
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const deferredOwnerSearchQuery = useDeferredValue(ownerSearchQuery);

    useEffect(() => {
        if (!isOpen || !apiKey) {
            setTargetOwnerType('org');
            setTargetOwnerCode('');
            setTargetOwnerName('');
            setOwnerSearchQuery('');
            setOwnerSearchResults([]);
            setSelectedOwnerUser(null);
            setIsSearchingOwner(false);
            setOwnerSearchError('');
            setReason('');
            setSubmitting(false);
            return;
        }
        setTargetOwnerType(apiKey.ownerType === 'project' ? 'project' : 'org');
        setTargetOwnerCode('');
        setTargetOwnerName('');
        setOwnerSearchQuery('');
        setOwnerSearchResults([]);
        setSelectedOwnerUser(null);
        setIsSearchingOwner(false);
        setOwnerSearchError('');
        setReason('');
        setSubmitting(false);
    }, [isOpen, apiKey]);

    useEffect(() => {
        if (!isOpen || targetOwnerType !== 'person') {
            setOwnerSearchResults([]);
            setIsSearchingOwner(false);
            setOwnerSearchError('');
            return;
        }
        if (deferredOwnerSearchQuery.trim().length < 1) {
            setOwnerSearchResults([]);
            setIsSearchingOwner(false);
            setOwnerSearchError('');
            return;
        }

        setIsSearchingOwner(true);
        setOwnerSearchError('');
        const timer = setTimeout(async () => {
            try {
                const results = await searchUserInfo(deferredOwnerSearchQuery, false);
                setOwnerSearchResults(results);
            } catch {
                setOwnerSearchError('搜索失败，请重试');
            } finally {
                setIsSearchingOwner(false);
            }
        }, 500);

        return () => clearTimeout(timer);
    }, [deferredOwnerSearchQuery, isOpen, targetOwnerType]);

    const handleTargetOwnerTypeChange = (value: string) => {
        setTargetOwnerType(value as TargetOwnerType);
        setTargetOwnerCode('');
        setTargetOwnerName('');
        setOwnerSearchQuery('');
        setOwnerSearchResults([]);
        setSelectedOwnerUser(null);
        setOwnerSearchError('');
    };

    const ownerTypeLabel = (ownerType?: string) => {
        if (ownerType === 'person') return '个人';
        if (ownerType === 'org') return '组织';
        if (ownerType === 'project') return '项目';
        return ownerType || '-';
    };

    const previewOwnerCode = targetOwnerType === 'person'
        ? selectedOwnerUser?.sourceId || ''
        : targetOwnerCode.trim() || apiKey?.ownerCode || '';
    const previewOwnerName = targetOwnerType === 'person'
        ? selectedOwnerUser?.userName || ''
        : targetOwnerName.trim() || apiKey?.ownerName || '';

    const hasActualChange = useMemo(() => {
        if (!apiKey) return false;
        if (targetOwnerType === 'person' && !selectedOwnerUser) return false;
        return apiKey.ownerType !== targetOwnerType
            || apiKey.ownerCode !== previewOwnerCode
            || apiKey.ownerName !== previewOwnerName;
    }, [apiKey, previewOwnerCode, previewOwnerName, selectedOwnerUser, targetOwnerType]);

    const handleSubmit = async () => {
        if (!apiKey || !hasActualChange) return;
        try {
            setSubmitting(true);
            await changeApiKeyOwner({
                code: apiKey.code,
                targetOwnerType,
                ...(previewOwnerCode ? { targetOwnerCode: previewOwnerCode } : {}),
                ...(previewOwnerName ? { targetOwnerName: previewOwnerName } : {}),
                ...(reason.trim() ? { reason: reason.trim() } : {}),
            });
            toast.success('所有者变更成功');
            onSuccess();
            onClose();
        } catch (error) {
            toast.error(error instanceof Error ? error.message : '所有者变更失败');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>变更所有者</DialogTitle>
                    <DialogDescription className="break-words">
                        将 <span className="font-medium text-foreground break-all">{apiKey?.akDisplay || '-'}</span> 的 owner 调整为个人、组织或项目。
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4">
                    <div className="rounded-md border bg-muted/30 p-3 text-sm">
                        <div className="break-words">
                            当前所有者：<span className="break-all">{apiKey?.ownerName || '-'}</span>（{ownerTypeLabel(apiKey?.ownerType)}）
                        </div>
                        <div className="text-muted-foreground break-words">
                            编码：<span className="break-all">{apiKey?.ownerCode || '-'}</span>
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium">目标类型</label>
                        <Select value={targetOwnerType} onValueChange={handleTargetOwnerTypeChange}>
                            <SelectTrigger>
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="person">个人</SelectItem>
                                <SelectItem value="org">组织</SelectItem>
                                <SelectItem value="project">项目</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>

                    {targetOwnerType === 'person' ? (
                        <div className="space-y-2">
                            <label className="text-sm font-medium">目标个人</label>
                            {selectedOwnerUser ? (
                                <div className="flex items-center justify-between rounded-md border bg-muted/40 p-3">
                                    <div className="min-w-0">
                                        <div className="truncate text-sm font-medium">{selectedOwnerUser.userName}</div>
                                        <div className="truncate text-xs text-muted-foreground">{selectedOwnerUser.email}</div>
                                        <div className="truncate text-xs text-muted-foreground">编码: {selectedOwnerUser.sourceId}</div>
                                    </div>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            setSelectedOwnerUser(null);
                                            setOwnerSearchQuery('');
                                            setOwnerSearchResults([]);
                                        }}
                                    >
                                        重新选择
                                    </Button>
                                </div>
                            ) : (
                                <>
                                    <Input
                                        value={ownerSearchQuery}
                                        onChange={(e) => setOwnerSearchQuery(e.target.value)}
                                        placeholder="搜索用户名、邮箱或ID"
                                    />
                                    <div className="min-h-[96px] max-h-[220px] overflow-y-auto rounded-md border p-3">
                                        {isSearchingOwner ? (
                                            <div className="flex h-16 items-center justify-center text-sm text-muted-foreground">搜索中...</div>
                                        ) : ownerSearchError ? (
                                            <div className="flex h-16 items-center justify-center text-sm text-destructive">{ownerSearchError}</div>
                                        ) : ownerSearchResults.length > 0 ? (
                                            <div className="space-y-2">
                                                {ownerSearchResults.map((user) => (
                                                    <button
                                                        key={user.id}
                                                        type="button"
                                                        className="w-full rounded border border-border/50 p-2 text-left hover:bg-accent"
                                                        onClick={() => {
                                                            setSelectedOwnerUser(user);
                                                            setOwnerSearchQuery('');
                                                            setOwnerSearchResults([]);
                                                        }}
                                                    >
                                                        <div className="text-sm font-medium">{user.userName}</div>
                                                        <div className="text-xs text-muted-foreground">{user.email}</div>
                                                        <div className="text-xs text-muted-foreground">编码: {user.sourceId}</div>
                                                    </button>
                                                ))}
                                            </div>
                                        ) : deferredOwnerSearchQuery.trim().length >= 1 ? (
                                            <div className="flex h-16 items-center justify-center text-sm text-muted-foreground">未找到匹配的用户</div>
                                        ) : (
                                            <div className="flex h-16 items-center justify-center text-sm text-muted-foreground">请选择目标个人</div>
                                        )}
                                    </div>
                                </>
                            )}
                        </div>
                    ) : (
                        <>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">目标编码</label>
                                <Input
                                    value={targetOwnerCode}
                                    onChange={(e) => setTargetOwnerCode(e.target.value)}
                                    placeholder="留空则沿用当前 ownerCode"
                                />
                            </div>

                            <div className="space-y-2">
                                <label className="text-sm font-medium">目标名称</label>
                                <Input
                                    value={targetOwnerName}
                                    onChange={(e) => setTargetOwnerName(e.target.value)}
                                    placeholder="留空则沿用当前 ownerName"
                                />
                            </div>
                        </>
                    )}

                    <div className="space-y-2">
                        <label className="text-sm font-medium">变更原因</label>
                        <Textarea
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            placeholder="选填，建议记录本次治理背景"
                            className="min-h-[88px]"
                        />
                    </div>

                    <div className="rounded-md border border-primary/20 bg-primary/5 p-3 text-sm">
                        <div className="font-medium">变更预览</div>
                        <div className="mt-1">类型：{ownerTypeLabel(targetOwnerType)}</div>
                        <div className="break-words">
                            编码：<span className="break-all">{previewOwnerCode || '-'}</span>
                        </div>
                        <div className="break-words">
                            名称：<span className="break-all">{previewOwnerName || '-'}</span>
                        </div>
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose} disabled={submitting}>取消</Button>
                    <Button onClick={handleSubmit} disabled={submitting || !hasActualChange}>
                        {submitting ? '提交中...' : '确认变更'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
