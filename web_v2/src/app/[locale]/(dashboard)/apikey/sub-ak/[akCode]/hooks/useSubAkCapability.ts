/**
 * useSubAkCapability — 子 AK 操作权限计算 Hook
 *
 * 职责：根据 viewer（视角参数）和 isSuperAdmin 集中计算当前用户对子 AK 页面的操作能力。
 *
 * 设计原则：
 *   - 组件层只消费 capability 的 canXxx 布尔值，不直接判断 viewer 字符串
 *   - 新增角色只需在此 hook 加一个 case，组件层零改动
 *   - 新增操作权限只需在 SubAkCapability 接口加字段，各 case 补赋值
 *
 * viewer 取值：
 *   - 'user'（默认）：普通用户，操作自己父 AK 下的子 AK
 *   - 'admin'：管理员，操作全量 AK（超级管理员额外可删除）
 *   - 'manager'：管理者，操作被授权的子 AK（未来角色，预留 case）
 */

import { useMemo } from 'react'

export interface SubAkCapability {
    /** 数据查询方式：user=getApiKeys(ownerCode)，admin=getAdminApiKeys */
    fetchMode: 'user' | 'admin'
    /** 返回按钮跳转路由 */
    backHref: string
    /** 返回按钮文案 */
    backLabel: string
    /** 能否创建子 AK */
    canCreate: boolean
    /** 能否修改子 AK 配额 */
    canEditQuota: boolean
    /** 能否编辑子 AK 模型白名单 */
    canEditModelWhitelist: boolean
    /** 能否重置子 AK */
    canReset: boolean
    /** 能否删除子 AK */
    canDelete: boolean
    /**
     * 能否为子 AK 设置管理者
     * owner / manager 均可为其子 AK 指派管理者（防权限扩散：仅能下授，不能上改）
     * admin 始终可设置
     */
    canSetManager: boolean
}

/**
 * @param viewer - URL searchParam viewer 的值（'user' | 'admin' | 'manager' | undefined）
 * @param isSuperAdmin - 是否为超级管理员（roleCode=all），影响管理员视角下的删除权限
 */
export function useSubAkCapability(
    viewer: string | undefined,
    isSuperAdmin: boolean
): SubAkCapability {
    return useMemo((): SubAkCapability => {
        switch (viewer) {
            case 'admin':
                return {
                    fetchMode: 'admin',
                    backHref: '/apikey-admin',
                    backLabel: 'API Key 管理（管理员）',
                    canCreate: true,
                    canEditQuota: true,
                    canEditModelWhitelist: true,
                    canReset: true,
                    canDelete: isSuperAdmin,
                    canSetManager: true,
                }

            case 'manager':
                // 管理者视角：权限等同 owner，可为子 AK 设置管理者，但不可 transfer/delete 父 AK
                return {
                    fetchMode: 'admin',
                    backHref: '/manager',
                    backLabel: '我管理的 AK',
                    canCreate: true,
                    canEditQuota: true,
                    canEditModelWhitelist: true,
                    canReset: true,
                    canDelete: false,
                    canSetManager: true,
                }

            default: // 'user' 或无参数：AK owner，可为子 AK 设置管理者
                return {
                    fetchMode: 'user',
                    backHref: '/apikey',
                    backLabel: 'API Keys 列表',
                    canCreate: true,
                    canEditQuota: true,
                    canEditModelWhitelist: false,
                    canReset: true,
                    canDelete: true,
                    canSetManager: true,
                }
        }
    }, [viewer, isSuperAdmin])
}