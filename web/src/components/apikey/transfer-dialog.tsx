'use client'

import React, { useState, useEffect, useCallback, useRef } from 'react'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Search, User, Mail, ExternalLink, AlertCircle, ArrowLeft, History } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import { searchUsers } from "@/lib/api/user"
import { transferApikey, getTransferHistory } from "@/lib/api/apikey"
import { UserSearchResult, TransferApikeyRequest, ApikeyTransferLog } from "@/lib/types/openapi"
import { Alert, AlertDescription } from "@/components/ui/alert"

interface TransferDialogProps {
    isOpen: boolean
    onClose: () => void
    akCode: string
    displayName: string
    onTransferSuccess: () => void
}

export const TransferDialog: React.FC<TransferDialogProps> = ({
    isOpen,
    onClose,
    akCode,
    displayName,
    onTransferSuccess
}) => {
    const [searchKeyword, setSearchKeyword] = useState('')
    const [searchResults, setSearchResults] = useState<UserSearchResult[]>([])
    const [selectedUser, setSelectedUser] = useState<UserSearchResult | null>(null)
    const [transferReason, setTransferReason] = useState('')
    const [isSearching, setIsSearching] = useState(false)
    const [isTransferring, setIsTransferring] = useState(false)
    const [searchError, setSearchError] = useState('')
    const [step, setStep] = useState<'search' | 'confirm' | 'history'>('search')
    const [transferHistory, setTransferHistory] = useState<ApikeyTransferLog[]>([])
    const [isLoadingHistory, setIsLoadingHistory] = useState(false)
    const [isComposing, setIsComposing] = useState(false) // 处理中文输入法
    const searchInputRef = useRef<HTMLInputElement>(null) // 输入框引用
    const { toast } = useToast()

    // 搜索用户
    const handleSearch = useCallback(async () => {
        const keyword = searchKeyword.trim()
        if (!keyword) {
            setSearchResults([])
            setSearchError('')
            return
        }

        setIsSearching(true)
        setSearchError('')
        
        try {
            const results = await searchUsers(keyword, 20, true) // excludeSelf=true 排除自己
            setSearchResults(results)
            setSearchError('') // 搜索成功时清除错误信息
        } catch (error) {
            console.error('Error searching users:', error)
            setSearchError('搜索失败，请重试')
            setSearchResults([])
        } finally {
            setIsSearching(false)
        }
    }, [searchKeyword])

    // 自动搜索 - 防抖（避免中文输入法干扰）
    useEffect(() => {
        if (isComposing) return // 中文输入法输入中时不触发搜索
        
        const timer = setTimeout(() => {
            handleSearch()
        }, 500) // 500ms 防抖

        return () => clearTimeout(timer)
    }, [handleSearch, isComposing])

    // 搜索完成后恢复输入框焦点
    useEffect(() => {
        if (!isSearching && step === 'search') {
            // 使用较长的延迟确保 DOM 更新完成
            const timer = setTimeout(() => {
                searchInputRef.current?.focus()
            }, 100)
            return () => clearTimeout(timer)
        }
    }, [isSearching, step])

    // 重置状态
    const resetState = useCallback(() => {
        setSearchKeyword('')
        setSearchResults([])
        setSelectedUser(null)
        setTransferReason('')
        setSearchError('')
        setTransferHistory([])
        setStep('search')
    }, [])

    // 关闭对话框
    const handleClose = useCallback(() => {
        resetState()
        onClose()
    }, [resetState, onClose])



    // 选择用户
    const handleSelectUser = useCallback((user: UserSearchResult) => {
        setSelectedUser(user)
        setStep('confirm')
    }, [])

    // 返回
    const handleBackToSearch = useCallback(() => {
        setSelectedUser(null)
        setTransferReason('')
        setStep('search')
    }, [])

    // 查看转交历史
    const handleViewHistory = useCallback(async () => {
        setIsLoadingHistory(true)
        try {
            const history = await getTransferHistory(akCode)
            setTransferHistory(history)
            setStep('history')
        } catch (error) {
            console.error('Error fetching transfer history:', error)
            toast({
                title: "获取转交历史失败",
                description: "无法获取转交历史记录，请重试",
                variant: "destructive",
            })
        } finally {
            setIsLoadingHistory(false)
        }
    }, [akCode, toast])

    // 执行转交
    const handleTransfer = useCallback(async () => {
        if (!selectedUser) return
        if (!transferReason.trim()) {
            toast({
                title: "错误",
                description: "请填写转交原因",
                variant: "destructive",
            })
            return
        }

        setIsTransferring(true)

        try {
            const request: TransferApikeyRequest = {
                akCode,
                targetUserId: selectedUser.id,
                transferReason: transferReason.trim()
            }

            const success = await transferApikey(request)
            
            if (success) {
                toast({
                    title: "转交成功",
                    description: `API Key "${displayName}" 已成功转交给 ${selectedUser.userName}`,
                })
                onTransferSuccess()
                handleClose()
            } else {
                toast({
                    title: "转交失败",
                    description: "转交过程中发生错误，请重试",
                    variant: "destructive",
                })
            }
        } catch (error) {
            console.error('Transfer error:', error)
            toast({
                title: "转交失败",
                description: "转交过程中发生错误，请重试",
                variant: "destructive",
            })
        } finally {
            setIsTransferring(false)
        }
    }, [selectedUser, transferReason, akCode, displayName, toast, onTransferSuccess, handleClose])

    // 按回车搜索
    const handleKeyPress = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !isSearching) {
            handleSearch()
        }
    }, [handleSearch, isSearching])

    // 防止搜索期间失去焦点
    const handleInputBlur = useCallback((e: React.FocusEvent<HTMLInputElement>) => {
        if (isSearching) {
            // 搜索期间不允许失去焦点
            e.preventDefault()
            searchInputRef.current?.focus()
        }
    }, [isSearching])



    // 获取状态显示
    const getStatusDisplay = (status: string) => {
        const statusMap: Record<string, { text: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
            'SUCCESS': { text: '成功', variant: 'default' },
            'PENDING': { text: '处理中', variant: 'secondary' },
            'FAILED': { text: '失败', variant: 'destructive' }
        }
        return statusMap[status] || { text: status, variant: 'outline' }
    }

    return (
        <Dialog open={isOpen} onOpenChange={handleClose}>
            <DialogContent className="max-w-2xl max-h-[80vh] overflow-hidden flex flex-col bg-white border border-gray-200 shadow-lg">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        {(step === 'confirm' || step === 'history') && (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={handleBackToSearch}
                                className="p-1 h-auto"
                            >
                                <ArrowLeft className="h-4 w-4" />
                            </Button>
                        )}
                        {step === 'search' && '转交 API Key - 搜索用户'}
                        {step === 'confirm' && '转交 API Key - 确认转交'}
                        {step === 'history' && '转交历史 - ' + (displayName)}
                    </DialogTitle>
                    <DialogDescription>
                        {step === 'search' && `搜索并选择要转交 "${displayName}" 的目标用户`}
                        {step === 'confirm' && `确认将 "${displayName}" 转交给所选用户`}
                        {step === 'history' && `查看此 API Key 的所有转交记录`}
                    </DialogDescription>
                </DialogHeader>

                <div className="flex-1 overflow-hidden">
                    {step === 'search' ? (
                        <div className="space-y-4">
                            {/* 搜索输入框 */}
                            <div className="space-y-2">
                                <div className="flex items-center justify-between">
                                    <Label htmlFor="search">搜索用户</Label>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={handleViewHistory}
                                        disabled={isLoadingHistory}
                                        className="text-gray-600"
                                    >
                                        <History className="h-3 w-3 mr-1" />
                                        {isLoadingHistory ? '加载中...' : '查看转交历史'}
                                    </Button>
                                </div>
                                <div className="relative">
                                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                                    <Input
                                        ref={searchInputRef}
                                        id="search"
                                        placeholder="输入用户名、邮箱或ID进行搜索（回车搜索）"
                                        value={searchKeyword}
                                        onChange={(e) => setSearchKeyword(e.target.value)}
                                        onKeyPress={handleKeyPress}
                                        onCompositionStart={() => setIsComposing(true)}
                                        onCompositionEnd={() => setIsComposing(false)}
                                        onBlur={handleInputBlur}
                                        className="pl-10 pr-10"
                                    />
                                    {isSearching && (
                                        <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
                                            <div className="animate-spin h-4 w-4 border-2 border-blue-500 border-t-transparent rounded-full"></div>
                                        </div>
                                    )}
                                </div>
                                {searchError && (
                                    <Alert variant="destructive">
                                        <AlertCircle className="h-4 w-4" />
                                        <AlertDescription>{searchError}</AlertDescription>
                                    </Alert>
                                )}
                            </div>

                            {/* 搜索结果 */}
                            {searchResults.length > 0 && (
                                <div className="space-y-2">
                                    <Label>搜索结果 ({searchResults.length})</Label>
                                    <div className="max-h-96 overflow-y-auto space-y-2">
                                        {searchResults.map((user) => (
                                            <Card 
                                                key={user.id} 
                                                className="cursor-pointer hover:bg-gray-50 transition-colors"
                                                onClick={() => handleSelectUser(user)}
                                            >
                                                <CardContent className="p-4">
                                                    <div className="flex items-center justify-between">
                                                        <div className="flex items-center space-x-3">
                                                            <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">
                                                                <User className="h-5 w-5 text-blue-600" />
                                                            </div>
                                                            <div>
                                                                <div className="flex items-center gap-2">
                                                                    <span className="font-medium">{user.userName}</span>
                                                                    <Badge variant="secondary" className="text-xs">
                                                                        {user.source}
                                                                    </Badge>
                                                                </div>
                                                                <div className="flex items-center text-sm text-gray-500 mt-1">
                                                                    <Mail className="h-3 w-3 mr-1" />
                                                                    {user.email}
                                                                </div>
                                                                <div className="text-xs text-gray-400 mt-1">
                                                                    ID: {user.sourceId}
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <ExternalLink className="h-4 w-4 text-gray-400" />
                                                    </div>
                                                </CardContent>
                                            </Card>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                    ) : step === 'confirm' ? (
                        <div className="space-y-4">
                            {/* 选中用户信息 */}
                            {selectedUser && (
                                <div className="space-y-2">
                                    <Label>转交目标用户</Label>
                                    <Card>
                                        <CardContent className="p-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                                                    <User className="h-6 w-6 text-blue-600" />
                                                </div>
                                                <div>
                                                    <div className="flex items-center gap-2">
                                                        <span className="font-medium text-lg">{selectedUser.userName}</span>
                                                        <Badge variant="secondary">
                                                            {selectedUser.source}
                                                        </Badge>
                                                    </div>
                                                    <div className="flex items-center text-sm text-gray-500 mt-1">
                                                        <Mail className="h-3 w-3 mr-1" />
                                                        {selectedUser.email}
                                                    </div>
                                                    <div className="text-xs text-gray-400 mt-1">
                                                        用户ID: {selectedUser.sourceId}
                                                    </div>
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                </div>
                            )}

                            {/* 转交原因 */}
                            <div className="space-y-2">
                                <Label htmlFor="reason">转交原因 *</Label>
                                <Textarea
                                    id="reason"
                                    placeholder="请说明转交此 API Key 的原因..."
                                    value={transferReason}
                                    onChange={(e) => setTransferReason(e.target.value)}
                                    rows={4}
                                    className="resize-none"
                                />
                            </div>

                            {/* 转交警告 */}
                            <Alert>
                                <AlertCircle className="h-4 w-4" />
                                <AlertDescription>
                                    <strong>注意：</strong>转交后，此 API Key 的所有权将完全转移给目标用户，您将失去对此 API Key 的控制权。此操作不可撤销，请谨慎操作。
                                </AlertDescription>
                            </Alert>
                        </div>
                    ) : (
                        <div className="space-y-4 overflow-y-auto max-h-[60vh] pr-1">
                            {/* 转交历史列表 */}
                            {transferHistory.length === 0 ? (
                                <div className="flex items-center justify-center py-8">
                                    <div className="text-gray-500">暂无转交记录</div>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {transferHistory.map((log) => {
                                        const status = getStatusDisplay(log.status)
                                        return (
                                            <Card key={log.id}>
                                                <CardContent className="p-4">
                                                    <div className="flex items-start justify-between">
                                                        <div className="space-y-2">
                                                            <div className="flex items-center gap-2">
                                                                <Badge variant={status.variant}>
                                                                    {status.text}
                                                                </Badge>
                                                                <span className="text-sm text-gray-500">
                                                                    {new Date(log.ctime).toLocaleString()}
                                                                </span>
                                                            </div>
                                                            <div className="text-sm">
                                                                <span className="font-medium">{log.fromOwnerName}</span>
                                                                <ExternalLink className="inline h-3 w-3 mx-2" />
                                                                <span className="font-medium">{log.toOwnerName}</span>
                                                            </div>
                                                            {log.transferReason && (
                                                                <div className="text-sm text-gray-600">
                                                                    <span className="font-medium">原因：</span>
                                                                    {log.transferReason}
                                                                </div>
                                                            )}
                                                            <div className="text-xs text-gray-400">
                                                                操作人：{log.operatorName}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </CardContent>
                                            </Card>
                                        )
                                    })}
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <DialogFooter>
                    {step === 'search' ? (
                        <Button variant="outline" onClick={handleClose}>
                            取消
                        </Button>
                    ) : step === 'confirm' ? (
                        <div className="flex gap-2">
                            <Button variant="outline" onClick={handleBackToSearch}>
                                返回
                            </Button>
                            <Button 
                                onClick={handleTransfer}
                                disabled={isTransferring || !transferReason.trim()}
                                className="bg-red-600 hover:bg-red-700"
                            >
                                {isTransferring ? '转交中...' : '确认转交'}
                            </Button>
                        </div>
                    ) : (
                        <Button variant="outline" onClick={handleBackToSearch}>
                            返回
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}
