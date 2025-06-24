'use client'

import React, {useEffect, useState, useMemo, useCallback} from "react"
import {useParams, useRouter} from "next/navigation"
import {DataTable} from "@/components/ui/data-table"
import {getApikeyInfos, getApikeyByCode, getSafetyLevel} from "@/lib/api/apikey"
import {SubApikeyColumns} from "@/components/apikey/sub-apikey-column"
import {ApikeyInfo} from "@/lib/types/openapi"
import {ClientHeader} from "@/components/user/client-header"
import {Button} from "@/components/ui/button"
import {Input} from "@/components/ui/input"
import {ArrowLeft, ChevronLeft, ChevronRight, Plus, Search} from "lucide-react"
import {useUser} from "@/lib/context/user-context"
import {useToast} from "@/hooks/use-toast"
import {SubApikeyDialog} from "@/components/apikey/sub-apikey-dialog"
import {CopyApikeyDialog} from "@/components/apikey/copy-apikey-dialog";

const SubApikeyPage: React.FC = () => {
    const params = useParams()
    const router = useRouter()
    const akCode = params.akCode as string
    const [page, setPage] = useState<number>(1)
    const [data, setData] = useState<ApikeyInfo[] | null>(null)
    const [totalPages, setTotalPages] = useState<number>(1)
    const [isLoading, setIsLoading] = useState<boolean>(true)
    const [searchTerm, setSearchTerm] = useState<string>("")
    const [parentApikey, setParentApikey] = useState<ApikeyInfo | null>(null)
    const [currentSubApikey, setCurrentSubApikey] = useState<ApikeyInfo | null>(null)
    const [showSubApikeyDialog, setShowSubApikeyDialog] = useState<boolean>(false)
    const {userInfo} = useUser()
    const {toast} = useToast()
    const [newApiKey, setNewApiKey] = useState<string | null>(null)
    const [showCopyDialog, setShowCopyDialog] = useState<boolean>(false)
    const [copied, setCopied] = useState<boolean>(false)

    // 局部更新函数
    const updateApiKeyInPlace = useCallback((code: string, updates: Partial<ApikeyInfo>) => {
        setData(currentData => {
            if (!currentData) return currentData
            return currentData.map(item => 
                item.code === code ? { ...item, ...updates } : item
            )
        })
    }, [])

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


    const refresh = useCallback(async () => {
        setIsLoading(true)
        if (userInfo) {
            try {
                // 获取子API Key列表
                const res = await getApikeyInfos(page, userInfo?.userId || null, searchTerm || null, akCode)
                setData(res?.data || null)
                if (res) {
                    setTotalPages(Math.ceil(res.total / 10))
                } else {
                    setTotalPages(1)
                }
            } catch (error) {
                console.error('Failed to fetch sub API keys:', error)
                setData(null)
            } finally {
                setIsLoading(false)
            }
        }
    }, [page, userInfo, searchTerm, akCode])

    const loadParentApikey = useCallback(async () => {
        try {
            const parent = await getApikeyByCode(akCode)
            setParentApikey(parent)
        } catch (error) {
            console.error('Failed to fetch parent API key:', error)
            toast({
                title: "错误",
                description: "无法获取父API Key信息",
                variant: "destructive",
            })
        }
    }, [akCode, toast])

    useEffect(() => {
        loadParentApikey()
        refresh()
    }, [loadParentApikey, refresh])

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

    const handleCreateSuccess = useCallback((apikey: string | null, isUpdate?: boolean, updatedData?: ApikeyInfo) => {
        setShowSubApikeyDialog(false)
        
        if (isUpdate && updatedData) {
            // 局部更新现有项目
            updateApiKeyInPlace(updatedData.code, updatedData)
        } else {
            // 创建新项目，需要刷新列表
            refresh()
        }
        
        if(apikey) {
            handleCopyDialog(apikey)
        }
    }, [refresh, updateApiKeyInPlace])

    const handleCopyDialog = useCallback((apikey: string) => {
        setNewApiKey(apikey)
        setShowCopyDialog(true)
    }, [])

    const handleDialogClose = useCallback(() => {
        setShowSubApikeyDialog(false)
        setCurrentSubApikey(null)
    }, [])

    const handleBack = useCallback(() => {
        router.push('/apikey')
    }, [router])

    const columns = useMemo(() => SubApikeyColumns(setCurrentSubApikey, setShowSubApikeyDialog, handleCopyDialog, refresh, updateApiKeyInPlace), [handleCopyDialog, refresh, updateApiKeyInPlace])

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title={`子API Key管理 - ${parentApikey?.name || akCode}`}/>
            <div className="container mx-auto py-8 px-4 sm:px-6 lg:px-8">
                <div className="p-6">
                    <div className="mb-4 flex items-center justify-between">
                        <div className="flex items-center space-x-4">
                            <Button
                                onClick={handleBack}
                                variant="outline"
                                size="sm"
                                className="text-gray-600 hover:bg-gray-50 border-gray-200"
                            >
                                <ArrowLeft className="h-4 w-4 mr-2"/>
                                返回
                            </Button>
                            <form onSubmit={handleSearchSubmit} className="relative">
                                <Search
                                    className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4"/>
                                <Input
                                    type="text"
                                    placeholder="搜索子API Key名称"
                                    value={searchTerm}
                                    onChange={handleSearch}
                                    className="pl-10 w-64"
                                />
                            </form>
                        </div>
                        <Button
                            onClick={() => {
                                setCurrentSubApikey(null)
                                setShowSubApikeyDialog(true)
                            }}
                            className="bg-gray-700 hover:bg-gray-900 text-white"
                            disabled={!parentApikey}
                        >
                            <Plus className="h-4 w-4 mr-2"/>
                            创建子API Key
                        </Button>
                    </div>

                    {parentApikey && (
                        <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                            <h3 className="font-semibold text-blue-900 mb-2">父API Key信息</h3>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                                {parentApikey.name && <div>
                                    <span className="text-blue-700 font-medium">名称：</span>
                                    <span className="text-blue-800">{parentApikey.name}</span>
                                </div>}
                                <div>
                                    <span className="text-blue-700 font-medium">安全等级：</span>
                                    <span className="text-blue-800">{getSafetyLevel(parentApikey.safetyLevel)}</span>
                                </div>
                                <div>
                                    <span className="text-blue-700 font-medium">月配额：</span>
                                    <span className="text-blue-800">¥{parentApikey.monthQuota}</span>
                                </div>
                                <div>
                                    <span className="text-blue-700 font-medium">AK：</span>
                                    <span className="text-blue-800 font-mono">{parentApikey.akDisplay}</span>
                                </div>
                            </div>
                        </div>
                    )}

                    {isLoading ? (
                        <div className="flex justify-center items-center h-64">
                            <div
                                className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
                        </div>
                    ) : data ? (
                        <DataTable columns={columns} data={data}/>
                    ) : (
                        <p className="text-center text-gray-500">暂无子API Key。</p>
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

            {parentApikey && (
                <SubApikeyDialog
                    isOpen={showSubApikeyDialog}
                    onClose={handleDialogClose}
                    onSuccess={handleCreateSuccess}
                    parentApikey={parentApikey}
                    currentSubApikey={currentSubApikey}
                />
            )}
            <CopyApikeyDialog showDialog={showCopyDialog} setShowDialog={setShowCopyDialog} apikey={newApiKey} handleCopyApiKey={handleCopyApiKey} copied={copied}/>
        </div>
    )
}

export default SubApikeyPage
