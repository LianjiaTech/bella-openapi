import { useState, useEffect } from 'react'

/**
 * 响应式列数计算 Hook
 *
 * 根据内容区域宽度自动计算网格列数：
 * - 内容宽度 >= 1440px: 4 列
 * - 内容宽度 >= 1024px: 3 列
 * - 内容宽度 >= 640px: 2 列
 * - 内容宽度 < 640px: 1 列
 *
 * @returns 当前应显示的列数
 */
export function useResponsiveColumns(): number {
  const DESKTOP_BREAKPOINT = 1280
  const SIDEBAR_WIDTH = 256

  const getContentWidth = () => {
    if (typeof window === 'undefined') return 1024
    const isDesktop = window.innerWidth >= DESKTOP_BREAKPOINT
    return isDesktop ? window.innerWidth - SIDEBAR_WIDTH : window.innerWidth
  }

  const [columns, setColumns] = useState(() => {
    if (typeof window === 'undefined') return 3

    const contentWidth = getContentWidth()

    if (contentWidth >= 1440) return 4
    if (contentWidth >= 1024) return 3
    if (contentWidth >= 640) return 2
    return 1
  })

  useEffect(() => {
    const updateColumns = () => {
      const contentWidth = getContentWidth()

      if (contentWidth >= 1440) setColumns(4)
      else if (contentWidth >= 1024) setColumns(3)
      else if (contentWidth >= 640) setColumns(2)
      else setColumns(1)
    }

    let timeoutId: NodeJS.Timeout
    const debouncedUpdate = () => {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(updateColumns, 150)
    }

    window.addEventListener('resize', debouncedUpdate)

    return () => {
      clearTimeout(timeoutId)
      window.removeEventListener('resize', debouncedUpdate)
    }
  }, [])

  return columns
}
