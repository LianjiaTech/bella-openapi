import React, {ChangeEvent, useState} from 'react'
import {bindService, deleteApikey, rename, resetApikey, updateCertify} from "@/lib/api/apikey"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog"
import {Button} from "@/components/ui/button"
import {Label} from "@/components/ui/label"
import {Input} from "@/components/ui/input"
import {useToast} from "@/hooks/use-toast"
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from "@/components/ui/tooltip"
import {RotateCcw, SquarePen, Trash2} from 'lucide-react'
import {apikey_quota_apply_url, safety_apply_url} from "@/config";


interface ActionDialogProps {
    label: string
    description: string | React.ReactNode
    onConfirm: (() => Promise<void>) | (() => void)
    inputLabel?: string
    inputProps?: React.InputHTMLAttributes<HTMLInputElement>
    icon: React.ReactNode
    isIcon: boolean
    isOpen: boolean
    onClose: () => void
    remark? : string | React.ReactNode
}

const ActionDialog: React.FC<ActionDialogProps> = ({
                                                       label, description, onConfirm, inputLabel, inputProps,
                                                       icon, isIcon, isOpen, onClose, remark
                                                   }) => {
    const [isLoading, setIsLoading] = useState(false)

    const handleConfirm = async () => {
        setIsLoading(true)
        await onConfirm()
        setIsLoading(false)
        onClose()
    }

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogTrigger asChild>
                {isIcon ? (
                    <Button
                        variant="ghost"
                        size="icon"
                        className="p-0 focus:ring-0"
                    >
                        <TooltipProvider>
                            <Tooltip>
                                <TooltipTrigger asChild>
                                    <div>
                                        {icon}
                                        <span className="sr-only">{label}</span>
                                    </div>
                                </TooltipTrigger>
                                <TooltipContent>
                                    <p>{label}</p>
                                </TooltipContent>
                            </Tooltip>
                        </TooltipProvider>
                    </Button>
                ) : (
                    <Button variant="outline" size="sm" className="w-full sm:w-auto">
                        {icon}
                        <span className="sr-only sm:not-sr-only sm:ml-2">{label}</span>
                    </Button>
                )}
            </DialogTrigger>
            <DialogContent className="sm:max-w-[425px] bg-white">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2 text-gray-900">
                        {icon}
                        {label}
                    </DialogTitle>
                    <DialogDescription className="text-gray-600">{description}</DialogDescription>
                </DialogHeader>
                {inputLabel && (
                    <div className="grid gap-4 py-4">
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor={inputProps?.id} className="text-right text-gray-700">{inputLabel}</Label>
                            {inputProps && <Input {...inputProps} className="col-span-3"/>}
                        </div>
                    </div>
                )}
                {
                    remark && <div className="text-gray-600">
                        {remark}
                    </div>
                }
                <DialogFooter>
                    <Button type="submit" onClick={handleConfirm} disabled={isLoading}>
                        {isLoading ? "处理中..." : "确认"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}

export const DeleteDialog: React.FC<{ code: string; refresh: () => void }> = ({code, refresh}) => {
    const {toast} = useToast()
    const [isOpen, setIsOpen] = useState(false)

    const handleConfirm = async () => {
        try {
            const success = await deleteApikey(code)
            if (success) {
                refresh()
                toast({title: "API Key 已删除", description: "API Key 已成功删除。"})
            } else {
                toast({title: "删除失败", description: "无法删除 API Key，请稍后重试。", variant: "destructive"})
            }
        } catch (error) {
            // @ts-ignore
            toast({title: "删除失败", description: error.error, variant: "destructive"})
        }
    }

    const handleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (!isOpen) {
            setIsOpen(true);
        }
    };

    return (
        <div onClick={handleClick} className="cursor-pointer">
            <ActionDialog
                label="删除"
                description="确定要删除此 API Key 吗？此操作无法撤销。"
                onConfirm={handleConfirm}
                icon={<Trash2 className="h-4 w-4"/>}
                isIcon={true}
                isOpen={isOpen}
                onClose={() => setIsOpen(false)}
            />
        </div>
    )
}

export const ResetDialog: React.FC<{ code: string; showApikey: (apikey : string) => void }> = ({code, showApikey}) => {
    const {toast} = useToast()
    const [isOpen, setIsOpen] = useState(false)

    const handleConfirm = async () => {
        try {
            const apikey = await resetApikey(code)
            if (apikey) {
                showApikey(apikey)
            } else {
                toast({title: "重置失败", description: "无法重置 API Key，请稍后重试。", variant: "destructive"})
            }
        } catch (error) {
            // @ts-ignore
            toast({title: "重置失败", description: error.error, variant: "destructive"})
        }
    }

    const handleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (!isOpen) {
            setIsOpen(true);
        }
    };

    return (
        <div onClick={handleClick} className="cursor-pointer">
            <ActionDialog
                label="重置"
                description="确定要重置此 API Key 吗？重置后，当前的 Key 将失效。"
                onConfirm={handleConfirm}
                icon={<RotateCcw className="h-4 w-4"/>}
                isIcon={true}
                isOpen={isOpen}
                onClose={() => setIsOpen(false)}
            />
        </div>
    )
}

