'use client'

import { useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Input } from "@/components/common/input";
import { Textarea } from "@/components/common/textarea";
import { changeApiKeyParent, getApiKeyByCode } from "@/lib/api/apiKeys";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { toast } from "sonner";

interface ParentChangeDialogProps {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
    onClose: () => void;
    onSuccess: () => void;
}

export function ParentChangeDialog({ isOpen, apiKey, onClose, onSuccess }: ParentChangeDialogProps) {
    const [targetParentCode, setTargetParentCode] = useState('');
    const [targetParent, setTargetParent] = useState<ApikeyInfo | null>(null);
    const [reason, setReason] = useState('');
    const [verifying, setVerifying] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [verifyMessage, setVerifyMessage] = useState<string>('');

    useEffect(() => {
        if (!isOpen) {
            setTargetParentCode('');
            setTargetParent(null);
            setReason('');
            setVerifying(false);
            setSubmitting(false);
            setVerifyMessage('');
        }
    }, [isOpen]);

    const verifyTargetParent = async () => {
        const code = targetParentCode.trim();
        if (!code) {
            setVerifyMessage('请输入目标父 AK code');
            setTargetParent(null);
            return;
        }
        if (code === apiKey?.code) {
            setVerifyMessage('目标父 AK 不能与当前 AK 相同');
            setTargetParent(null);
            return;
        }
        try {
            setVerifying(true);
            setVerifyMessage('');
            const result = await getApiKeyByCode(code);
            if (!result) {
                setVerifyMessage('未找到目标 AK');
                setTargetParent(null);
                return;
            }
            if (result.parentCode) {
                setVerifyMessage('目标 AK 不是父级 AK，不能作为挂载目标');
                setTargetParent(null);
                return;
            }
            if (apiKey?.parentCode === result.code) {
                setVerifyMessage('当前 AK 已挂在该目标父 AK 下');
                setTargetParent(result);
                return;
            }
            setTargetParent(result);
            setVerifyMessage('目标父 AK 校验通过');
        } catch (error) {
            setTargetParent(null);
            setVerifyMessage(error instanceof Error ? error.message : '目标父 AK 校验失败');
        } finally {
            setVerifying(false);
        }
    };

    const canSubmit = useMemo(() => {
        if (!apiKey || !targetParent) return false;
        return targetParent.code !== apiKey.code && apiKey.parentCode !== targetParent.code;
    }, [apiKey, targetParent]);

    const handleSubmit = async () => {
        if (!apiKey || !targetParent || !canSubmit) return;
        try {
            setSubmitting(true);
            await changeApiKeyParent({
                code: apiKey.code,
                targetParentCode: targetParent.code,
                ...(reason.trim() ? { reason: reason.trim() } : {}),
            });
            toast.success('父级迁移成功');
            onSuccess();
            onClose();
        } catch (error) {
            toast.error(error instanceof Error ? error.message : '父级迁移失败');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>迁移父级</DialogTitle>
                    <DialogDescription>
                        将 <span className="font-medium text-foreground">{apiKey?.akDisplay || '-'}</span> 迁移到另一个父 AK 名下。若当前 AK 为父 AK，将按扁平化规则一并迁移直属子 AK。
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4">
                    <div className="rounded-md border bg-muted/30 p-3 text-sm">
                        <div>当前 AK：{apiKey?.akDisplay || '-'}</div>
                        <div className="text-muted-foreground">当前父级：{apiKey?.parentCode || '顶层 AK'}</div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium">目标父 AK code</label>
                        <div className="flex gap-2">
                            <Input
                                value={targetParentCode}
                                onChange={(e) => {
                                    setTargetParentCode(e.target.value);
                                    setTargetParent(null);
                                    setVerifyMessage('');
                                }}
                                placeholder="输入目标父 AK code"
                                onKeyDown={(e) => e.key === 'Enter' && verifyTargetParent()}
                            />
                            <Button type="button" variant="outline" onClick={verifyTargetParent} disabled={verifying}>
                                {verifying ? '校验中...' : '校验'}
                            </Button>
                        </div>
                        {verifyMessage && (
                            <p className={`text-sm ${targetParent ? 'text-emerald-600' : 'text-destructive'}`}>
                                {verifyMessage}
                            </p>
                        )}
                    </div>

                    {targetParent && (
                        <div className="rounded-md border border-primary/20 bg-primary/5 p-3 text-sm">
                            <div className="font-medium">目标父 AK 预览</div>
                            <div className="mt-1">AK：{targetParent.akDisplay || '-'}</div>
                            <div>名称：{targetParent.name || '-'}</div>
                            <div>所有者：{targetParent.ownerName || '-'}（{targetParent.ownerType || '-'}）</div>
                            <div>管理者：{targetParent.managerName || '-'}</div>
                        </div>
                    )}

                    <div className="space-y-2">
                        <label className="text-sm font-medium">迁移原因</label>
                        <Textarea
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            placeholder="选填，建议说明为何需要挂靠到该父 AK"
                            className="min-h-[88px]"
                        />
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose} disabled={submitting}>取消</Button>
                    <Button onClick={handleSubmit} disabled={submitting || !canSubmit}>
                        {submitting ? '提交中...' : '确认迁移'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
