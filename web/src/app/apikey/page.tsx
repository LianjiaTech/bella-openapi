'use client'

import React, {useEffect, useState} from "react"
import {DataTable} from "@/components/ui/data-table"
import {applyApikey, getApikeyInfos} from "@/lib/api/apikey"
import {ApikeyColumns} from "@/components/apikey/apikey-coloumn"
import {ApikeyInfo} from "@/lib/types/openapi"
import {ClientHeader} from "@/components/user/client-header"
import {Button} from "@/components/ui/button"
import {Input} from "@/components/ui/input"
import {Check, ChevronLeft, ChevronRight, Copy, Plus, Search} from "lucide-react"
import {useUser} from "@/lib/context/user-context"
import {useToast} from "@/hooks/use-toast"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"

const ApikeyPage: React.FC = () => {
    const [page, setPage] = useState<number>(1)
    const [data, setData] = useState<ApikeyInfo[] | null>(null)
    const [totalPages, setTotalPages] = useState<number>(1)
    const [isLoading, setIsLoading] = useState<boolean>(true)
    const [searchTerm, setSearchTerm] = useState<string>("")
    const [newApiKey, setNewApiKey] = useState<string | null>(null)
    const [showDialog, setShowDialog] = useState<boolean>(false)
    const [copied, setCopied] = useState<boolean>(false)
    const {userInfo} = useUser()
    const {toast} = useToast()

    const refresh = async () => {
        setIsLoading(true)
        if (userInfo) {
            try {
                const res = await getApikeyInfos(page, userInfo?.userId || null, searchTerm || null)
                setData(res?.data || null)
                if (res) {
                    setTotalPages(Math.ceil(res.total / 10))
                } else {
                    setTotalPages(1)
                }
            } catch (error) {
                console.error('Failed to fetch API keys:', error)
                setData(null)
            } finally {
                setIsLoading(false)
            }
        }
    }

    const showApikey = async (apikey: string) => {
        await refresh()
        setNewApiKey(apikey)
        setShowDialog(true)
        setCopied(false)
    }

    useEffect(() => {
        refresh()
    }, [page, userInfo])

    const handlePageChange = (newPage: number) => {
        setPage(newPage)
    }

    const handleSearch = (event: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(event.target.value)
    }

    const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        setPage(1)
        refresh()
    }

    const handleCopyApiKey = () => {
        if (newApiKey) {
            navigator.clipboard.writeText(newApiKey).then(() => {
                setCopied(true)
                setTimeout(() => setCopied(false), 2000)
                toast({
                    title: "已复制",
                    description: "API Key 已复制到剪贴板",
                })
            }).catch(err => {
                console.error('复制失败:', err)
                toast({
                    title: "复制失败",
                    description: "无法复制到剪贴板，请手动复制",
                    variant: "destructive",
                })
            })
        }
    }

    const handleCreateApiKey = async () => {
        if (!userInfo) {
            toast({
                title: "错误",
                description: "用户未登录，无法创建 API Key",
                variant: "destructive",
            })
            return
        }

        try {
            setIsLoading(true)
            const apikey = await applyApikey(userInfo.userId.toString(), userInfo.userName)
            if (apikey) {
                showApikey(apikey)
            } else {
                toast({
                    title: "错误",
                    description: "创建 API Key 失败",
                    variant: "destructive",
                })
            }
        } catch (error) {
            console.error('Failed to create API key:', error)
            toast({
                title: "错误",
                description: "创建 API Key 时发生错误",
                variant: "destructive",
            })
        } finally {
            setIsLoading(false)
        }
    }

    const columns = ApikeyColumns(refresh, showApikey)

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='API Key 管理'/>
            <div className="container mx-auto py-8 px-4 sm:px-6 lg:px-8">
                <div className="p-6">
                    <div className="mb-4 flex justify-between items-center">
                        <form onSubmit={handleSearchSubmit} className="relative">
                            <Search
                                className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4"/>
                            <Input
                                type="text"
                                placeholder="搜索 API Key 名称"
                                value={searchTerm}
                                onChange={handleSearch}
                                className="pl-10 w-64"
                            />
                        </form>
                        <Button onClick={handleCreateApiKey} className="bg-gray-700 hover:bg-gray-900 text-white">
                            <Plus className="h-4 w-4 mr-2"/>
                            创建 API Key
                        </Button>
                    </div>

                    {isLoading ? (
                        <div className="flex justify-center items-center h-64">
                            <div
                                className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
                        </div>
                    ) : data ? (
                        <DataTable columns={columns} data={data}/>
                    ) : (
                        <p className="text-center text-gray-500">No API keys found.</p>
                    )}
                    <div className="mt-6 flex items-center justify-between">
                        <div className="flex items-center space-x-2">
                            <Button
                                onClick={() => handlePageChange(page - 1)}
                                disabled={page === 1 || isLoading}
                                variant="outline"
                                size="sm"
                                className="text-gray-600 hover:bg-gray-50 border-gray-200"
                            >
                                <ChevronLeft className="h-4 w-4 mr-2"/>
                                上一页
                            </Button>
                            <Button
                                onClick={() => handlePageChange(page + 1)}
                                disabled={page === totalPages || isLoading}
                                variant="outline"
                                size="sm"
                                className="text-gray-600 hover:bg-gray-50 border-gray-200"
                            >
                                下一页
                                <ChevronRight className="h-4 w-4 ml-2"/>
                            </Button>
                        </div>
                        <span className="text-sm text-gray-600">
                            第 {page} 页，共 {totalPages} 页
                        </span>
                    </div>
                </div>
            </div>

            {/* API Key 创建成功弹窗 */}
            <Dialog open={showDialog} onOpenChange={setShowDialog}>
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
                                {newApiKey}
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
        </div>
    )
}

export default ApikeyPage
