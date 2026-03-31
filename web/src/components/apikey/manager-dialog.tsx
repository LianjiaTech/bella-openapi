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
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Search, User, Mail, AlertCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import { searchUsers } from "@/lib/api/user"
import { updateManager } from "@/lib/api/apikey"
import { UserSearchResult } from "@/lib/types/openapi"
import { Alert, AlertDescription } from "@/components/ui/alert"

interface ManagerDialogProps {
    isOpen: boolean
    onClose: () => void
    akCode: string
    currentManagerName?: string
    onSuccess: (managerCode: string, managerName: string) => void
}

export const ManagerDialog: React.FC<ManagerDialogProps> = ({
    isOpen,
    onClose,
    akCode,
    currentManagerName,
    onSuccess
}) => {
    const [searchKeyword, setSearchKeyword] = useState('')
    const [searchResults, setSearchResults] = useState<UserSearchResult[]>([])
    const [isSearching, setIsSearching] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [searchError, setSearchError] = useState('')
    const [isComposing, setIsComposing] = useState(false)
    const searchInputRef = useRef<HTMLInputElement>(null)
    const { toast } = useToast()

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
            const results = await searchUsers(keyword, 20, false)
            setSearchResults(results)
        } catch (error) {
            console.error('Error searching users:', error)
            setSearchError('搜索失败，请重试')
            setSearchResults([])
        } finally {
            setIsSearching(false)
        }
    }, [searchKeyword])

    useEffect(() => {
        if (isComposing) return
        const timer = setTimeout(() => { handleSearch() }, 500)
        return () => clearTimeout(timer)
    }, [handleSearch, isComposing])

    useEffect(() => {
        if (!isSearching) {
            const timer = setTimeout(() => { searchInputRef.current?.focus() }, 100)
            return () => clearTimeout(timer)
        }
    }, [isSearching])

    const resetState = useCallback(() => {
        setSearchKeyword('')
        setSearchResults([])
        setSearchError('')
    }, [])

    const handleClose = useCallback(() => {
        resetState()
        onClose()
    }, [resetState, onClose])

    const handleSelectUser = useCallback(async (user: UserSearchResult) => {
        setIsSaving(true)
        try {
            await updateManager({
                code: akCode,
                managerUserId: user.id,
            })
            toast({
                title: "设置成功",
                description: `管理人已设置为 ${user.userName}`,
            })
            onSuccess(user.id.toString(), user.userName)
            handleClose()
        } catch (error) {
            console.error('Error updating manager:', error)
            toast({
                title: "设置失败",
                description: "设置管理人时发生错误，请重试",
                variant: "destructive",
            })
        } finally {
            setIsSaving(false)
        }
    }, [akCode, toast, onSuccess, handleClose])

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !isComposing && !isSearching) handleSearch()
    }, [handleSearch, isComposing, isSearching])

    return (
        <Dialog open={isOpen} onOpenChange={handleClose}>
            <DialogContent className="max-w-xl max-h-[75vh] overflow-hidden flex flex-col bg-white border border-gray-200 shadow-lg">
                <DialogHeader>
                    <DialogTitle>设置管理人</DialogTitle>
                    <DialogDescription>
                        {currentManagerName
                            ? `当前管理人：${currentManagerName}，搜索并选择新的管理人`
                            : '搜索并选择管理人'}
                    </DialogDescription>
                </DialogHeader>

                <div className="flex-1 overflow-hidden space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="manager-search">搜索用户</Label>
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                            <Input
                                ref={searchInputRef}
                                id="manager-search"
                                placeholder="输入用户名、邮箱或ID进行搜索"
                                value={searchKeyword}
                                onChange={(e) => setSearchKeyword(e.target.value)}
                                onKeyDown={handleKeyDown}
                                onCompositionStart={() => setIsComposing(true)}
                                onCompositionEnd={() => setIsComposing(false)}
                                className="pl-10 pr-10"
                                disabled={isSaving}
                            />
                            {isSearching && (
                                <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
                                    <div className="animate-spin h-4 w-4 border-2 border-blue-500 border-t-transparent rounded-full" />
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

                    {searchResults.length > 0 && (
                        <div className="space-y-2">
                            <Label>搜索结果 ({searchResults.length})，点击选择</Label>
                            <div className="max-h-80 overflow-y-auto space-y-2">
                                {searchResults.map((user) => (
                                    <Card
                                        key={user.id}
                                        className="cursor-pointer hover:bg-gray-50 transition-colors"
                                        onClick={() => !isSaving && handleSelectUser(user)}
                                    >
                                        <CardContent className="p-4">
                                            <div className="flex items-center space-x-3">
                                                <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">
                                                    <User className="h-5 w-5 text-blue-600" />
                                                </div>
                                                <div>
                                                    <div className="flex items-center gap-2">
                                                        <span className="font-medium">{user.userName}</span>
                                                        <Badge variant="secondary" className="text-xs">{user.source}</Badge>
                                                    </div>
                                                    <div className="flex items-center text-sm text-gray-500 mt-1">
                                                        <Mail className="h-3 w-3 mr-1" />
                                                        {user.email}
                                                    </div>
                                                    <div className="text-xs text-gray-400 mt-1">ID: {user.sourceId}</div>
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={handleClose} disabled={isSaving}>取消</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}
