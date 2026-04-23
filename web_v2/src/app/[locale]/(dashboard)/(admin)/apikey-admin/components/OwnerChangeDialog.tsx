'use client'

import { useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Input } from "@/components/common/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";
import { Textarea } from "@/components/common/textarea";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { changeApiKeyOwner } from "@/lib/api/apiKeys";
import { toast } from "sonner";

interface OwnerChangeDialogProps {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
    onClose: () => void;
    onSuccess: () => void;
}

export function OwnerChangeDialog({ isOpen, apiKey, onClose, onSuccess }: OwnerChangeDialogProps) {
    const [targetOwnerType, setTargetOwnerType] = useState<'org' | 'project'>('org');
    const [targetOwnerCode, setTargetOwnerCode] = useState('');
    const [targetOwnerName, setTargetOwnerName] = useState('');
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!isOpen || !apiKey) {
            setTargetOwnerType('org');
            setTargetOwnerCode('');
            setTargetOwnerName('');
            setReason('');
            setSubmitting(false);
            return;
        }
        setTargetOwnerType(apiKey.ownerType === 'project' ? 'project' : 'org');
        setTargetOwnerCode('');
        setTargetOwnerName('');
        setReason('');
        setSubmitting(false);
    }, [isOpen, apiKey]);

    const previewOwnerCode = targetOwnerCode.trim() || apiKey?.ownerCode || '';
    const previewOwnerName = targetOwnerName.trim() || apiKey?.ownerName || '';

    const hasActualChange = useMemo(() => {
        if (!apiKey) return false;
        return apiKey.ownerType !== targetOwnerType
            || apiKey.ownerCode !== previewOwnerCode
            || apiKey.ownerName !== previewOwnerName;
    }, [apiKey, previewOwnerCode, previewOwnerName, targetOwnerType]);

    const handleSubmit = async () => {
        if (!apiKey || !hasActualChange) return;
        try {
            setSubmitting(true);
            await changeApiKeyOwner({
                code: apiKey.code,
                targetOwnerType,
                ...(targetOwnerCode.trim() ? { targetOwnerCode: targetOwnerCode.trim() } : {}),
                ...(targetOwnerName.trim() ? { targetOwnerName: targetOwnerName.trim() } : {}),
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
                        将 <span className="font-medium text-foreground break-all">{apiKey?.akDisplay || '-'}</span> 的 owner 调整为组织或项目。编码和名称留空时沿用当前值。
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4">
                    <div className="rounded-md border bg-muted/30 p-3 text-sm">
                        <div className="break-words">
                            当前所有者：<span className="break-all">{apiKey?.ownerName || '-'}</span>（{apiKey?.ownerType || '-'}）
                        </div>
                        <div className="text-muted-foreground break-words">
                            编码：<span className="break-all">{apiKey?.ownerCode || '-'}</span>
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium">目标类型</label>
                        <Select value={targetOwnerType} onValueChange={(value) => setTargetOwnerType(value as 'org' | 'project')}>
                            <SelectTrigger>
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="org">组织</SelectItem>
                                <SelectItem value="project">项目</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>

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
                        <div className="mt-1">类型：{targetOwnerType === 'org' ? '组织' : '项目'}</div>
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
