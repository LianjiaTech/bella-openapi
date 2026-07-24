'use client'

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, RefreshCw, Save, Search, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { TopBar } from "@/components/layout/top-bar";
import { Alert, AlertDescription } from "@/components/common/alert";
import { Badge } from "@/components/common/badge";
import { Button } from "@/components/common/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card";
import { Input } from "@/components/common/input";
import { Label } from "@/components/common/label";
import { getApiKeyByCode, getCurrentQps, updateApiKeyQpsLimit } from "@/lib/api/apiKeys";
import { ApikeyInfo } from "@/lib/types/apikeys";

function formatQpsLimit(value?: number | null): string {
    if (value === null || value === undefined || value === 0) {
        return "系统默认";
    }
    if (value < 0) {
        return "不限流";
    }
    return `${value} QPS`;
}

const MAX_QPS_LIMIT = 200;

export default function OpsToolsPage() {
    const [akCode, setAkCode] = useState("");
    const [qpsLimit, setQpsLimit] = useState("");
    const [apiKey, setApiKey] = useState<ApikeyInfo | null>(null);
    const [currentQps, setCurrentQps] = useState<number | null>(null);
    const [loadingAk, setLoadingAk] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const normalizedAkCode = useMemo(() => akCode.trim(), [akCode]);
    const currentAkCodeRef = useRef(normalizedAkCode);

    useEffect(() => {
        currentAkCodeRef.current = normalizedAkCode;
    }, [normalizedAkCode]);

    const handleAkCodeChange = useCallback((value: string) => {
        currentAkCodeRef.current = value.trim();
        setAkCode(value);
        setApiKey(null);
        setCurrentQps(null);
        setQpsLimit("");
        setError(null);
    }, []);

    const fetchAkInfo = useCallback(async () => {
        if (!normalizedAkCode) {
            setError(new Error("请输入 AK Code"));
            return;
        }

        try {
            setLoadingAk(true);
            setError(null);
            const targetAkCode = normalizedAkCode;
            const [detail, qps] = await Promise.all([
                getApiKeyByCode(targetAkCode),
                getCurrentQps(targetAkCode),
            ]);
            if (currentAkCodeRef.current !== targetAkCode) {
                return;
            }
            setApiKey(detail);
            setCurrentQps(qps);
            setQpsLimit(detail?.qpsLimit === null || detail?.qpsLimit === undefined ? "" : String(detail.qpsLimit));
        } catch (err) {
            setApiKey(null);
            setCurrentQps(null);
            setError(err instanceof Error ? err : new Error("查询 AK 信息失败"));
        } finally {
            setLoadingAk(false);
        }
    }, [normalizedAkCode]);

    const handleSubmit = useCallback(async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const limit = Number(qpsLimit);

        if (!normalizedAkCode) {
            setError(new Error("请输入 AK Code"));
            return;
        }
        if (!Number.isInteger(limit) || limit < 0 || limit > MAX_QPS_LIMIT) {
            setError(new Error(`限流大小必须为 0-${MAX_QPS_LIMIT} 的整数`));
            return;
        }

        try {
            setSubmitting(true);
            setError(null);
            await updateApiKeyQpsLimit(normalizedAkCode, limit);
            toast.success("限流配置已更新");
            const [detail, qps] = await Promise.all([
                getApiKeyByCode(normalizedAkCode),
                getCurrentQps(normalizedAkCode),
            ]);
            setApiKey(detail);
            setCurrentQps(qps);
        } catch (err) {
            setError(err instanceof Error ? err : new Error("更新限流配置失败"));
        } finally {
            setSubmitting(false);
        }
    }, [normalizedAkCode, qpsLimit]);

    return (
        <div>
            <TopBar title="运维工具" description="管理员快捷运维操作" />
            <div className="p-8">
                {error && (
                    <Alert variant="destructive" className="mb-4">
                        <AlertCircle className="h-4 w-4" />
                        <AlertDescription>{error.message}</AlertDescription>
                    </Alert>
                )}

                <div className="max-w-xl">
                    <Card className="rounded-md">
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2 text-base">
                                <ShieldCheck className="h-5 w-5" />
                                AK QPS 限流
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <form onSubmit={handleSubmit} className="space-y-5">
                                <div className="space-y-2">
                                    <Label htmlFor="ak-code">AK Code</Label>
                                    <div className="flex gap-2">
                                        <Input
                                            id="ak-code"
                                            value={akCode}
                                            onChange={(event) => handleAkCodeChange(event.target.value)}
                                            placeholder="ak-..."
                                            autoComplete="off"
                                        />
                                        <Button
                                            type="button"
                                            variant="outline"
                                            onClick={fetchAkInfo}
                                            disabled={loadingAk || !normalizedAkCode}
                                            className="shrink-0"
                                        >
                                            {loadingAk ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                                            查询
                                        </Button>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="qps-limit">限流大小</Label>
                                    <Input
                                        id="qps-limit"
                                        type="number"
                                        min={0}
                                        max={MAX_QPS_LIMIT}
                                        step={1}
                                        value={qpsLimit}
                                        onChange={(event) => setQpsLimit(event.target.value)}
                                        placeholder="200"
                                    />
                                    <div className="text-xs text-muted-foreground">0 为系统默认，最大 {MAX_QPS_LIMIT}</div>
                                </div>

                                <Button type="submit" disabled={submitting || !normalizedAkCode || qpsLimit === ""} className="w-full">
                                    {submitting ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                                    确认
                                </Button>
                            </form>

                            <div className="mt-6 rounded-md border">
                                <div className="grid grid-cols-2 gap-0 text-sm">
                                    <div className="border-b border-r p-3 text-muted-foreground">当前配置</div>
                                    <div className="border-b p-3 font-medium">{apiKey ? formatQpsLimit(apiKey.qpsLimit) : "-"}</div>
                                    <div className="border-b border-r p-3 text-muted-foreground">当前 QPS</div>
                                    <div className="border-b p-3 font-medium">{currentQps ?? "-"}</div>
                                    <div className="border-r p-3 text-muted-foreground">AK 状态</div>
                                    <div className="p-3">
                                        {apiKey ? (
                                            <Badge variant={apiKey.status === "active" ? "default" : "secondary"}>
                                                {apiKey.status}
                                            </Badge>
                                        ) : "-"}
                                    </div>
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    );
}
