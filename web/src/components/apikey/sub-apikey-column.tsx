'use client'

import React from "react"
import {ColumnDef} from "@tanstack/react-table"
import {ApikeyInfo} from "@/lib/types/openapi"
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from "@/components/ui/tooltip"
import {Badge} from "@/components/ui/badge"
import {Button} from "@/components/ui/button"
import {Copy} from 'lucide-react'
import {useToast} from "@/hooks/use-toast"

const RemarkCell = ({ value }: { value: string }) => {
    const remark = value || '/'
    return (
        <TooltipProvider>
            <Tooltip>
                <TooltipTrigger asChild>
                    <div className="truncate max-w-xs cursor-help">{remark}</div>
                </TooltipTrigger>
                <TooltipContent side="bottom" className="w-64 break-words">
                    <p>{remark}</p>
                </TooltipContent>
            </Tooltip>
        </TooltipProvider>
    )
}

const ActionCell = ({code}: { code: string }) => {
    const { toast } = useToast();

    const copyToClipboard = () => {
        navigator.clipboard.writeText(code).then(() => {
            toast({ title: "复制成功", description: "子API Key编码复制成功。" })
        });
    };

    return (
        <div className="flex justify-end">
            <Button onClick={copyToClipboard} variant="ghost" size="icon" className="p-0 focus:ring-0">
                <TooltipProvider>
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <div>
                                <Copy className="h-4 w-4" />
                                <span className="sr-only">复制子ak code</span>
                            </div>
                        </TooltipTrigger>
                        <TooltipContent>
                            <p>复制子ak code</p>
                        </TooltipContent>
                    </Tooltip>
                </TooltipProvider>
            </Button>
        </div>
    )
}

function getSafetyLevel(level: number): string {
    switch (level) {
        case 10:
            return "极低";
        case 20:
            return "低";
        case 30:
            return "中";
        case 40:
            return "高";
        default:
            return "N/A";
    }
}

export const SubApikeyColumns = (refresh: () => void): ColumnDef<ApikeyInfo>[] => [
    {
        accessorKey: "akDisplay",
        header: "子AK",
        cell: ({row}) =>
            (<div className="font-mono text-sm">
                {row.original.akDisplay}
            </div>)
    },
    {
        accessorKey: "name",
        header: "名称",
        cell: ({row}) => (
            <div className="font-medium">{row.original.name}</div>
        ),
    },
    {
        accessorKey: "outEntityCode",
        header: "关联标识",
        cell: ({row}) => <div className="font-mono text-sm">{row.original.outEntityCode || '/'}</div>,
    },
    {
        accessorKey: "safetyLevel",
        header: "安全等级",
        cell: ({row}) => {
            const level = row.original.safetyLevel as number;
            let color = "bg-green-100 text-green-800";
            if (level <= 20) {
                color = "bg-yellow-100 text-yellow-800";
            }
            if (level <= 10) {
                color = "bg-red-100 text-red-800";
            }

            return <Badge className={`${color} capitalize`}>{getSafetyLevel(level)}</Badge>;
        },
    },
    {
        accessorKey: "monthQuota",
        header: "每月额度",
        cell: ({row}) => {
            const formatted = new Intl.NumberFormat("zh-CN", {
                style: "currency",
                currency: "CNY",
            }).format(row.original.monthQuota);
            return formatted;
        }
    },
    {
        accessorKey: "remark",
        header: "备注",
        cell: ({row}) => <RemarkCell value={row.original.remark}/>,
    },
    {
        id: "actions",
        header: "",
        cell: ({row}) => (
            <ActionCell code={row.original.code}/>
        ),
    },
]
