import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, DropdownMenuLabel} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import Link from 'next/link';
import {hasPermission} from "@/lib/api/userInfo";
import React from "react"
import {UserInfo} from "@/lib/types/openapi";
import { useRouter } from 'next/navigation'
import { openapi } from '@/lib/api/openapi'

interface UserNavProps {
    user: UserInfo
}

export function UserNav({ user }: UserNavProps) {
    const router = useRouter()

    const handleLogout = async () => {
        try {
            await openapi.post('/logout')
            router.push('/login')
        } catch (error) {
            console.error('Logout failed:', error)
            // 即使失败也跳转到登录页
            router.push('/login')
        }
    }

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Avatar className="h-10 w-10 cursor-pointer border-2 border-gray-300 dark:border-gray-600 shadow-sm">
                    <AvatarImage src={user.image} alt={user.userName} />
                    <AvatarFallback className="bg-black text-white text-lg font-bold">{user.userName.charAt(0)}</AvatarFallback>
                </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="w-56" align="end">
                <div className="flex items-center justify-center p-2 bg-gray-100 dark:bg-gray-800">
                    <span className="text-sm font-medium text-gray-800 dark:text-gray-100">{user.userName}</span>
                </div>
                <DropdownMenuItem>
                    <Link href="/" className="w-full text-sm">
                        主页
                    </Link>
                </DropdownMenuItem>
                <DropdownMenuItem>
                    <Link href="/apikey" className="w-full text-sm">
                        API Key管理
                    </Link>
                </DropdownMenuItem>
                {hasPermission(user, '/console/model/**') && (
                    <DropdownMenuItem>
                        <Link href="/meta/console" className="w-full text-sm">
                            元数据管理
                        </Link>
                    </DropdownMenuItem>
                )}
                <DropdownMenuItem>
                    <Link href="/monitor" className="w-full text-sm">
                        能力点监控
                    </Link>
                </DropdownMenuItem>
                <DropdownMenuItem>
                    <Link href="/logs" className="w-full text-sm">
                        日志查询
                    </Link>
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleLogout} className="text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-900 dark:hover:text-red-300">
                    登出
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    )
}
