import React, { useMemo } from 'react'
import { formatBatchDiscount } from '@/lib/utils/price'

/**
 * 格式化 Token 范围显示
 * @param min - 最小 token 数
 * @param max - 最大 token 数,>=2147483647 时显示为 +∞
 * @returns 格式化后的范围字符串,如 "(1,000,100,000)"
 */
function formatTokenRange(min: number, max: number): string {
    const formatNumber = (n: number) => {
        if (n >= 2147483647) return "+∞"
        return n.toLocaleString()
    }
    return `(${formatNumber(min)},${formatNumber(max)})`
}

/**
 * TiersTable 组件
 * 职责:根据模型的分层价格数据(tiers),渲染价格表格
 * 包含上下文长度、输入/输出价格、图片价格、缓存命中价格等信息
 */
export const TiersTable = ({ data, discount }: { data: any, discount: number | undefined }) => {

    /**
     * 表格行数据类型定义
     */
    type TableRow = {
        contextLength: string
        input: string
        output: string
        imageInput: string
        imageOutput: string
        cachedHit: string
        cachedCreation: string
    }

    /**
     * 价格列配置
     * 定义所有可能出现的价格列及其 key 与显示标签
     * 通过 visibleColumns 过滤后动态渲染,避免固定列导致全为"-"的列显示
     */
    const PRICE_COLUMNS = [
        { key: 'input',          label: '输入' },
        { key: 'output',         label: '输出' },
        { key: 'imageInput',     label: '图片输入' },
        { key: 'imageOutput',    label: '图片输出' },
        { key: 'cachedHit',      label: '缓存命中' },
        { key: 'cachedCreation', label: '缓存创建' },
    ] as const

    /**
     * 使用 useMemo 计算表格行数据
     * 设计说明:
     * 1. 将数据格式化逻辑作为派生状态,仅在 data.tiers 变化时重新计算
     * 2. 支持两种数据模式:
     *    - 模式A: inputRangePrice + outputRangePrices (笛卡尔积)
     *    - 模式B: 仅 inputRangePrice (单行数据)
     * 3. 使用 flatMap 展开嵌套结构,生成扁平的表格行数组
     *
     * 数据结构映射:
     * - tier.inputRangePrice: { minToken, maxToken, input, output, ... } - 输入 token 范围和价格信息
     * - tier.outputRangePrices[]: (可选) 数组,每项包含:
     *   - minToken, maxToken: 输出 token 范围
     *   - output: 输出价格
     *   - imageInput, imageOutput, cachedRead: 可选的其他价格字段
     *
     * 价格字段映射规则:
     * - 模式A (有 outputRangePrices):
     *   - input: 从 tier.inputRangePrice.input 获取
     *   - output: 从 outputPrice.output 获取
     *   - 其他字段: 从 outputPrice 获取
     * - 模式B (无 outputRangePrices):
     *   - 所有字段均从 tier.inputRangePrice 获取
     *
     * 避免 re-render:
     * - useMemo 缓存计算结果,仅在依赖项 data.tiers 变化时重新执行
     * - 组件无内部状态,为纯展示组件
     */
    const tableRows = useMemo<TableRow[]>(() => {
        if (!Array.isArray(data?.tiers) || data.tiers.length === 0) {
            return []
        }

        return data.tiers.flatMap((tier: any) => {
            const formatPrice = (price: number | null | undefined): string => {
                if (price === null || price === undefined) return "-"
                return price.toString()
            }

            // 情况1: 存在 outputRangePrices，使用笛卡尔积逻辑
            // 将输入范围 x 输出范围的笛卡尔积展开为扁平的表格行
            if (Array.isArray(tier.outputRangePrices) && tier.outputRangePrices.length > 0) {
                return tier.outputRangePrices.map((outputPrice: any) => {
                    return {
                        contextLength: `输入 ${formatTokenRange(tier.inputRangePrice?.minToken ?? 0, tier.inputRangePrice?.maxToken ?? 0)}\n输出 ${formatTokenRange(outputPrice.minToken ?? 0, outputPrice.maxToken ?? 0)}`,
                        input: formatPrice(tier.inputRangePrice?.input),
                        output: formatPrice(outputPrice.output),
                        imageInput: formatPrice(outputPrice.imageInput),
                        imageOutput: formatPrice(outputPrice.imageOutput),
                        cachedHit: formatPrice(outputPrice.cachedRead),
                        cachedCreation: formatPrice(outputPrice.cachedCreation),
                    }
                })
            }

            // 情况2: 不存在 outputRangePrices，仅使用 inputRangePrice
            if (tier.inputRangePrice) {
                return [{
                    contextLength: `输入 ${formatTokenRange(tier.inputRangePrice?.minToken ?? 0, tier.inputRangePrice?.maxToken ?? 0)}`,
                    input: formatPrice(tier.inputRangePrice?.input),
                    output: formatPrice(tier.inputRangePrice?.output),
                    imageInput: formatPrice(tier.inputRangePrice?.imageInput),
                    imageOutput: formatPrice(tier.inputRangePrice?.imageOutput),
                    cachedHit: formatPrice(tier.inputRangePrice?.cachedRead),
                    cachedCreation: formatPrice(tier.inputRangePrice?.cachedCreation),
                }]
            }

            // 情况3: 两者都不存在，返回空数组
            return []
        })
    }, [data.tiers])

    /**
     * 可见列派生状态
     * 设计说明: 参考 v1 price-display.tsx 的 collectAllPriceFields 逻辑
     * 过滤掉所有行都是 "-" 的列,只渲染至少有一行有实际价格值的列
     * 避免 re-render: 依赖 tableRows,仅在行数据变化时重新计算
     */
    const visibleColumns = useMemo(() => {
        return PRICE_COLUMNS.filter(col =>
            tableRows.some(row => row[col.key] !== '-')
        )
    }, [tableRows])

    // 边界处理: 无数据时显示提示信息
    if (tableRows.length === 0) {
        return (
            <div className="mt-4 p-4 text-center text-sm text-slate-500 border border-slate-200 rounded-lg bg-slate-50">
                暂无价格数据
            </div>
        )
    }
    return (
        <div>
            {/* 费用表格: 仅渲染 visibleColumns 中的列,隐藏全为"-"的列 */}
            <div className="mt-4 border border-blue-500 rounded-lg overflow-hidden overflow-x-auto">
                <table className="w-full text-xs">
                    <thead>
                        <tr className="bg-slate-50">
                            <th className="px-2 py-2 text-center font-medium text-slate-700 border-r border-blue-500 whitespace-nowrap">
                                上下文长度
                            </th>
                            {visibleColumns.map((col, i) => (
                                <th
                                    key={col.key}
                                    className={`px-2 py-2 text-center font-medium text-slate-700 whitespace-nowrap${i < visibleColumns.length - 1 ? ' border-r border-blue-500' : ''}`}
                                >
                                    {col.label}
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {tableRows.map((row: TableRow, index: number) => (
                            <tr key={index} className="border-t border-blue-500">
                                <td className="px-2 py-2 text-center text-slate-600 border-r border-blue-500 whitespace-nowrap">
                                    {row.contextLength}
                                </td>
                                {visibleColumns.map((col, i) => (
                                    <td
                                        key={col.key}
                                        className={`px-2 py-2 text-center text-slate-700${i < visibleColumns.length - 1 ? ' border-r border-blue-500' : ''}`}
                                    >
                                        {row[col.key]}
                                    </td>
                                ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* 折扣和单位信息 */}
            <div className="flex justify-end gap-4 mt-2 text-xs text-slate-600">
                {formatBatchDiscount(data?.batchDiscount) && (
                  <span>批量折扣：{formatBatchDiscount(data?.batchDiscount)}</span>
                )}
                <span>
                    单位: {data?.unit}
                </span>
            </div>
        </div>
    )
}