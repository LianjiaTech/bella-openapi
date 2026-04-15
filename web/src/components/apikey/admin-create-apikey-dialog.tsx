'use client'

import React, { useState, useCallback, useEffect, useRef } from 'react'
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Search, User } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import { searchUsers } from "@/lib/api/user"
import { applyApikey } from "@/lib/api/apikey"
import { UserSearchResult } from "@/lib/types/openapi"
import { CopyApikeyDialog } from "./copy-apikey-dialog"

interface AdminCreateApikeyDialogProps {
    isOpen: boolean
    onClose: () => void
    onSuccess: () => void
}

export const AdminCreateApikeyDialog: React.FC<AdminCreateApikeyDialogProps> = ({
    isOpen,
    onClose,
    onSuccess,
}) => {
    const [ownerType, setOwnerType] = useState<'person' | 'org' | 'project'>('person')
    const [monthQuota, setMonthQuota] = useState<string>('50')

    // person 模式
    const [userSearch, setUserSearch] = useState('')
    const [userSearchResults, setUserSearchResults] = useState<UserSearchResult[]>([])
    const [isSearching, setIsSearching] = useState(false)
    const [selectedUser, setSelectedUser] = useState<UserSearchResult | null>(null)
    const [isComposing, setIsComposing] = useState(false)

    // org / project 模式
    const [orgCode, setOrgCode] = useState('')
    const [orgName, setOrgName] = useState('')

    const [isSubmitting, setIsSubmitting] = useState(false)
    const [newApiKey, setNewApiKey] = useState<string | null>(null)
    const [showCopyDialog, setShowCopyDialog] = useState(false)
    const [copied, setCopied] = useState(false)

    const searchInputRef = useRef<HTMLInputElement>(null)
    const { toast } = useToast()

    const handleUserSearch = useCallback(async () => {
        const keyword = userSearch.trim()
        if (!keyword) { setUserSearchResults([]); return }
        setIsSearching(true)
        try {
            const results = await searchUsers(keyword, 20, false)
            setUserSearchResults(results)
        } catch {
            setUserSearchResults([])
        } finally {
            setIsSearching(false)
        }
    }, [userSearch])

    useEffect(() => {
        if (isComposing) return
        const timer = setTimeout(() => { handleUserSearch() }, 500)
        return () => clearTimeout(timer)
    }, [handleUserSearch, isComposing])

    const resetState = useCallback(() => {
        setOwnerType('person')
        setMonthQuota('50')
        setUserSearch('')
        setUserSearchResults([])
        setSelectedUser(null)
        setOrgCode('')
        setOrgName('')
    }, [])

    const handleOwnerTypeChange = (v: 'person' | 'org' | 'project') => {
        setOwnerType(v)
        setSelectedUser(null)
        setUserSearch('')
        setUserSearchResults([])
        setOrgCode('')
        setOrgName('')
    }

    const handleClose = useCallback(() => {
        resetState()
        onClose()
    }, [resetState, onClose])

    const handleSubmit = async () => {
        const quota = parseInt(monthQuota, 10)
        if (isNaN(quota) || quota <= 0) {
            toast({ title: "请输入有效的月配额", variant: "destructive" })
            return
        }
        if (ownerType === 'person' && !selectedUser) {
            toast({ title: "请选择目标用户", variant: "destructive" })
            return
        }
        if ((ownerType === 'org' || ownerType === 'project') && (!orgCode.trim() || !orgName.trim())) {
            toast({ title: `请填写${ownerType === 'org' ? '组织' : '项目'}编码和名称`, variant: "destructive" })
            return
        }

        setIsSubmitting(true)
        try {
            const op = ownerType === 'person'
                ? { ownerType: 'person', ownerUserId: selectedUser!.id, monthQuota: quota }
                : { ownerType, ownerCode: orgCode.trim(), ownerName: orgName.trim(), monthQuota: quota }

            const apikey = await applyApikey(op)
            setNewApiKey(apikey)
            setShowCopyDialog(true)
            resetState()
            onClose()
            onSuccess()
        } catch {
            toast({ title: "创建失败", description: "创建 API Key 时发生错误，请重试", variant: "destructive" })
        } finally {
            setIsSubmitting(false)
        }
    }

    const handleCopy = () => {
        if (!newApiKey) return
        navigator.clipboard.writeText(newApiKey).then(() => {
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
        })
    }

    return (
        <>
            <Dialog open={isOpen} onOpenChange={handleClose}>
                <DialogContent className="max-w-xl max-h-[80vh] overflow-y-auto bg-white border border-gray-200 shadow-lg">
                    <DialogHeader>
                        <DialogTitle>创建 API Key</DialogTitle>
                    </DialogHeader>

                    <div className="space-y-4">
                        {/* 所有者类型 */}
                        <div className="space-y-1.5">
                            <Label>所有者类型</Label>
                            <Select value={ownerType} onValueChange={(v: 'person' | 'org' | 'project') => handleOwnerTypeChange(v)}>
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="person">个人</SelectItem>
                                    <SelectItem value="org">组织</SelectItem>
                                    <SelectItem value="project">项目</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>

                        {/* person：搜索用户 */}
                        {ownerType === 'person' && (
                            <div className="space-y-1.5">
                                <Label>目标用户</Label>
                                {selectedUser ? (
                                    <div className="flex items-center justify-between p-2 rounded-md border border-gray-200 bg-gray-50">
                                        <div className="flex items-center gap-2">
                                            <div className="w-7 h-7 bg-blue-100 rounded-full flex items-center justify-center">
                                                <User className="h-4 w-4 text-blue-600" />
                                            </div>
                                            <span className="text-sm font-medium">{selectedUser.userName}</span>
                                            <Badge variant="secondary" className="text-xs">{selectedUser.source}</Badge>
                                        </div>
                                        <Button variant="ghost" size="sm" className="text-xs h-6 px-2" onClick={() => { setSelectedUser(null); setUserSearch(''); setUserSearchResults([]) }}>
                                            重选
                                        </Button>
                                    </div>
                                ) : (
                                    <>
                                        <div className="relative">
                                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 h-4 w-4" />
                                            <Input
                                                ref={searchInputRef}
                                                placeholder="输入用户名、邮箱或ID搜索"
                                                value={userSearch}
                                                onChange={e => setUserSearch(e.target.value)}
                                                onCompositionStart={() => setIsComposing(true)}
                                                onCompositionEnd={() => setIsComposing(false)}
                                                className="pl-10"
                                            />
                                            {isSearching && (
                                                <div className="absolute right-3 top-1/2 -translate-y-1/2">
                                                    <div className="animate-spin h-4 w-4 border-2 border-blue-500 border-t-transparent rounded-full" />
                                                </div>
                                            )}
                                        </div>
                                        {userSearchResults.length > 0 && (
                                            <div className="max-h-48 overflow-y-auto space-y-1.5 mt-1">
                                                {userSearchResults.map(user => (
                                                    <Card
                                                        key={user.id}
                                                        className="cursor-pointer hover:bg-gray-50 transition-colors"
                                                        onClick={() => { setSelectedUser(user); setUserSearchResults([]) }}
                                                    >
                                                        <CardContent className="p-3 flex items-center gap-3">
                                                            <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center shrink-0">
                                                                <User className="h-4 w-4 text-blue-600" />
                                                            </div>
                                                            <div>
                                                                <div className="flex items-center gap-1.5">
                                                                    <span className="text-sm font-medium">{user.userName}</span>
                                                                    <Badge variant="secondary" className="text-xs">{user.source}</Badge>
                                                                </div>
                                                                <div className="text-xs text-gray-400">{user.email} · ID: {user.sourceId}</div>
                                                            </div>
                                                        </CardContent>
                                                    </Card>
                                                ))}
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>
                        )}

                        {/* org / project：输入编码和名称 */}
                        {(ownerType === 'org' || ownerType === 'project') && (
                            <>
                                <div className="space-y-1.5">
                                    <Label>{ownerType === 'org' ? '组织' : '项目'}编码</Label>
                                    <Input placeholder={`请输入${ownerType === 'org' ? '组织' : '项目'}编码`} value={orgCode} onChange={e => setOrgCode(e.target.value)} />
                                </div>
                                <div className="space-y-1.5">
                                    <Label>{ownerType === 'org' ? '组织' : '项目'}名称</Label>
                                    <Input placeholder={`请输入${ownerType === 'org' ? '组织' : '项目'}名称`} value={orgName} onChange={e => setOrgName(e.target.value)} />
                                </div>
                            </>
                        )}

                        {/* 月配额 */}
                        <div className="space-y-1.5">
                            <Label>月配额（元）</Label>
                            <Input
                                type="number"
                                min={1}
                                placeholder="50"
                                value={monthQuota}
                                onChange={e => setMonthQuota(e.target.value)}
                            />
                        </div>
                    </div>

                    <DialogFooter>
                        <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>取消</Button>
                        <Button onClick={handleSubmit} disabled={isSubmitting}>
                            {isSubmitting ? '创建中...' : '创建'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <CopyApikeyDialog
                showDialog={showCopyDialog}
                setShowDialog={setShowCopyDialog}
                apikey={newApiKey}
                handleCopyApiKey={handleCopy}
                copied={copied}
            />
        </>
    )
}
