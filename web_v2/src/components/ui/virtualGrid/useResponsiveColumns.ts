import { useState, useEffect } from 'react'

/**
 * 响应式列数计算 Hook
 *
 * 根据窗口宽度自动计算网格列数：
 * - >= 1024px (lg): 3 列
 * - >= 768px (md): 2 列
 * - < 768px (sm): 1 列
 *
 * @returns 当前应显示的列数
 *
 * @example
 * ```tsx
 * const columns = useResponsiveColumns()
 * // 在 1920px 宽度下返回 3
 * // 在 800px 宽度下返回 2
 * // 在 375px 宽度下返回 1
 * ```
 */
export function useResponsiveColumns(): number {
  // SSR 安全：初始化时根据窗口宽度计算列数
  const [columns, setColumns] = useState(() => {
    // 服务端渲染时默认返回 3 列
    if (typeof window === 'undefined') return 3

    const width = window.innerWidth
    if (width >= 1024) return 3 // lg
    if (width >= 768) return 2 // md
    return 1 // sm
  })

  useEffect(() => {
    // 更新列数的函数
    const updateColumns = () => {
      const width = window.innerWidth
      if (width >= 1024) setColumns(3)
      else if (width >= 768) setColumns(2)
      else setColumns(1)
    }

    // 防抖优化：避免 resize 时频繁触发
    let timeoutId: NodeJS.Timeout
    const debouncedUpdate = () => {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(updateColumns, 150)
    }

    // 监听窗口大小变化
    window.addEventListener('resize', debouncedUpdate)

    // 清理函数
    return () => {
      clearTimeout(timeoutId)
      window.removeEventListener('resize', debouncedUpdate)
    }
  }, [])

  return columns
}
