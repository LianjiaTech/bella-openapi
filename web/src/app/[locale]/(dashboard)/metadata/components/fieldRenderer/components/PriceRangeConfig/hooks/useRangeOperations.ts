/**
 * 职责: 封装价格区间的所有 CRUD 操作逻辑
 *
 * 功能:
 * - addRange: 添加新的价格区间
 * - removeRange: 删除指定区间
 * - updateRange: 更新区间属性
 * - handleEnableOutputRange: 切换输出区间开关
 * - addOutputSubRange: 添加输出子区间
 * - removeOutputSubRange: 删除输出子区间
 * - updateOutputSubRange: 更新输出子区间
 *
 * 设计:
 * - 所有操作使用 useCallback 包裹,确保引用稳定
 * - 保持原有业务逻辑完全一致 (边界处理、默认值等)
 * - 不可变更新: 使用 map/filter 返回新数组
 *
 * 避免 re-render:
 * - useCallback 依赖精确,仅在必要时更新引用
 * - 子组件可安全地将这些方法放入依赖数组
 */

import { useCallback } from "react"
import { nanoid } from "nanoid"
import type { PriceRange, OutputRangePrice } from "../types"
import { defaultRange, createDefaultOutputRangePrice, MAX_TOKEN_VALUE } from "../constants"

interface UseRangeOperationsParams {
  mode: 'create' | 'edit'  // 操作模式：控制图片/缓存字段初始化值
  normalizedRanges: PriceRange[]
  onRangesChange: (ranges: PriceRange[]) => void
  setExpandedRanges: (updater: (prev: Set<string>) => Set<string>) => void
}

interface UseRangeOperationsReturn {
  addRange: () => void
  removeRange: (id: string) => void
  updateRange: (id: string, updates: Partial<PriceRange>) => void
  updateMaxTokenWithCascade: (currentRangeId: string, nextRangeId: string | undefined, newMaxToken: number) => void
  handleEnableOutputRange: (rangeId: string, enabled: boolean) => void
  addOutputSubRange: (rangeId: string) => void
  removeOutputSubRange: (rangeId: string, outputRangeId: string) => void
  updateOutputSubRange: (
    rangeId: string,
    outputRangeId: string,
    updates: Partial<OutputRangePrice>
  ) => void
}

/**
 * CRUD 操作 Hook
 *
 * @param mode - 操作模式：控制图片/缓存字段初始化值
 * @param normalizedRanges - 规范化后的区间数组
 * @param onRangesChange - 区间变化回调
 * @param setExpandedRanges - 折叠状态更新方法
 * @returns 7 个 CRUD 操作方法
 */
