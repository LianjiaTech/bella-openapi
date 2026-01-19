import React from 'react';
import {RangePrice, Tier} from "@/lib/types/openapi";
import {formatTokenRange} from "@/lib/utils/price-matcher";

const formatPrice = (price: number) => {
    return price.toString();
};

const collectPriceFields = (tier: Tier) => {
    const fields: Array<{ key: string; label: string }> = [];
    const checkRange = tier.outputRangePrices?.[0] || tier.inputRangePrice;
    if (!checkRange) return fields;

    if (checkRange.input && checkRange.input > 0) fields.push({ key: 'input', label: '输入' });
    if (checkRange.output && checkRange.output > 0) fields.push({ key: 'output', label: '输出' });
    if (checkRange.imageInput && checkRange.imageInput > 0) fields.push({ key: 'imageInput', label: '图片输入' });
    if (checkRange.imageOutput && checkRange.imageOutput > 0) fields.push({ key: 'imageOutput', label: '图片输出' });
    if (checkRange.cachedRead && checkRange.cachedRead > 0) fields.push({ key: 'cachedRead', label: '缓存命中' });
    if (checkRange.cachedCreation && checkRange.cachedCreation > 0) fields.push({ key: 'cachedCreation', label: '缓存创建' });

    return fields;
};

// 渲染单元格价格
const renderPriceCell = (range: RangePrice, key: string) => {
    const value = (range as any)[key];
    if (value === undefined || value === null || value <= 0) return '-';
    return formatPrice(value);
};

interface TableRow {
    inputRange: RangePrice;
    outputRange?: RangePrice;
}

const buildTableRows = (tiers: Tier[]): TableRow[] => {
    const rows: TableRow[] = [];

    tiers.forEach(tier => {
        if (!tier.inputRangePrice) return;

        const inputRange = tier.inputRangePrice;

        if (tier.outputRangePrices && tier.outputRangePrices.length > 0) {
            tier.outputRangePrices.forEach(outputRange => {
                rows.push({ inputRange, outputRange });
            });
        } else {
            rows.push({ inputRange });
        }
    });

    return rows;
};

// 收集所有 tier 中的价格列（取第一个有值的即可）
const collectAllPriceFields = (tiers: Tier[]) => {
    for (const tier of tiers) {
        const fields = collectPriceFields(tier);
        if (fields.length > 0) return fields;
    }
    return [];
};

export const RenderAllTiersTable = ({ tiers }: { tiers: Tier[] }) => {
    if (!tiers || tiers.length === 0) return null;

    const rows = buildTableRows(tiers);
    const priceFields = collectAllPriceFields(tiers);

    return (
        <div className="mb-4">
            <div className="overflow-x-auto overflow-y-auto max-h-[300px] border border-gray-200 rounded-lg">
                <table className="min-w-full bg-white table-auto">
                    <thead className="bg-gray-50">
                    <tr className="text-xs">
                        <th className="sticky top-0 z-30 bg-gray-50 p-3 font-medium text-gray-700 border-l border-gray-200 whitespace-nowrap">
                            上下文长度
                        </th>
                        {priceFields.map(field => (
                            <th key={field.key}
                                className="sticky top-0 z-30 bg-gray-50 p-3 text-center font-medium text-gray-700 border-l border-gray-200 whitespace-nowrap">
                                {field.label}
                            </th>
                        ))}
                    </tr>
                    </thead>

                    <tbody>
                    {rows.map((row, idx) => {
                        const isEven = idx % 2 === 0;
                        const bg = isEven ? 'bg-white' : 'bg-gray-50';

                        return (
                            <tr key={idx} className={`text-xs ${bg} border-t border-gray-100`}>
                                <td className={`sticky left-0 z-10 p-3 border-r border-gray-100 whitespace-nowrap ${bg}`}>
                                    <div className="text-center text-gray-900">
                                        <div>
                                            输入 {formatTokenRange(row.inputRange.minToken, row.inputRange.maxToken)}
                                        </div>
                                        {row.outputRange && (
                                            <div className="mt-1">
                                                输出 {formatTokenRange(row.outputRange.minToken, row.outputRange.maxToken)}
                                            </div>
                                        )}
                                    </div>
                                </td>

                                {priceFields.map(field => (
                                    <td key={field.key} className="p-3 text-center border-l border-gray-100 text-gray-900 whitespace-nowrap">
                                        {renderPriceCell(row.outputRange ?? row.inputRange, field.key)}
                                    </td>
                                ))}
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>
        </div>
    );
};
