import { useState, useEffect } from 'react'

/**
 * 响应式列数计算 Hook
 *
 * 根据内容区域宽度（窗口宽度 - Sidebar 宽度）自动计算网格列数：
 * - 内容宽度 >= 1440px: 4 列
 * - 内容宽度 >= 1024px: 3 列
 * - 内容宽度 >= 640px: 2 列
 * - 内容宽度 < 640px: 1 列
 *
 * @returns 当前应显示的列数
 *
 * @example
 * ```tsx
 * const columns = useResponsiveColumns()
 * // 窗口宽度 1920px，内容宽度 1664px -> 4列
 * // 窗口宽度 1440px，内容宽度 1184px -> 3列
 * // 窗口宽度 1024px，内容宽度 768px -> 2列
 * ```
 */
export function useResponsiveColumns(): number {
  // Sidebar 固定宽度（Tailwind w-64 = 256px）
  const SIDEBAR_WIDTH = 256

  // SSR 安全：初始化时根据内容区域宽度计算列数
  const [columns, setColumns] = useState(() => {
    // 服务端渲染时默认返回 3 列
    if (typeof window === 'undefined') return 3

    // 计算实际内容区域宽度（减去 Sidebar）
    const contentWidth = window.innerWidth - SIDEBAR_WIDTH

    if (contentWidth >= 1440) return 4
    if (contentWidth >= 1024) return 3
    if (contentWidth >= 640) return 2
    return 1
  })

  useEffect(() => {
    // 更新列数的函数
    const updateColumns = () => {
      // 计算实际内容区域宽度（减去 Sidebar）
      const contentWidth = window.innerWidth - SIDEBAR_WIDTH

      if (contentWidth >= 1440) setColumns(4)
      else if (contentWidth >= 1024) setColumns(3)
      else if (contentWidth >= 640) setColumns(2)
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
