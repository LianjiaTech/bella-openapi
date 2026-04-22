'use client'

/**
 * 管理员路由域 Layout
 *
 * 职责：对 (admin)/ 下所有路由做统一鉴权前置。
 * 任何进入此路由组的页面（apikey-admin、manager 等）无需再单独写权限判断。
 *
 * 权限规则：
 *   - 具有 /console/** 角色路径的用户 → 通过，渲染 children
 *   - 加载中 → 渲染空白（防止闪屏后再跳转）
 *   - 无权限 → redirect 到 /apikey（与现有 auth 风格一致）
 *
 * 防 re-render：
 *   - hasPermission 基于原始值 user 计算，useAuth 内部已做 memoize，此处无需额外优化
 */

import React from 'react'
import { useAuth } from '@/components/providers/auth-provider'
import { hasPermission } from '@/lib/utils/permission'
import { redirect } from 'next/navigation'

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const { user, isLoading, isInitialized } = useAuth()

    // 用户信息加载中：暂不渲染，防止权限未就绪时短暂显示内容再跳转
    if (isLoading || !isInitialized) return null

    // 无管理员权限：重定向到普通 apikey 页面
    if (!hasPermission(user, '/console/**')) {
        redirect('/apikey')
    }

    return <>{children}</>
}