export const CertifyDialog: React.FC<{
    code: string;
    refresh: () => void;
    isOpen: boolean;
    onClose: () => void
}> = ({code, refresh, isOpen, onClose}) => {
    const [certify, setCertify] = useState("")
    const {toast} = useToast()

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        setCertify(e.target.value)
    }

    const handleConfirm = async () => {
        if (certify === "") return
        try {
            const success = await updateCertify(code, certify)
            if (success) {
                refresh()
                toast({title: "安全认证成功", description: "API Key 的安全认证已更新。"})
            } else {
                toast({title: "安全认证失败", description: "无法更新安全认证，请稍后重试。", variant: "destructive"})
            }
        } catch (error) {
            // @ts-ignore
            toast({title: "安全认证失败", description: error.error, variant: "destructive"})
        }
    }

    return (
        <ActionDialog
            label="安全认证"
            description={<div>
                安全认证码申请请点击跳转：
                <a href={safety_apply_url} className='text-blue-700' target='_blank'>安全合规申请</a>
            </div>}
            onConfirm={handleConfirm}
            inputLabel="安全认证码"
            inputProps={{
                id: "certify",
                value: certify,
                onChange: handleChange,
                placeholder: "输入新的安全认证码",
            }}
            icon={<SquarePen className="h-4 w-4"/>}
            isIcon={true}
            isOpen={isOpen}
            onClose={onClose}
        />
    )
}

export const QuotaDialog: React.FC<{
    code: string;
    origin: number;
    refresh: () => void;
    isOpen: boolean;
    onClose: () => void
}> = ({code, origin, refresh, isOpen, onClose}) => {
    const handleApplyQuota = () => {
        if(apikey_quota_apply_url) {
            window.open(apikey_quota_apply_url, '_blank')
        }
        onClose()
    }

    return (
        <ActionDialog
            label="充值额度"
            description={apikey_quota_apply_url ? "点击确认按钮跳转到额度申请页面。Ak Code:" + code : "充值功能暂未开放"}
            onConfirm={handleApplyQuota}
            icon={<SquarePen className="h-4 w-4"/>}
            isIcon={true}
            isOpen={isOpen}
            onClose={onClose}
        >
        </ActionDialog>
    )
}

export const RenameDialog: React.FC<{
    code: string;
    origin: string;
    refresh: () => void;
    isOpen: boolean;
    onClose: () => void
}> = ({code, origin, refresh, isOpen, onClose}) => {
    const [name, setName] = useState(origin)
    const {toast} = useToast()

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value
        if (!value) {
            setName("")
        } else {
            setName(value)
        }
    }

    const handleConfirm = async () => {
        if (origin === name) return
        try {
            const success = await rename(code, name)
            if (success) {
                refresh()
                toast({title: "修改成功", description: "apikey名称修改为:" + name})
            } else {
                setName(origin)
                toast({title: "修改失败", description: "apikey名称修改失败，请稍后重试。", variant: "destructive"})
            }
        } catch (error) {
            setName(origin)
            // @ts-ignore
            toast({title: "修改失败", description: error.error, variant: "destructive"})
        }
    }

    return (
        <ActionDialog
            label="修改名称"
            description="请输入apikey名称"
            onConfirm={handleConfirm}
            inputLabel="名称"
            inputProps={{
                id: "name",
                value: name,
                onChange: handleChange,
                placeholder: "输入名称",
            }}
            icon={<SquarePen className="h-4 w-4"/>}
            isIcon={true}
            isOpen={isOpen}
            onClose={onClose}
        />
    )
}

export const ServiceIdDialog: React.FC<{
    code: string;
    origin: string;
    refresh: () => void;
    isOpen: boolean;
    onClose: () => void
}> = ({code, origin, refresh, isOpen, onClose}) => {
    const [serviceId, setServiceId] = useState(origin)
    const {toast} = useToast()

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value
        setServiceId(value)
    }

    const handleConfirm = async () => {
        if (origin === serviceId) return
        try {
            const success = await bindService(code, serviceId)
            if (success) {
                refresh()
                toast({title: "修改成功", description: "服务ID修改为:" + serviceId})
            } else {
                setServiceId(origin)
                toast({title: "修改失败", description: "服务ID修改失败，请稍后重试。", variant: "destructive"})
            }
        } catch (error) {
            setServiceId(origin)
            // @ts-ignore
            toast({title: "修改失败", description: error.error, variant: "destructive"})
        }
    }

    return (
        <ActionDialog
            label="修改服务ID"
            description="请输入服务ID"
            onConfirm={handleConfirm}
            inputLabel="服务ID"
            inputProps={{
                id: "serviceId",
                value: serviceId,
                onChange: handleChange,
                placeholder: "输入服务ID",
            }}
            icon={<SquarePen className="h-4 w-4"/>}
            isIcon={true}
            isOpen={isOpen}
            onClose={onClose}
        />
    )
}
