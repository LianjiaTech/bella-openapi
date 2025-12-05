'use client'

import React from "react"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import Image from "next/image"
import {Users} from "lucide-react";

interface SubApikeyTipsDialogProps {
    showDialog: boolean
    setShowDialog: (show: boolean) => void
}

export const SubApikeyTipsDialog: React.FC<SubApikeyTipsDialogProps> = ({
    showDialog,
    setShowDialog
}) => {
    const handleClose = () => {
        setShowDialog(false)
    }

    return (
        <Dialog open={showDialog} onOpenChange={handleClose}>
            <DialogContent className="sm:max-w-md bg-white dark:bg-gray-800 border-0">
                <DialogHeader>
                    <DialogTitle>如何管理子API Key</DialogTitle>
                    <DialogDescription>
                        您可以通过以下步骤管理子API Key
                    </DialogDescription>
                </DialogHeader>

                <div className="space-y-4">
                    <div className="text-sm">
                        <ol className="list-decimal pl-5 space-y-3">
                            <li>在API Key列表中，找到API Key行最右侧的操作按钮栏</li>
                            <li>
                                点击
                                <span className="font-semibold inline-flex items-center">
                                    <Users className="h-4 w-4 mr-1"/>
                                </span>
                            </li>
                            <li>在打开的页面中，您可以管理子API Key</li>
                        </ol>
                    </div>

                    <div className="border rounded-md p-3 bg-gray-50 dark:bg-gray-700">
                        <p className="text-sm text-gray-600 dark:text-gray-300 mb-2">提示：</p>
                        <ul className="list-disc pl-5 text-sm text-gray-600 dark:text-gray-300 space-y-1">
                            <li>子API Key继承父API Key的权限，但不能超过父API Key的权限级别</li>
                            <li>子API Key的配额不能超过父API Key的配额</li>
                            <li>您可以随时创建、修改、禁用或删除子API Key</li>
                        </ul>
                    </div>
                </div>

                <DialogFooter>
                    <Button onClick={handleClose} className="w-full">
                        我知道了
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}
