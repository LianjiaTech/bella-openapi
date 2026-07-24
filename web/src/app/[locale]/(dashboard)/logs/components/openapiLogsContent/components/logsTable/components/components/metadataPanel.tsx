"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import {
    Clock,
    Hash,
    Globe,
    User,
    Key,
    Cpu,
    Route,
    Coins,
} from "lucide-react"
import type { LogEntry } from "@/lib/types/logs"

interface MetadataPanelProps {
    data: LogEntry
}

function MetadataItem({
    icon: Icon,
    label,
    value,
    isMono,
}: {
    icon: React.ElementType
    label: string
    value: string | undefined
    isMono?: boolean
}) {
    return (
        <div className="flex items-start gap-3 py-3 px-4 rounded-lg bg-secondary/50 hover:bg-secondary transition-colors">
            <Icon className="h-4 w-4 text-primary mt-0.5 shrink-0" />
            <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground mb-0.5">{label}</p>
                <p
                    className={`text-sm text-foreground break-all ${isMono ? "font-mono" : ""}`}
                >
                    {value ?? "N/A"}
                </p>
            </div>
        </div>
    )
}

export function MetadataPanel({ data }: MetadataPanelProps) {
    const requestDate = new Date(data.data_info_msg_requestTime * 1000)
    const formattedTime = requestDate.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    })

    return (
        <Card className="border-border bg-card shadow-[0_1px_4px_0_rgb(0_0_0/0.06)]">
            <CardHeader className="pb-4">
                <div className="flex items-center justify-between">
                    <CardTitle className="text-base font-medium text-foreground">
                        请求元数据
                    </CardTitle>
                </div>
            </CardHeader>
            <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    <MetadataItem
                        icon={Key}
                        label="AK Code"
                        value={data.data_info_msg_akCode || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Hash}
                        label="Request ID"
                        value={data.data_info_msg_requestId || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Route}
                        label="Bella Trace ID"
                        value={data.data_info_msg_bellaTraceId || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={User}
                        label="用户ID"
                        value={data.data_info_msg_accountCode || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Globe}
                        label="转发URL"
                        value={data.data_info_msg_forwardUrl || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Clock}
                        label="请求时间"
                        value={formattedTime || '--'}
                    />
                    <MetadataItem
                        icon={Cpu}
                        label="模型"
                        value={data.data_info_msg_model || '--'}
                    />
                    <MetadataItem
                        icon={Route}
                        label="Endpoint"
                        value={data.data_info_msg_endpoint || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Hash}
                        label="渠道ID"
                        value={data.data_info_msg_channelCode || '--'}
                        isMono
                    />
                    <MetadataItem
                        icon={Coins}
                        label="Tokens"
                        value={data.data_info_msg_usage || '--'}
                        isMono
                    />
                </div>
            </CardContent>
        </Card>
    )
}