export function useRangeOperations({
  mode,
  normalizedRanges,
  onRangesChange,
  setExpandedRanges,
}: UseRangeOperationsParams): UseRangeOperationsReturn {
  /**
   * 职责: 添加新的价格区间
   * 逻辑:
   * 1. 如果最后一个区间的 inputRangePrice.maxToken 是 +∞,先将其改为 minToken + 1000
   * 2. 创建新区间,minToken 为上一区间的 maxToken,maxToken 为 +∞
   * 3. 自动展开新区间
   */
  const addRange = useCallback(() => {
    const updatedRanges = normalizedRanges.map((r, idx) => {
      if (idx === normalizedRanges.length - 1) {
        const currentMaxToken = r.inputRangePrice.maxToken
        const newMaxToken =
          currentMaxToken === MAX_TOKEN_VALUE ? r.inputRangePrice.minToken + 1000 : currentMaxToken
        return {
          ...r,
          inputRangePrice: {
            ...r.inputRangePrice,
            maxToken: newMaxToken,
          },
        }
      }
      return r
    })

    const updatedLastRange = updatedRanges[updatedRanges.length - 1]
    const newRange: PriceRange = {
      ...defaultRange,
      id: nanoid(),
      inputRangePrice: {
        ...defaultRange.inputRangePrice,
        minToken: updatedLastRange.inputRangePrice.maxToken,
        maxToken: MAX_TOKEN_VALUE,
      },
    }
    const newRanges = [...updatedRanges, newRange]
    onRangesChange(newRanges)
    setExpandedRanges((prev) => new Set([...prev, newRange.id]))
  }, [normalizedRanges, onRangesChange, setExpandedRanges])

  /**
   * 职责: 删除指定区间
   * 边界: 至少保留 1 个区间
   * 逻辑:
   * 1. 过滤掉目标区间
   * 2. 如果删除后只剩一个区间，重置为默认值 (minToken=0, maxToken=+∞)
   * 3. 如果剩余多个区间，重新计算联动关系：
   *    - 新的首区间：minToken 重置为 0
   *    - 非首区间：minToken 联动到上一个区间的 maxToken
   *    - 最后一个区间：maxToken 设为 +∞
   */
  const removeRange = useCallback(
    (id: string) => {
      if (normalizedRanges.length <= 1) return

      // 1. 过滤掉要删除的区间
      const filteredRanges = normalizedRanges.filter((r) => r.id !== id)

      // 2. 如果删除后只剩一个区间，重置为默认值
      if (filteredRanges.length === 1) {
        const newRanges = [{
          ...filteredRanges[0],
          inputRangePrice: {
            ...filteredRanges[0].inputRangePrice,
            minToken: 0,
            maxToken: MAX_TOKEN_VALUE,
          },
        }]
        onRangesChange(newRanges)
        return
      }

      // 3. 如果剩余多个区间，重新计算联动关系
      const newRanges = filteredRanges.map((r, idx) => {
        const isNewFirst = idx === 0
        const isNewLast = idx === filteredRanges.length - 1
        const prevRange = idx > 0 ? filteredRanges[idx - 1] : null

        return {
          ...r,
          inputRangePrice: {
            ...r.inputRangePrice,
            // 新的首区间：minToken 重置为 0
            ...(isNewFirst && { minToken: 0 }),
            // 非首区间：minToken 联动到上一个区间的 maxToken
            ...(prevRange && { minToken: prevRange.inputRangePrice.maxToken }),
            // 最后一个区间：maxToken 设置为 MAX_TOKEN_VALUE
            ...(isNewLast && { maxToken: MAX_TOKEN_VALUE }),
          },
        }
      })

      onRangesChange(newRanges)
    },
    [normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 更新指定区间的部分属性
   */
  const updateRange = useCallback(
    (id: string, updates: Partial<PriceRange>) => {
      const newRanges = normalizedRanges.map((r) => {
        if (r.id === id) {
          // 📌 修复: 如果只更新 inputRangePrice 的部分字段，需要合并原有数据
          if (updates.inputRangePrice && r.inputRangePrice) {
            return {
              ...r,
              ...updates,
              inputRangePrice: {
                ...r.inputRangePrice,
                ...updates.inputRangePrice,
              },
            }
          }
          return { ...r, ...updates }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 更新当前区间的 maxToken，并联动更新下一个区间的 minToken
   *
   * 设计:
   * - 在一次遍历中同时更新两个区间，避免连续调用 updateRange 导致状态覆盖
   * - 解决了 RangeInputSection 中修改 maxToken 无效的问题
   *
   * @param currentRangeId - 当前区间 ID
   * @param nextRangeId - 下一个区间 ID（可选，最后一个区间时为 undefined）
   * @param newMaxToken - 新的 maxToken 值
   */
  const updateMaxTokenWithCascade = useCallback(
    (currentRangeId: string, nextRangeId: string | undefined, newMaxToken: number) => {
      const newRanges = normalizedRanges.map((r) => {
        // 更新当前区间的 maxToken
        if (r.id === currentRangeId) {
          return {
            ...r,
            inputRangePrice: {
              ...r.inputRangePrice,
              maxToken: newMaxToken,
            },
          }
        }
        // 联动更新下一个区间的 minToken
        if (nextRangeId && r.id === nextRangeId) {
          return {
            ...r,
            inputRangePrice: {
              ...r.inputRangePrice,
              minToken: newMaxToken,
            },
          }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 切换输出区间开关
   * 逻辑:
   * 1. 如果启用且 outputRangePrices 为空,自动创建一个默认输出区间
   * 2. 默认值从父区间的 inputRangePrice.output 继承
   * 3. 如果禁用,保留 outputRangePrices 数据 (不清空)
   * 4. create 模式下图片/缓存字段初始化为 null
   */
  const handleEnableOutputRange = useCallback(
    (rangeId: string, enabled: boolean) => {
      const newRanges = normalizedRanges.map((r) => {
        if (r.id === rangeId) {
          return {
            ...r,
            outputRangePrices: enabled
              ? (r.outputRangePrices?.length ?? 0) === 0
                  ? [createDefaultOutputRangePrice(0, MAX_TOKEN_VALUE, r.inputRangePrice.input, r.inputRangePrice.output, mode)]
                  : r.outputRangePrices
              : [],  // 📌 关闭时清空数组,确保 Switch 状态同步
          }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [mode, normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 添加输出子区间
   * 逻辑:
   * 1. 如果最后一个输出区间的 maxToken 是 +∞,先将其改为 minToken + 1000
   * 2. 创建新输出区间,minToken 为上一区间的 maxToken,maxToken 为 +∞
   * 3. 新区间的 output 价格继承上一区间的值
   * 4. create 模式下图片/缓存字段初始化为 null
   */
  const addOutputSubRange = useCallback(
    (rangeId: string) => {
      const newRanges = normalizedRanges.map((r) => {
        if (r.id === rangeId) {
          const outputRangePrices = r.outputRangePrices || []
          const updatedOutputRanges = outputRangePrices.map((or, idx) => {
            if (idx === outputRangePrices.length - 1) {
              const newMaxToken =
                or.maxToken === MAX_TOKEN_VALUE ? or.minToken + 1000 : or.maxToken
              return { ...or, maxToken: newMaxToken }
            }
            return or
          })
          const lastOutputRange = updatedOutputRanges[updatedOutputRanges.length - 1]
          return {
            ...r,
            outputRangePrices: [
              ...updatedOutputRanges,
              createDefaultOutputRangePrice(
                lastOutputRange.maxToken,
                MAX_TOKEN_VALUE,
                lastOutputRange.input,
                lastOutputRange.output,
                mode
              ),
            ],
          }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [mode, normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 删除输出子区间
   * 边界: 至少保留 1 个输出区间
   * 逻辑:
   * 1. 根据 id 删除目标输出区间
   * 2. 如果删除后只剩一个区间,重置为默认边界 (0, +∞)
   * 3. 如果剩余多个区间,重新计算联动关系:
   *    - 第一个区间: minToken 重置为 0
   *    - 非第一个区间: minToken 联动到上一个区间的 maxToken
   *    - 最后一个区间: maxToken 设为 +∞
   */
  const removeOutputSubRange = useCallback(
    (rangeId: string, outputRangeId: string) => {
      const newRanges = normalizedRanges.map((r) => {
        if (r.id === rangeId) {
          const outputRangePrices = r.outputRangePrices || []
          if (outputRangePrices.length > 1) {
            // 1. 过滤掉要删除的区间
            const filteredOutputRanges = outputRangePrices.filter((or) => or.id !== outputRangeId)

            // 2. 如果删除后只剩一个区间,重置为默认边界
            if (filteredOutputRanges.length === 1) {
              return {
                ...r,
                outputRangePrices: [{
                  ...filteredOutputRanges[0],
                  minToken: 0,
                  maxToken: MAX_TOKEN_VALUE,
                }],
              }
            }

            // 3. 如果剩余多个区间,重新计算联动关系
            const finalOutputRanges = filteredOutputRanges.map((or, idx) => {
              const isFirst = idx === 0
              const isLast = idx === filteredOutputRanges.length - 1
              const prevRange = idx > 0 ? filteredOutputRanges[idx - 1] : null

              return {
                ...or,
                // 第一个区间: minToken 保持为 0
                ...(isFirst && { minToken: 0 }),
                // 非第一个区间: minToken 联动到上一个区间的 maxToken
                ...(prevRange && { minToken: prevRange.maxToken }),
                // 最后一个区间: maxToken 设为 +∞
                ...(isLast && { maxToken: MAX_TOKEN_VALUE }),
              }
            })
            return { ...r, outputRangePrices: finalOutputRanges }
          }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [normalizedRanges, onRangesChange]
  )

  /**
   * 职责: 更新输出子区间的部分属性
   *
   * 业务规则:
   * - 当更新 maxToken 时,级联更新下一个区间的 minToken (保证区间连续性)
   * - 其他字段更新不需要级联
   */
  const updateOutputSubRange = useCallback(
    (rangeId: string, outputRangeId: string, updates: Partial<OutputRangePrice>) => {
      const newRanges = normalizedRanges.map((r) => {
        if (r.id === rangeId) {
          const outputRangePrices = r.outputRangePrices || []

          // 📌 如果更新了 maxToken,需要级联更新下一个区间的 minToken
          if (updates.maxToken !== undefined) {
            const targetIndex = outputRangePrices.findIndex((or) => or.id === outputRangeId)
            const newMaxToken = updates.maxToken  // 提取为常量,确保类型为 number

            return {
              ...r,
              outputRangePrices: outputRangePrices.map((or, idx) => {
                // 更新当前区间的 maxToken
                if (or.id === outputRangeId) {
                  return { ...or, ...updates }
                }
                // 级联更新下一个区间的 minToken
                if (targetIndex !== -1 && idx === targetIndex + 1) {
                  return { ...or, minToken: newMaxToken }
                }
                return or
              }),
            }
          }

          // 其他字段更新不需要级联
          return {
            ...r,
            outputRangePrices: outputRangePrices.map((or) =>
              or.id === outputRangeId ? { ...or, ...updates } : or
            ),
          }
        }
        return r
      })
      onRangesChange(newRanges)
    },
    [normalizedRanges, onRangesChange]
  )

  return {
    addRange,
    removeRange,
    updateRange,
    updateMaxTokenWithCascade,
    handleEnableOutputRange,
    addOutputSubRange,
    removeOutputSubRange,
    updateOutputSubRange,
  }
}
