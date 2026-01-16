'use client'

import { useRef, useEffect, useMemo } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { VirtualGridProps } from './types'
import { useResponsiveColumns } from './useResponsiveColumns'
import { cn } from '@/lib/utils'

/**
 * 虚拟滚动网格组件
 *
 * 支持动态高度测量的虚拟滚动网格布局，适用于大数据量场景（1000+ 条数据）
 * 核心特性：
 * - 仅渲染可见区域 + 缓冲区，大幅减少 DOM 节点
 * - 智能预估卡片高度，支持内容动态变化
 * - 高度缓存机制，避免重复测量
 * - 响应式 1/2/3 列布局（根据窗口宽度自动调整）
 * - 数据变化时自动滚动到顶部
 *
 * @example
 * ```tsx
 * <VirtualGrid
 *   items={models}
 *   estimateItemSize={(model) => 258 + model.features.length * 10}
 *   gap={16}
 *   overscan={5}
 *   getItemKey={(model) => model.id}
 *   renderItem={(model) => <ModelCard model={model} />}
 *   emptyElement={<div>暂无数据</div>}
 * />
 * ```
 */
export function VirtualGrid<T>({
  items,
  renderItem,
  getItemKey,
  estimateItemSize,
  estimatedItemHeight = 258,
  gap = 16,
  overscan = 5,
  emptyElement,
  className,
}: VirtualGridProps<T>) {
  // 滚动容器引用
  const parentRef = useRef<HTMLDivElement>(null)

  // 高度缓存 Map（key: 行索引, value: 实际测量高度）
  const heightCache = useRef<Map<number, number>>(new Map())

  // 响应式列数计算（1/2/3 列）
  const columns = useResponsiveColumns()

  // 计算总行数（向上取整）
  const rowCount = Math.ceil(items.length / columns)

  // 智能预估函数：结合自定义预估和缓存
  const estimateRowSize = useMemo(
    () => (index: number) => {
      // 优先使用缓存的实际高度
      const cached = heightCache.current.get(index)
      if (cached !== undefined) {
        return cached
      }

      // 如果提供了自定义预估函数，计算当前行的预估高度
      if (estimateItemSize) {
        const startIdx = index * columns
        const rowItems = items.slice(startIdx, startIdx + columns)

        if (rowItems.length === 0) {
          return estimatedItemHeight + gap
        }

        // 计算当前行所有卡片的预估高度，取最大值
        const maxEstimatedHeight = Math.max(
          ...rowItems.map((item) => estimateItemSize(item))
        )

        return maxEstimatedHeight + gap
      }

      // 降级使用固定预估高度
      return estimatedItemHeight + gap
    },
    [columns, items, estimateItemSize, estimatedItemHeight, gap]
  )

  // 核心：虚拟滚动实例（动态高度支持）
  const rowVirtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => parentRef.current,
    // 使用智能预估函数
    estimateSize: estimateRowSize,
    // 动态测量实际 DOM 高度并缓存
    measureElement:
      typeof window !== 'undefined' && navigator.userAgent.indexOf('Firefox') === -1
        ? (element) => {
            const height = element?.getBoundingClientRect().height
            if (height && element) {
              const index = Number(element.getAttribute('data-index'))
              if (!isNaN(index)) {
                heightCache.current.set(index, height)
              }
            }
            return height ?? estimatedItemHeight + gap
          }
        : undefined,
    // 预渲染缓冲区（上下各 N 行）
    overscan,
  })

  // 获取可见的虚拟行
  const virtualItems = rowVirtualizer.getVirtualItems()

  // 当数据源变化时，清除缓存并滚动到顶部
  useEffect(() => {
    heightCache.current.clear()
    rowVirtualizer.scrollToIndex(0, { align: 'start' })
  }, [items.length, rowVirtualizer])

  // 当列数变化时，清除缓存并重新测量所有行高
  useEffect(() => {
    heightCache.current.clear()
    rowVirtualizer.measure()
  }, [columns, rowVirtualizer])

  // 空状态处理
  if (items.length === 0 && emptyElement) {
    return <div className={cn('h-full', className)}>{emptyElement}</div>
  }

  return (
    <div ref={parentRef} className={cn('h-full overflow-y-auto', className)}>
      {/* 虚拟滚动容器（总高度） */}
      <div
        style={{
          height: `${rowVirtualizer.getTotalSize()}px`,
          width: '100%',
          position: 'relative',
        }}
      >
        {/* 仅渲染可见行 */}
        {virtualItems.map((virtualRow) => {
          // 计算当前行包含的数据项
          const startIdx = virtualRow.index * columns
          const rowItems = items.slice(startIdx, startIdx + columns)

          return (
            <div
              key={virtualRow.key}
              data-index={virtualRow.index}
              ref={rowVirtualizer.measureElement} // 测量引用
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              {/* 网格布局（当前行） */}
              <div
                className="grid items-stretch mb-6"
                style={{
                  gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                  gap: `${gap}px`,
                }}
              >
                {rowItems.map((item) => (
                  <div key={getItemKey(item)} className="h-full">{renderItem(item)}</div>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
