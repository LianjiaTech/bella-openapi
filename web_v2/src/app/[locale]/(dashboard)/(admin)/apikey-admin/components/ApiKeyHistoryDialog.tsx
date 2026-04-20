'use client'

import { useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Badge } from "@/components/common/badge";
import { ApikeyChangeLog, ApikeyInfo } from "@/lib/types/apikeys";
import { getApiKeyChangeHistory } from "@/lib/api/apiKeys";
import { toast } from "sonner";

interface ApiKeyHistoryDialogProps {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
    onClose: () => void;
}

type HistoryFilter = 'all' | 'owner_transfer' | 'owner_change' | 'parent_change';

const FILTER_OPTIONS: Array<{ value: HistoryFilter; label: string }> = [
    { value: 'all', label: '全部' },
    { value: 'owner_transfer', label: '转交' },
    { value: 'owner_change', label: '所有者变更' },
    { value: 'parent_change', label: '父级迁移' },
];

const ACTION_LABEL: Record<Exclude<HistoryFilter, 'all'>, string> = {
    owner_transfer: '转交',
    owner_change: '所有者变更',
    parent_change: '父级迁移',
};

export function ApiKeyHistoryDialog({ isOpen, apiKey, onClose }: ApiKeyHistoryDialogProps) {
    const [loading, setLoading] = useState(false);
    const [logs, setLogs] = useState<ApikeyChangeLog[]>([]);
    const [filter, setFilter] = useState<HistoryFilter>('all');

    useEffect(() => {
        if (!isOpen || !apiKey?.code) {
            setLogs([]);
            setFilter('all');
            return;
        }
        let cancelled = false;
        const fetchLogs = async () => {
            try {
                setLoading(true);
                const result = await getApiKeyChangeHistory(apiKey.code);
                if (!cancelled) {
                    setLogs(result);
                }
            } catch (error) {
                if (!cancelled) {
                    toast.error(error instanceof Error ? error.message : '获取变更历史失败');
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };
        fetchLogs();
        return () => {
            cancelled = true;
        };
    }, [isOpen, apiKey]);

    const filteredLogs = useMemo(() => {
        if (filter === 'all') return logs;
        return logs.filter((log) => log.actionType === filter);
    }, [filter, logs]);

    const parseAffectedCount = (affectedCodes: string) => {
        try {
            const parsed = JSON.parse(affectedCodes);
            return Array.isArray(parsed) ? parsed.length : 0;
        } catch {
            return 0;
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-3xl">
                <DialogHeader>
                    <DialogTitle>变更历史</DialogTitle>
                    <DialogDescription>
                        查看 <span className="font-medium text-foreground">{apiKey?.akDisplay || '-'}</span> 的管理员变更记录。
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-wrap gap-2">
                    {FILTER_OPTIONS.map((option) => (
                        <Button
                            key={option.value}
                            type="button"
                            variant={filter === option.value ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setFilter(option.value)}
                        >
                            {option.label}
                        </Button>
                    ))}
                </div>

                <div className="max-h-[420px] overflow-y-auto rounded-md border">
                    {loading ? (
                        <div className="p-8 text-center text-sm text-muted-foreground">加载中...</div>
                    ) : filteredLogs.length === 0 ? (
                        <div className="p-8 text-center text-sm text-muted-foreground">暂无变更历史</div>
                    ) : (
                        <div className="divide-y">
                            {filteredLogs.map((log) => (
                                <div key={log.id} className="p-4 space-y-3">
                                    <div className="flex flex-wrap items-center justify-between gap-2">
                                        <div className="flex items-center gap-2">
                                            <Badge>{ACTION_LABEL[log.actionType as Exclude<HistoryFilter, 'all'>] || log.actionType}</Badge>
                                            <span className="text-sm text-muted-foreground">{log.ctime}</span>
                                        </div>
                                        <div className="text-xs text-muted-foreground">
                                            操作人：{log.operatorName || '-'}
                                        </div>
                                    </div>

                                    <div className="grid gap-3 md:grid-cols-2 text-sm">
                                        <div className="rounded-md bg-muted/40 p-3">
                                            <div className="font-medium">变更前</div>
                                            <div className="mt-1">owner：{log.fromOwnerName || '-'}（{log.fromOwnerType || '-'}）</div>
                                            <div>ownerCode：{log.fromOwnerCode || '-'}</div>
                                            <div>parent：{log.fromParentCode || '顶层 AK'}</div>
                                            <div>manager：{log.fromManagerName || '-'}</div>
                                        </div>
                                        <div className="rounded-md bg-primary/5 p-3">
                                            <div className="font-medium">变更后</div>
                                            <div className="mt-1">owner：{log.toOwnerName || '-'}（{log.toOwnerType || '-'}）</div>
                                            <div>ownerCode：{log.toOwnerCode || '-'}</div>
                                            <div>parent：{log.toParentCode || '顶层 AK'}</div>
                                            <div>manager：{log.toManagerName || '-'}</div>
                                        </div>
                                    </div>

                                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                                        <div>影响 AK 数量：{parseAffectedCount(log.affectedCodes)}</div>
                                        <div>原因：{log.reason || '-'}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>关闭</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
