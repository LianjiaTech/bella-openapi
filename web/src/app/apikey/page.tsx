'use client'

import React, {useEffect, useState, useMemo, useCallback} from "react"
import {DataTable} from "@/components/ui/data-table"
import {applyApikey, getApikeyInfos} from "@/lib/api/apikey"
import {ApikeyColumns} from "@/components/apikey/apikey-column"
import {ApikeyInfo} from "@/lib/types/openapi"
import {ClientHeader} from "@/components/user/client-header"
import {Button} from "@/components/ui/button"
import {Input} from "@/components/ui/input"
import {ChevronLeft, ChevronRight, Plus, Search, Users} from "lucide-react"
import {useUser} from "@/lib/context/user-context"
import {useToast} from "@/hooks/use-toast"
import {CopyApikeyDialog} from "@/components/apikey/copy-apikey-dialog";
import {SubApikeyTipsDialog} from "@/components/apikey/sub-apikey-tips-dialog";

const ApikeyPage: React.FC = () => {
    const [page, setPage] = useState<number>(1)
    const [data, setData] = useState<ApikeyInfo[] | null>(null)
    const [totalPages, setTotalPages] = useState<number>(1)
    const [isLoading, setIsLoading] = useState<boolean>(true)
    const [searchTerm, setSearchTerm] = useState<string>("")
    const [newApiKey, setNewApiKey] = useState<string | null>(null)
    const [showDialog, setShowDialog] = useState<boolean>(false)
    const [showSubApikeyTipsDialog, setShowSubApikeyTipsDialog] = useState<boolean>(false)
    const [copied, setCopied] = useState<boolean>(false)
    const {userInfo} = useUser()
    const {toast} = useToast()

    // 局部更新函数
    const updateApiKeyInPlace = useCallback((code: string, updates: Partial<ApikeyInfo>) => {
        setData(currentData => {
            if (!currentData) return currentData
            return currentData.map(item =>
                item.code === code ? { ...item, ...updates } : item
            )
        })
    }, [])

    const refresh = useCallback(async () => {
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
    }, [page, userInfo, searchTerm])

    const showApikey = useCallback(async (apikey: string) => {
        await refresh()
        setNewApiKey(apikey)
        setShowDialog(true)
        setCopied(false)
    }, [refresh])

    useEffect(() => {
        refresh()
    }, [refresh])

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

    const handleSubApikeyTipsDialog = () => {
        setShowSubApikeyTipsDialog(true)
    }

    const columns = useMemo(() => ApikeyColumns(refresh, showApikey, updateApiKeyInPlace), [refresh, showApikey, updateApiKeyInPlace])

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
                        <div className="flex space-x-2">
                            <Button onClick={handleSubApikeyTipsDialog} className="bg-gray-700 hover:bg-gray-900 text-white ">
                                <Users className="h-4 w-4"/><p>管理子ak</p>
                            </Button>
                            <Button onClick={handleCreateApiKey} className="bg-gray-700 hover:bg-gray-900 text-white">
                                <Plus className="h-4 w-4 mr-2"/>
                                创建 API Key
                            </Button>
                        </div>
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
            <CopyApikeyDialog showDialog={showDialog} setShowDialog={setShowDialog} apikey={newApiKey} handleCopyApiKey={handleCopyApiKey} copied={copied}/>
            <SubApikeyTipsDialog showDialog={showSubApikeyTipsDialog} setShowDialog={setShowSubApikeyTipsDialog} />
        </div>
    )
}

export default ApikeyPage
