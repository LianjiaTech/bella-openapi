'use client'

import React, {ReactNode, useEffect, useRef, useState} from "react"
import {ColumnDef} from "@tanstack/react-table"
import {ApikeyInfo} from "@/lib/types/openapi"
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from "@/components/ui/tooltip"
import {CertifyDialog, DeleteDialog, QuotaDialog, RenameDialog, ResetDialog} from "./apikey-dialog"
import {HoverContext} from "@/components/ui/data-table";
import {Badge} from "@/components/ui/badge"
import {Button} from "@/components/ui/button"
import {Copy, Wallet} from 'lucide-react'
import {useToast} from "@/hooks/use-toast";
import {safety_apply_url} from "@/config";
import {ApiKeyBalanceDialog, ApiKeyBalanceIndicator} from "./apikey-balance";

interface EditableCellProps {
    content: ReactNode;
    dialogComponent: (isOpen: boolean, onClose: () => void) => React.ReactElement;
    positionCalc: string;
    rowId: string;
}

const EditableCell: React.FC<EditableCellProps> = ({ content, dialogComponent, positionCalc, rowId }) => {
    const hoveredRowId = React.useContext(HoverContext);
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const contentRef = useRef<HTMLSpanElement>(null);
    const [iconPosition, setIconPosition] = useState(0);

    useEffect(() => {
        if (contentRef.current) {
            const contentWidth = contentRef.current.offsetWidth;
            setIconPosition(contentWidth / 2 + 5);
        }
    }, [content]);

    const showButton = hoveredRowId === rowId || isDialogOpen;

    return (
        <div className="relative flex justify-center items-center w-full">
            <span ref={contentRef} className="font-medium">{content}</span>
            {showButton && (
                <div style={{ position: 'absolute', left: `calc(${positionCalc} + ${iconPosition}px)` }}>
                    {dialogComponent(isDialogOpen, () => setIsDialogOpen(!isDialogOpen))}
                </div>
            )}
        </div>
    );
};


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

const ActionCell = ({code, refresh, showApikey}: { code: string, refresh: () => void, showApikey: (apikey: string) => void }) => {
    const { toast } = useToast();
    const [showBalance, setShowBalance] = useState(false);
    
    const copyToClipboard = () => {
        navigator.clipboard.writeText(code).then(() => {
            toast({ title: "复制成功", description: "API Key编码复制成功。" })
        });
    };

    
    return (
        <div className="flex flex-wrap justify-end gap-2">
            <Button onClick={copyToClipboard} variant="ghost" size="icon" className="p-0 focus:ring-0">
                <TooltipProvider>
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <div>
                                <Copy className="h-4 w-4" />
                                <span className="sr-only">复制ak code</span>
                            </div>
                        </TooltipTrigger>
                        <TooltipContent>
                            <p>复制ak code</p>
                        </TooltipContent>
                    </Tooltip>
                </TooltipProvider>
            </Button>
            <DeleteDialog code={code} refresh={refresh}/>
            <ResetDialog code={code} showApikey={showApikey}/>
            <Button 
                onClick={() => setShowBalance(true)} 
                variant="ghost" 
                size="icon" 
                className="p-0 focus:ring-0"
            >
                <TooltipProvider>
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <div>
                                <Wallet className="h-4 w-4" />
                                <span className="sr-only">查看余额</span>
                            </div>
                        </TooltipTrigger>
                        <TooltipContent>查看余额</TooltipContent>
                    </Tooltip>
                </TooltipProvider>
            </Button>
            <ApiKeyBalanceDialog code={code} isOpen={showBalance} onClose={() => setShowBalance(false)} />
        </div>
    )
}

function getSafetyLevel(level: number) : string {
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

export const ApikeyColumns = (refresh: () => void, showApikey: (apikey : string) => void): ColumnDef<ApikeyInfo>[] => [
    {
        accessorKey: "akDisplay",
        header: "AK",
        cell: ({row}) =>
            (<div className="font-mono text-sm">
                {row.original.akDisplay}
            </div>)
        ,
    },
    {
        accessorKey: "name",
        header: "名称",
        cell: ({row}) => (
            <EditableCell
                content={row.original.name}
                dialogComponent={(isOpen, onClose) => (
                    <RenameDialog
                        code={row.original.code}
                        origin={row.original.name}
                        refresh={refresh}
                        isOpen={isOpen}
                        onClose={onClose}
                    />
                )}
                positionCalc="50%"
                rowId={row.id}
            />
        ),
    },
    {
        accessorKey: "serviceId",
        header:
            "服务名",
        cell:
            ({row}) => <div>{row.getValue("serviceId")}</div>,
    },
    {
        accessorKey: "safetyLevel",
        header: "安全等级",
        cell: ({row}) => {
            const level = row.original.safetyLevel as number;
            let color = "bg-green-100 text-green-800";
            if (level == 1) {
                color = "bg-yellow-100 text-yellow-800";
            }
            if (level == 0) {
                color = "bg-red-100 text-red-800";
            }

            return (
                safety_apply_url ?
                <EditableCell
                    content={<Badge className={`${color} capitalize`}>{getSafetyLevel(level)}
                </Badge>}
                    dialogComponent={(isOpen, onClose) => (
                        <CertifyDialog
                            code={row.original.code}
                            refresh={refresh}
                            isOpen={isOpen}
                            onClose={onClose}
                        />
                    )}
                    positionCalc="60%"
                    rowId={row.id}
                /> : <Badge className={`${color} capitalize`}>{getSafetyLevel(level)}
                    </Badge>
            );
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

            return (
                <EditableCell
                    content={formatted}
                    dialogComponent={(isOpen, onClose) => (
                        <QuotaDialog
                            code={row.original.code}
                            origin={row.original.monthQuota}
                            refresh={refresh}
                            isOpen={isOpen}
                            onClose={onClose}
                        />
                    )}
                    positionCalc="50%"
                    rowId={row.id}
                />
            );
        }
    },
    {
        accessorKey: "code",
        header: "余额状态",
        cell: ({row}) => 
                <ApiKeyBalanceIndicator code={row.original.code} />
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
            <ActionCell code={row.original.code} refresh={refresh} showApikey={showApikey}/>
        ),
    },
]