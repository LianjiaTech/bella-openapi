import React from "react";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "@/components/ui/dialog";
import {Button} from "@/components/ui/button";
import {Check, Copy} from "lucide-react";

interface CopyApikeyProps {
    showDialog: boolean,
    setShowDialog: (open: boolean) => void,
    apikey: string | null,
    handleCopyApiKey: () => void,
    copied: boolean
}

export const CopyApikeyDialog: React.FC<CopyApikeyProps> = ({
    showDialog, setShowDialog, apikey, handleCopyApiKey, copied
}) => {
    return <Dialog open={showDialog} onOpenChange={setShowDialog}>
        <DialogContent className="sm:max-w-md bg-white dark:bg-gray-800 border-0">
            <DialogHeader>
                <DialogTitle className="text-center text-xl font-semibold">API Key 创建成功</DialogTitle>
                <DialogDescription className="text-center pt-2">
                    <div className="space-y-4 mt-2">
                        <div
                            className="bg-blue-50 dark:bg-blue-900/30 p-3 rounded-md border border-blue-100 dark:border-blue-800">
                            <p className="text-sm font-medium text-blue-800 dark:text-blue-300">
                                请保存您的API Key，它只会显示一次。关闭此窗口后将无法再次查看完整的API Key。
                            </p>
                        </div>
                    </div>
                </DialogDescription>
            </DialogHeader>
            <div className="flex flex-col items-center mt-6">
                <div
                    className="w-full bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-4 shadow-sm">
                    <div
                        className="bg-white dark:bg-gray-800 p-4 rounded-md font-mono text-sm break-all border border-gray-100 dark:border-gray-700 shadow-inner">
                        {apikey}
                    </div>
                </div>
                <div className="flex items-center mt-4 text-amber-600 dark:text-amber-500">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2" viewBox="0 0 20 20"
                         fill="currentColor">
                        <path fillRule="evenodd"
                              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2h-1V9z"
                              clipRule="evenodd"/>
                    </svg>
                    <p className="text-sm">
                        请妥善保管您的API Key，不要与他人分享。
                    </p>
                </div>
                <div
                    className="bg-amber-50 dark:bg-amber-900/30 p-3 rounded-md border border-amber-100 dark:border-amber-800">
                    <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                        此API Key
                        仅用于openapi的接口请求鉴权。后续申请额度等操作，需要填写的并非此apikey，而是<span
                        className="font-bold underline">ak code</span>（即apikey的id，不是用于身份验证的密钥），获取方式为：点击每一行操作栏中的复制按钮。
                    </p>
                </div>
            </div>
            <DialogFooter className="flex flex-col sm:flex-row gap-3 mt-6">
                <Button
                    type="button"
                    variant="outline"
                    onClick={handleCopyApiKey}
                    className="w-full relative overflow-hidden group"
                >
                            <span
                                className={`absolute inset-0 flex items-center justify-center transition-opacity duration-300 ${copied ? 'opacity-100' : 'opacity-0'}`}>
                                <Check className="h-4 w-4 mr-2"/>
                                已复制
                            </span>
                    <span
                        className={`flex items-center justify-center transition-opacity duration-300 ${copied ? 'opacity-0' : 'opacity-100'}`}>
                                <Copy className="h-4 w-4 mr-2"/>
                                复制API Key
                            </span>
                </Button>
                <Button
                    type="button"
                    onClick={() => setShowDialog(false)}
                    className="w-full bg-gray-800 hover:bg-gray-900"
                >
                    确认并关闭
                </Button>
            </DialogFooter>
        </DialogContent>
    </Dialog>
}
