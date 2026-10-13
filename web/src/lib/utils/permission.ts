/**
 * 权限工具函数
 * 提供基于Ant Path Matcher模式的权限判断
 */

import type { UserInfo } from '@/lib/types/auth'

/**
 * Ant Path Matcher 实现
 * 支持通配符模式匹配
 *
 * 通配符说明:
 * - `*`  : 匹配路径段中的0个或多个字符（不包括 /）
 * - `**` : 匹配0个或多个路径段
 * - `?`  : 匹配单个字符
 *
 * 示例:
 * - `/admin/**`      匹配 `/admin/users`, `/admin/settings/profile`
 * - `/api/*`         匹配 `/api/users` 但不匹配 `/api/users/123`
 * - `/api/user?`     匹配 `/api/user1`, `/api/userA`
 * - `/api/*.json`    匹配 `/api/data.json`
 */
class AntPathMatcher {
  /**
   * 匹配路径模式
   *
   * @param pattern - 模式字符串，如 `/admin/**` 或 `/api/*\/users`
   * @param path - 要匹配的路径
   * @returns 是否匹配
   */
  static match(pattern: string, path: string): boolean {
    // 转义正则表达式中的特殊字符
    let regexPattern = pattern
      .replace(/[.+^${}()|[\]\\]/g, '\\$&')  // 转义正则特殊字符
      .replace(/\*\*/g, '§DOUBLE_STAR§')     // 临时标记 **
      .replace(/\*/g, '[^/]*')                // * 匹配除 / 外的任意字符
      .replace(/§DOUBLE_STAR§/g, '.*')       // ** 匹配任意字符（包括 /）
      .replace(/\?/g, '.')                    // ? 匹配单个字符

    const regex = new RegExp(`^${regexPattern}$`)
    return regex.test(path)
  }
}

/**
 * 检查用户是否有访问指定URL的权限
 *
 * 权限判断逻辑:
 * 1. 检查URL是否匹配roles中的任意模式（包含规则）
 * 2. 检查URL是否匹配excludes中的任意模式（排除规则）
 * 3. 结果 = 在包含规则中 且 不在排除规则中
 *
 * @param user - 用户信息（可为null）
 * @param url - 要检查的URL路径
 * @returns 是否有权限访问
 *
 * 使用示例:
 * ```typescript
 * const user = { optionalInfo: { roles: ['/admin/**', '/api/*'], excludes: ['/api/secret'] } }
 * hasPermission(user, '/admin/users')    // true
 * hasPermission(user, '/api/users')      // true
 * hasPermission(user, '/api/secret')     // false (被排除)
 * hasPermission(user, '/public')         // false (不在roles中)
 * ```
 */
export function hasPermission(user: UserInfo | null, url: string): boolean {
  if (!user?.optionalInfo) {
    return false
  }

  const roles = (user.optionalInfo.roles as string[]) || []
  const excludes = (user.optionalInfo.excludes as string[]) || []

  // 检查是否在包含列表中
  const isIncluded = roles.some(pattern => AntPathMatcher.match(pattern, url))

  // 检查是否在排除列表中
  const isExcluded = excludes.some(pattern => AntPathMatcher.match(pattern, url))

  // 必须在包含列表中，且不在排除列表中
  return isIncluded && !isExcluded
}

/**
 * 检查用户是否有特定角色
 *
 * @param user - 用户信息（可为null）
 * @param role - 角色名称
 * @returns 是否拥有该角色
 *
 * 使用示例:
 * ```typescript
 * const user = { optionalInfo: { roles: ['admin', 'editor'] } }
 * hasRole(user, 'admin')    // true
 * hasRole(user, 'viewer')   // false
 * ```
 */
export function hasRole(user: UserInfo | null, role: string): boolean {
  if (!user?.optionalInfo?.roles) {
    return false
  }

  const roles = user.optionalInfo.roles as string[]
  return roles.includes(role)
}

/**
 * 检查用户是否有任意一个角色
 *
 * @param user - 用户信息（可为null）
 * @param roleList - 角色列表
 * @returns 是否拥有列表中的任意一个角色
 *
 * 使用示例:
 * ```typescript
 * const user = { optionalInfo: { roles: ['editor'] } }
 * hasAnyRole(user, ['admin', 'editor'])    // true (有editor)
 * hasAnyRole(user, ['admin', 'viewer'])    // false
 * ```
 */
export function hasAnyRole(user: UserInfo | null, roleList: string[]): boolean {
  return roleList.some(role => hasRole(user, role))
}

/**
 * 检查用户是否有所有角色
 *
 * @param user - 用户信息（可为null）
 * @param roleList - 角色列表
 * @returns 是否拥有列表中的所有角色
 *
 * 使用示例:
 * ```typescript
 * const user = { optionalInfo: { roles: ['admin', 'editor'] } }
 * hasAllRoles(user, ['admin', 'editor'])    // true
 * hasAllRoles(user, ['admin', 'viewer'])    // false (缺少viewer)
 * ```
 */
export function hasAllRoles(user: UserInfo | null, roleList: string[]): boolean {
  return roleList.every(role => hasRole(user, role))
}
