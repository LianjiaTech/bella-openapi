'use client'

import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { ApikeyInfo, OwnerInheritancePreview } from "@/lib/types/apikeys";
import { previewOwnerInheritance } from "@/lib/api/apiKeys";
import { toast } from "sonner";

interface OwnerInheritanceDialogProps {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
    onClose: () => void;
}

export function OwnerInheritanceDialog({ isOpen, apiKey, onClose }: OwnerInheritanceDialogProps) {
    const [preview, setPreview] = useState<OwnerInheritancePreview | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!isOpen || !apiKey?.code) {
            setPreview(null);
            setLoading(false);
            return;
        }

        let cancelled = false;
        const loadPreview = async () => {
            try {
                setLoading(true);
                const result = await previewOwnerInheritance(apiKey.code);
                if (!cancelled) {
                    setPreview(result);
                }
            } catch (error) {
                if (!cancelled) {
                    toast.error(error instanceof Error ? error.message : '获取子 AK 归属异常失败');
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        loadPreview();
        return () => {
            cancelled = true;
        };
    }, [isOpen, apiKey]);

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-3xl">
                <DialogHeader>
                    <DialogTitle>检查子 AK 归属异常</DialogTitle>
                    <DialogDescription className="break-words">
                        检查 <span className="font-medium text-foreground break-all">{apiKey?.akDisplay || apiKey?.code || '-'}</span> 的直属子 AK owner 是否与父 AK owner 一致。这里只展示异常，不自动修改数据。
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4">
                    <div className="rounded-md border bg-muted/30 p-3 text-sm">
                        <div>父 AK 归属：{preview?.parentOwnerName || apiKey?.ownerName || '-'}（{preview?.parentOwnerType || apiKey?.ownerType || '-'}）</div>
                        <div className="text-muted-foreground break-words">
                            编码：<span className="break-all">{preview?.parentOwnerCode || apiKey?.ownerCode || '-'}</span>
                        </div>
                    </div>

                    <div className="rounded-md border">
                        <div className="border-b px-3 py-2 text-sm font-medium">
                            检查结果：{loading ? '加载中...' : `${preview?.mismatchedCount || 0} 个子 AK 归属异常`}
                        </div>
                        <div className="max-h-[260px] overflow-y-auto">
                            {loading ? (
                                <div className="p-6 text-center text-sm text-muted-foreground">加载中...</div>
                            ) : !preview || preview.items.length === 0 ? (
                                <div className="p-6 text-center text-sm text-muted-foreground">当前直属子 AK 归属已与父 AK 一致，未发现异常</div>
                            ) : (
                                <div className="divide-y">
                                    {preview.items.map((item) => (
                                        <div key={item.code} className="grid gap-3 p-3 text-sm md:grid-cols-[1.2fr_1fr_1fr]">
                                            <div className="min-w-0">
                                                <div className="truncate font-medium" title={item.name || item.akDisplay || item.code}>
                                                    {item.name || item.akDisplay || item.code}
                                                </div>
                                                <div className="truncate text-xs text-muted-foreground" title={item.code}>{item.code}</div>
                                                <div className="truncate text-xs text-muted-foreground" title={item.managerName || item.managerCode || ''}>
                                                    负责人：{item.managerName || item.managerCode || '-'}
                                                </div>
                                            </div>
                                            <div className="min-w-0 rounded bg-muted/40 p-2">
                                                <div className="text-xs text-muted-foreground">当前归属</div>
                                                <div className="truncate" title={item.currentOwnerName}>{item.currentOwnerName || '-'}</div>
                                                <div className="truncate text-xs text-muted-foreground" title={item.currentOwnerCode}>
                                                    {item.currentOwnerType || '-'} / {item.currentOwnerCode || '-'}
                                                </div>
                                            </div>
                                            <div className="min-w-0 rounded bg-primary/5 p-2">
                                                <div className="text-xs text-muted-foreground">父 AK 归属</div>
                                                <div className="truncate" title={item.targetOwnerName}>{item.targetOwnerName || '-'}</div>
                                                <div className="truncate text-xs text-muted-foreground" title={item.targetOwnerCode}>
                                                    {item.targetOwnerType || '-'} / {item.targetOwnerCode || '-'}
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>关闭</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
