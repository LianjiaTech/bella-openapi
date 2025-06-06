'use client'

import React, {useState} from "react"
import {ApikeyInfo} from "@/lib/types/openapi"
import {createSubApikey} from "@/lib/api/apikey"
import {useToast} from "@/hooks/use-toast"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
import {Button} from "@/components/ui/button"
import {Input} from "@/components/ui/input"
import {Label} from "@/components/ui/label"
import {Textarea} from "@/components/ui/textarea"
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select"

interface CreateSubApikeyDialogProps {
    isOpen: boolean
    onClose: () => void
    onSuccess: () => void
    parentApikey: ApikeyInfo
}

interface FormData {
    name: string
    outEntityCode: string
    safetyLevel: number
    monthQuota: number
    remark: string
}

const safetyLevelOptions = [
    { value: 10, label: "极低" },
    { value: 20, label: "低" },
    { value: 30, label: "中" },
    { value: 40, label: "高" },
]

export const CreateSubApikeyDialog: React.FC<CreateSubApikeyDialogProps> = ({
    isOpen,
    onClose,
    onSuccess,
    parentApikey
}) => {
    const [formData, setFormData] = useState<FormData>({
        name: '',
        outEntityCode: '',
        safetyLevel: 10,
        monthQuota: 50,
        remark: ''
    })
    const [isLoading, setIsLoading] = useState(false)
    const {toast} = useToast()

    const handleInputChange = (field: keyof FormData, value: string | number) => {
        setFormData(prev => ({
            ...prev,
            [field]: value
        }))
    }

    const handleSubmit = async () => {
        // 验证必填字段
        if (!formData.name.trim()) {
            toast({
                title: "错误",
                description: "请填写子API Key名称",
                variant: "destructive",
            })
            return
        }

        if (!formData.outEntityCode.trim()) {
            toast({
                title: "错误",
                description: "请填写关联标识",
                variant: "destructive",
            })
            return
        }

        // 验证安全等级不超过父AK
        if (formData.safetyLevel > parentApikey.safetyLevel) {
            toast({
                title: "错误",
                description: "安全等级不能超过父API Key的安全等级",
                variant: "destructive",
            })
            return
        }

        // 验证月配额不超过父AK
        if (formData.monthQuota > parentApikey.monthQuota) {
            toast({
                title: "错误",
                description: "月配额不能超过父API Key的月配额",
                variant: "destructive",
            })
            return
        }

        setIsLoading(true)
        try {
            const result = await createSubApikey({
                parentCode: parentApikey.code,
                name: formData.name.trim(),
                outEntityCode: formData.outEntityCode.trim(),
                safetyLevel: formData.safetyLevel,
                monthQuota: formData.monthQuota,
                remark: formData.remark.trim(),
                roleCode: 'low' // 固定为low
            })

            if (result) {
                toast({
                    title: "成功",
                    description: "子API Key创建成功",
                })
                onSuccess()
                // 重置表单
                setFormData({
                    name: '',
                    outEntityCode: '',
                    safetyLevel: 10,
                    monthQuota: 50,
                    remark: ''
                })
            }
        } catch (error) {
            console.error('Failed to create sub API key:', error)
            toast({
                title: "错误",
                description: "创建子API Key失败",
                variant: "destructive",
            })
        } finally {
            setIsLoading(false)
        }
    }

    const handleClose = () => {
        if (!isLoading) {
            onClose()
        }
    }

    // 获取可选的安全等级选项（不超过父AK的等级）
    const availableSafetyLevels = safetyLevelOptions.filter(
        option => option.value <= parentApikey.safetyLevel
    )

    return (
        <Dialog open={isOpen} onOpenChange={handleClose}>
            <DialogContent className="sm:max-w-md bg-white dark:bg-gray-800 border-0">
                <DialogHeader>
                    <DialogTitle>创建子API Key</DialogTitle>
                    <DialogDescription>
                        为父API Key "{parentApikey.name}" 创建子API Key
                    </DialogDescription>
                </DialogHeader>
                
                <div className="space-y-4">
                    <div>
                        <Label htmlFor="name">名称 *</Label>
                        <Input
                            id="name"
                            value={formData.name}
                            onChange={(e) => handleInputChange('name', e.target.value)}
                            placeholder="请输入子API Key名称"
                            disabled={isLoading}
                        />
                    </div>
                    
                    <div>
                        <Label htmlFor="outEntityCode">关联标识 *</Label>
                        <Input
                            id="outEntityCode"
                            value={formData.outEntityCode}
                            onChange={(e) => handleInputChange('outEntityCode', e.target.value)}
                            placeholder="请输入关联标识"
                            disabled={isLoading}
                        />
                    </div>
                    
                    <div>
                        <Label htmlFor="safetyLevel">安全等级</Label>
                        <Select
                            value={formData.safetyLevel.toString()}
                            onValueChange={(value) => handleInputChange('safetyLevel', parseInt(value))}
                            disabled={isLoading}
                        >
                            <SelectTrigger>
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {availableSafetyLevels.map((option) => (
                                    <SelectItem key={option.value} value={option.value.toString()}>
                                        {option.label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    
                    <div>
                        <Label htmlFor="monthQuota">月配额 (最大: ¥{parentApikey.monthQuota})</Label>
                        <Input
                            id="monthQuota"
                            type="number"
                            min="1"
                            max={parentApikey.monthQuota}
                            value={formData.monthQuota}
                            onChange={(e) => handleInputChange('monthQuota', parseFloat(e.target.value) || 0)}
                            disabled={isLoading}
                        />
                    </div>
                    
                    <div>
                        <Label htmlFor="remark">备注</Label>
                        <Textarea
                            id="remark"
                            value={formData.remark}
                            onChange={(e) => handleInputChange('remark', e.target.value)}
                            placeholder="请输入备注信息"
                            disabled={isLoading}
                        />
                    </div>
                </div>
                
                <DialogFooter>
                    <Button variant="outline" onClick={handleClose} disabled={isLoading}>
                        取消
                    </Button>
                    <Button onClick={handleSubmit} disabled={isLoading}>
                        {isLoading ? "创建中..." : "创建"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}
