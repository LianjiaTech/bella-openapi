'use client';

import React, {useState, useEffect, forwardRef, useImperativeHandle} from 'react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {CompletionPriceInfo, Tier, RangePrice} from '@/lib/types/openapi';
import {PlusIcon, TrashIcon} from '@radix-ui/react-icons';
import {Switch} from '@/components/ui/switch';
import {formatTokenRange} from "@/lib/utils/price-matcher";
import {useToast} from '@/hooks/use-toast';

interface CompletionPriceEditorProps {
    value: CompletionPriceInfo | null;
    onChange: (value: CompletionPriceInfo) => void;
}

export interface CompletionPriceEditorRef {
    validate: () => boolean;
}

const MAX_TOKEN = 2147483647;

export const CompletionPriceEditor = forwardRef<CompletionPriceEditorRef, CompletionPriceEditorProps>(
    ({value, onChange}, ref) => {
        const {toast} = useToast();
        const [priceInfo, setPriceInfo] = useState<CompletionPriceInfo>({
            unit: '分/千token',
            batchDiscount: 1.0,
            tiers: [
                {
                    inputRangePrice: {
                        minToken: 0,
                        maxToken: MAX_TOKEN,
                        input: undefined as any,
                        output: undefined as any,
                    },
                    outputRangePrices: undefined,
                },
            ],
        });

        // 仅在 value 从外部传入时更新内部状态（避免无限循环）
        useEffect(() => {
            if (value && value.tiers && value.tiers.length > 0) {
                setPriceInfo(value);
            }
        }, [value]);

        // 当内部状态变化时，通知父组件（但不要在这里自动触发）
        const notifyChange = (newPriceInfo: CompletionPriceInfo) => {
            setPriceInfo(newPriceInfo);
            onChange(newPriceInfo);
        };

        // 暴露验证方法给父组件
        useImperativeHandle(ref, () => ({
            validate: validatePriceInfo,
        }));

        // 验证单个 RangePrice
        const validateRangePrice = (rangePrice: RangePrice, tierIndex: number, isOutput: boolean, outputIndex?: number): string | null => {
            const prefix = isOutput ? `区间 ${tierIndex + 1} - 输出区间 ${outputIndex! + 1}` : `区间 ${tierIndex + 1}`;

            // 验证token范围
            if (rangePrice.minToken < 0 || rangePrice.maxToken < 0) {
                return `${prefix}: Token 范围不能为负数`;
            }
            if (rangePrice.minToken >= rangePrice.maxToken) {
                return `${prefix}: 最小Token (${rangePrice.minToken}) 必须小于最大Token (${rangePrice.maxToken})`;
            }

            // 验证必填价格字段: input 和 output 必须存在且 > 0
            if (!rangePrice.input || rangePrice.input <= 0) {
                return `${prefix}: 输入价格必须大于 0`;
            }
            if (!rangePrice.output || rangePrice.output <= 0) {
                return `${prefix}: 输出价格必须大于 0`;
            }

            // 验证可选价格字段: 如果不为空，则必须 > 0
            if (rangePrice.imageInput !== undefined && rangePrice.imageInput !== null && rangePrice.imageInput <= 0) {
                return `${prefix}: 图片输入价格必须大于 0`;
            }
            if (rangePrice.imageOutput !== undefined && rangePrice.imageOutput !== null && rangePrice.imageOutput <= 0) {
                return `${prefix}: 图片输出价格必须大于 0`;
            }
            if (rangePrice.cachedRead !== undefined && rangePrice.cachedRead !== null && rangePrice.cachedRead <= 0) {
                return `${prefix}: 缓存读取价格必须大于 0`;
            }
            if (rangePrice.cachedCreation !== undefined && rangePrice.cachedCreation !== null && rangePrice.cachedCreation <= 0) {
                return `${prefix}: 缓存创建价格必须大于 0`;
            }

            return null;
        };

        // 验证单个 Tier
        const validateTier = (tier: Tier, tierIndex: number): string | null => {
            // inputRangePrice 必须存在且有效
            if (!tier.inputRangePrice) {
                return `区间 ${tierIndex + 1}: 缺少输入Token范围配置`;
            }

            const inputError = validateRangePrice(tier.inputRangePrice, tierIndex, false);
            if (inputError) return inputError;

            // outputRangePrices 如果存在，必须不为空且每个都有效
            if (tier.outputRangePrices) {
                if (tier.outputRangePrices.length === 0) {
                    return `区间 ${tierIndex + 1}: 启用了输出区间定价，但没有配置任何输出区间`;
                }

                for (let i = 0; i < tier.outputRangePrices.length; i++) {
                    const outputError = validateRangePrice(tier.outputRangePrices[i], tierIndex, true, i);
                    if (outputError) return outputError;
                }
            }

            return null;
        };

        // 验证区间覆盖性（连续且无重叠）
        const validateCoverage = (ranges: RangePrice[], tierIndex: number, isOutput: boolean): string | null => {
            if (!ranges || ranges.length === 0) {
                return null;
            }

            const prefix = isOutput ? `区间 ${tierIndex + 1} 的输出区间` : '输入Token区间';

            // 1. 按起始位置排序，并记录原始索引
            const sortedRangesWithIndex = ranges.map((range, idx) => ({range, originalIndex: idx}))
                .sort((a, b) => a.range.minToken - b.range.minToken);

            // 2. 检查第一个区间是否从 0 开始
            if (sortedRangesWithIndex[0].range.minToken !== 0) {
                const displayIndex = isOutput
                    ? `输出区间 ${sortedRangesWithIndex[0].originalIndex + 1}`
                    : `区间 ${sortedRangesWithIndex[0].originalIndex + 1}`;
                return `${prefix}: ${displayIndex} 的最小值应该为 0，当前为 ${sortedRangesWithIndex[0].range.minToken}（这是第一个区间）`;
            }

            // 3. 检查连续性
            let expectedStart = 0;
            for (let i = 0; i < sortedRangesWithIndex.length; i++) {
                const {range, originalIndex} = sortedRangesWithIndex[i];
                if (range.minToken !== expectedStart) {
                    const displayIndex = isOutput
                        ? `输出区间 ${originalIndex + 1}`
                        : `区间 ${originalIndex + 1}`;
                    return `${prefix}: ${displayIndex} 不连续，期望最小值为 ${expectedStart}，但实际为 ${range.minToken}`;
                }
                expectedStart = range.maxToken;
            }

            // 4. 检查最后一个区间是否到达 MAX_TOKEN
            if (expectedStart < MAX_TOKEN) {
                const lastIndex = sortedRangesWithIndex[sortedRangesWithIndex.length - 1].originalIndex;
                const displayIndex = isOutput
                    ? `输出区间 ${lastIndex + 1}`
                    : `区间 ${lastIndex + 1}`;
                return `${prefix}: ${displayIndex} 的最大值必须为 +∞，当前为 ${expectedStart}（这是最后一个区间）`;
            }

            return null;
        };

        // 完整验证
        const validatePriceInfo = (): boolean => {
            // tiers 必须存在且不为空
            if (!priceInfo.tiers || priceInfo.tiers.length === 0) {
                toast({
                    title: '验证失败',
                    description: '至少需要配置一个价格区间',
                    variant: 'destructive',
                });
                return false;
            }

            // 验证每个 tier 的结构有效性
            for (let i = 0; i < priceInfo.tiers.length; i++) {
                const tierError = validateTier(priceInfo.tiers[i], i);
                if (tierError) {
                    toast({
                        title: '验证失败',
                        description: tierError,
                        variant: 'destructive',
                    });
                    return false;
                }
            }

            // 验证所有 inputRangePrice 的 token 范围覆盖性
            const inputRanges = priceInfo.tiers.map(tier => tier.inputRangePrice);
            const coverageError = validateCoverage(inputRanges, 0, false);
            if (coverageError) {
                toast({
                    title: '验证失败',
                    description: coverageError,
                    variant: 'destructive',
                });
                return false;
            }

            // 验证每个 tier 的 outputRangePrices 的覆盖性
            for (let i = 0; i < priceInfo.tiers.length; i++) {
                const tier = priceInfo.tiers[i];
                if (tier.outputRangePrices && tier.outputRangePrices.length > 0) {
                    const outputCoverageError = validateCoverage(tier.outputRangePrices, i, true);
                    if (outputCoverageError) {
                        toast({
                            title: '验证失败',
                            description: outputCoverageError,
                            variant: 'destructive',
                        });
                        return false;
                    }
                }
            }

            return true;
        };

        const addTier = () => {
            const lastTier = priceInfo.tiers[priceInfo.tiers.length - 1];
            const updatedTiers = [...priceInfo.tiers];

            updatedTiers[updatedTiers.length - 1] = {
                ...lastTier,
                inputRangePrice: {
                    ...lastTier.inputRangePrice,
                    maxToken: '' as any,
                },
            };

            const newTier: Tier = {
                inputRangePrice: {
                    minToken: '' as any,
                    maxToken: MAX_TOKEN,
                    input: undefined as any,
                    output: undefined as any,
                },
                outputRangePrices: lastTier.outputRangePrices ? [] : undefined,
            };

            notifyChange({
                ...priceInfo,
                tiers: [...updatedTiers, newTier],
            });
        };

        const removeTier = (index: number) => {
            if (priceInfo.tiers.length <= 1) {
                return; // 至少保留一个tier
            }

            const updatedTiers = priceInfo.tiers.filter((_, i) => i !== index);

            // 如果删除的是最后一个，更新倒数第二个的maxToken为MAX_TOKEN
            if (index === priceInfo.tiers.length - 1) {
                updatedTiers[updatedTiers.length - 1] = {
                    ...updatedTiers[updatedTiers.length - 1],
                    inputRangePrice: {
                        ...updatedTiers[updatedTiers.length - 1].inputRangePrice,
                        maxToken: MAX_TOKEN,
                    },
                };
            }

            updatedTiers[0] = {
                ...updatedTiers[0],
                inputRangePrice: {
                    ...updatedTiers[0].inputRangePrice,
                    minToken: 0,
                },
            };

            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        const updateTierInputRange = (tierIndex: number, field: keyof RangePrice, value: number | undefined) => {
            const updatedTiers = [...priceInfo.tiers];
            updatedTiers[tierIndex] = {
                ...updatedTiers[tierIndex],
                inputRangePrice: {
                    ...updatedTiers[tierIndex].inputRangePrice,
                    [field]: value,
                },
            };

            // 自动更新相邻tier的边界
            if (field === 'maxToken' && tierIndex < updatedTiers.length - 1 && value !== undefined) {
                updatedTiers[tierIndex + 1] = {
                    ...updatedTiers[tierIndex + 1],
                    inputRangePrice: {
                        ...updatedTiers[tierIndex + 1].inputRangePrice,
                        minToken: value,
                    },
                };
            }

            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        const toggleOutputRanges = (tierIndex: number, enabled: boolean) => {
            const updatedTiers = [...priceInfo.tiers];
            if (enabled) {
                updatedTiers[tierIndex] = {
                    ...updatedTiers[tierIndex],
                    outputRangePrices: [
                        {
                            minToken: 0,
                            maxToken: MAX_TOKEN,
                            input: updatedTiers[tierIndex].inputRangePrice.input ?? undefined,
                            output: updatedTiers[tierIndex].inputRangePrice.output ?? undefined,
                        },
                    ],
                };
            } else {
                updatedTiers[tierIndex] = {
                    ...updatedTiers[tierIndex],
                    outputRangePrices: undefined,
                };
            }
            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        const addOutputRange = (tierIndex: number) => {
            const tier = priceInfo.tiers[tierIndex];
            if (!tier.outputRangePrices || tier.outputRangePrices.length === 0) {
                return;
            }

            const lastOutputRange = tier.outputRangePrices[tier.outputRangePrices.length - 1];
            const updatedOutputRanges = [...tier.outputRangePrices];

            // 将当前最后一个输出区间的 maxToken 设置为空，让用户填写
            updatedOutputRanges[updatedOutputRanges.length - 1] = {
                ...lastOutputRange,
                maxToken: '' as any,
            };

            const newOutputRange: RangePrice = {
                minToken: '' as any,
                maxToken: MAX_TOKEN,
                input: undefined as any,
                output: undefined as any,
            };

            const updatedTiers = [...priceInfo.tiers];
            updatedTiers[tierIndex] = {
                ...tier,
                outputRangePrices: [...updatedOutputRanges, newOutputRange],
            };

            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        const removeOutputRange = (tierIndex: number, outputIndex: number) => {
            const tier = priceInfo.tiers[tierIndex];
            if (!tier.outputRangePrices || tier.outputRangePrices.length <= 1) {
                return;
            }

            const updatedOutputRanges = tier.outputRangePrices.filter((_, i) => i !== outputIndex);

            // 如果删除的是最后一个，更新倒数第二个的maxToken
            if (outputIndex === tier.outputRangePrices.length - 1) {
                updatedOutputRanges[updatedOutputRanges.length - 1] = {
                    ...updatedOutputRanges[updatedOutputRanges.length - 1],
                    maxToken: MAX_TOKEN,
                };
            }

            updatedOutputRanges[0] = {
                ...updatedOutputRanges[0],
                minToken: 0,
            };

            const updatedTiers = [...priceInfo.tiers];
            updatedTiers[tierIndex] = {
                ...tier,
                outputRangePrices: updatedOutputRanges,
            };

            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        const updateOutputRange = (
            tierIndex: number,
            outputIndex: number,
            field: keyof RangePrice,
            value: number | undefined
        ) => {
            const tier = priceInfo.tiers[tierIndex];
            if (!tier.outputRangePrices) {
                return;
            }

            const updatedOutputRanges = [...tier.outputRangePrices];
            updatedOutputRanges[outputIndex] = {
                ...updatedOutputRanges[outputIndex],
                [field]: value,
            };

            // 自动更新相邻输出范围的边界
            if (field === 'maxToken' && outputIndex < updatedOutputRanges.length - 1 && value !== undefined) {
                updatedOutputRanges[outputIndex + 1] = {
                    ...updatedOutputRanges[outputIndex + 1],
                    minToken: value,
                };
            }

            const updatedTiers = [...priceInfo.tiers];
            updatedTiers[tierIndex] = {
                ...tier,
                outputRangePrices: updatedOutputRanges,
            };

            notifyChange({...priceInfo, tiers: updatedTiers});
        };

        return (
            <div className="space-y-4">
                <div className="space-y-3">
                    <div className="flex items-center justify-end">
                        <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={addTier}
                        >
                            <PlusIcon className="mr-1 h-4 w-4"/>
                            添加区间
                        </Button>
                    </div>

                    {priceInfo.tiers.map((tier, tierIndex) => (
                        <Card key={tierIndex} className="border-l-4 border-l-blue-500">
                            <CardHeader className="pb-3">
                                <div className="flex items-center justify-between">
                                    <CardTitle className="text-sm font-medium">
                                        区间 {tierIndex + 1}
                                    </CardTitle>
                                    {priceInfo.tiers.length > 1 && (
                                        <Button
                                            type="button"
                                            size="sm"
                                            variant="ghost"
                                            onClick={() => removeTier(tierIndex)}
                                        >
                                            <TrashIcon className="h-4 w-4 text-red-500"/>
                                        </Button>
                                    )}
                                </div>
                            </CardHeader>
                            <CardContent className="space-y-4">
                                {/* 输入范围 */}
                                <div className="space-y-2">
                                    <Label className="text-sm font-medium text-gray-700">输入Token范围</Label>
                                    <div className="grid grid-cols-2 gap-2">
                                        <div className="space-y-1">
                                            <Label className="text-xs text-gray-500">最小值 (minToken)</Label>
                                            <Input
                                                type="number"
                                                min="0"
                                                value={tier.inputRangePrice.minToken}
                                                onChange={(e) => {
                                                    const val = parseInt(e.target.value) || 0;
                                                    updateTierInputRange(tierIndex, 'minToken', val);
                                                }}
                                                disabled={tierIndex === 0}
                                                className={tierIndex === 0 ? 'bg-gray-100' : ''}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs text-gray-500">最大值 (maxToken)</Label>
                                            <Input
                                                type="number"
                                                min="0"
                                                value={!tier.inputRangePrice.maxToken || tier.inputRangePrice.maxToken >= MAX_TOKEN ? '' : tier.inputRangePrice.maxToken}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? MAX_TOKEN : parseInt(e.target.value) || 0;
                                                    updateTierInputRange(tierIndex, 'maxToken', val);
                                                }}
                                                placeholder={tierIndex === priceInfo.tiers.length - 1 ? "+∞" : ""}
                                                disabled={tierIndex === priceInfo.tiers.length - 1}
                                                className={tierIndex === priceInfo.tiers.length - 1 ? 'bg-gray-100' : ''}
                                            />
                                            <p className="text-xs text-gray-500">
                                                区间: {formatTokenRange(tier.inputRangePrice.minToken, tier.inputRangePrice.maxToken)}
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                {/* 输出区间开关 */}
                                <div className="flex items-center justify-between py-2 border-t border-gray-200">
                                    <Label className="text-sm">启用输出Token区间</Label>
                                    <Switch
                                        checked={!!tier.outputRangePrices}
                                        onCheckedChange={(checked) => toggleOutputRanges(tierIndex, checked)}
                                    />
                                </div>

                                {!tier.outputRangePrices ? (
                                    // 简单定价模式（无输出区间）
                                    <div className="grid grid-cols-6 gap-2">
                                        <div className="space-y-1">
                                            <Label className="text-xs">输入价格 <span className="text-red-500">*</span></Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.input ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'input', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="必填"
                                                required
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">输出价格 <span className="text-red-500">*</span></Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.output ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'output', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="必填"
                                                required
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">图片输入</Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.imageInput ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'imageInput', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="可选"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">图片输出</Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.imageOutput ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'imageOutput', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="可选"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">缓存读取</Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.cachedRead ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'cachedRead', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="可选"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">缓存创建</Label>
                                            <Input
                                                type="number"
                                                step="any"
                                                min="0"
                                                value={tier.inputRangePrice.cachedCreation ?? ''}
                                                onChange={(e) => {
                                                    const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                    updateTierInputRange(tierIndex, 'cachedCreation', val === undefined || isNaN(val) ? undefined : val);
                                                }}
                                                placeholder="可选"
                                            />
                                        </div>
                                        <div className="col-span-6 flex justify-end mt-1">
                                            <span className="text-xs text-gray-500">单位：分/千token</span>
                                        </div>
                                    </div>
                                ) : (
                                    // 输出区间定价模式
                                    <div className="space-y-3">
                                        <div className="flex items-center justify-between">
                                            <Label className="text-sm font-medium text-gray-700">输出Token区间</Label>
                                            <Button
                                                type="button"
                                                size="sm"
                                                variant="outline"
                                                onClick={() => addOutputRange(tierIndex)}
                                            >
                                                <PlusIcon className="mr-1 h-3 w-3"/>
                                                添加输出区间
                                            </Button>
                                        </div>

                                        {tier.outputRangePrices.map((outputRange, outputIndex) => (
                                            <div key={outputIndex} className="p-3 bg-gray-50 rounded-lg space-y-3">
                                                <div className="flex items-center justify-between">
                                                    <Label className="text-xs font-medium">输出区间 {outputIndex + 1}</Label>
                                                    {tier.outputRangePrices && tier.outputRangePrices.length > 1 && (
                                                        <Button
                                                            type="button"
                                                            size="sm"
                                                            variant="ghost"
                                                            onClick={() => removeOutputRange(tierIndex, outputIndex)}
                                                        >
                                                            <TrashIcon className="h-3 w-3 text-red-500"/>
                                                        </Button>
                                                    )}
                                                </div>

                                                <div className="grid grid-cols-2 gap-2">
                                                    <div className="space-y-1">
                                                        <Label className="text-xs text-gray-500">最小值</Label>
                                                        <Input
                                                            type="number"
                                                            min="0"
                                                            value={outputRange.minToken}
                                                            onChange={(e) => {
                                                                const val = parseInt(e.target.value) || 0;
                                                                updateOutputRange(tierIndex, outputIndex, 'minToken', val);
                                                            }}
                                                            disabled={outputIndex === 0}
                                                            className={outputIndex === 0 ? 'bg-gray-200 text-xs' : 'text-xs'}
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs text-gray-500">最大值</Label>
                                                        <Input
                                                            type="number"
                                                            min="0"
                                                            value={!outputRange.maxToken || outputRange.maxToken >= MAX_TOKEN ? '' : outputRange.maxToken}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? MAX_TOKEN : parseInt(e.target.value) || 0;
                                                                updateOutputRange(tierIndex, outputIndex, 'maxToken', val);
                                                            }}
                                                            placeholder={
                                                                tier.outputRangePrices &&
                                                                outputIndex === tier.outputRangePrices.length - 1
                                                                    ? "+∞"
                                                                    : ""
                                                            }
                                                            disabled={
                                                                tier.outputRangePrices &&
                                                                outputIndex === tier.outputRangePrices.length - 1
                                                            }
                                                            className={
                                                                tier.outputRangePrices &&
                                                                outputIndex === tier.outputRangePrices.length - 1
                                                                    ? 'bg-gray-200 text-xs'
                                                                    : 'text-xs'
                                                            }
                                                        />
                                                        <p className="text-xs text-gray-500">
                                                            {formatTokenRange(outputRange.minToken, outputRange.maxToken)}
                                                        </p>
                                                    </div>
                                                </div>

                                                <div className="grid grid-cols-6 gap-2">
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">输入价格 <span className="text-red-500">*</span></Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.input ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'input', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="必填"
                                                            className="text-xs"
                                                            required
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">输出价格 <span className="text-red-500">*</span></Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.output ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'output', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="必填"
                                                            className="text-xs"
                                                            required
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">图片输入</Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.imageInput ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'imageInput', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="可选"
                                                            className="text-xs"
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">图片输出</Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.imageOutput ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'imageOutput', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="可选"
                                                            className="text-xs"
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">缓存读取</Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.cachedRead ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'cachedRead', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="可选"
                                                            className="text-xs"
                                                        />
                                                    </div>
                                                    <div className="space-y-1">
                                                        <Label className="text-xs">缓存创建</Label>
                                                        <Input
                                                            type="number"
                                                            step="any"
                                                            min="0"
                                                            value={outputRange.cachedCreation ?? ''}
                                                            onChange={(e) => {
                                                                const val = e.target.value === '' ? undefined : parseFloat(e.target.value);
                                                                updateOutputRange(tierIndex, outputIndex, 'cachedCreation', val === undefined || isNaN(val) ? undefined : val);
                                                            }}
                                                            placeholder="可选"
                                                            className="text-xs"
                                                        />
                                                    </div>
                                                    <div className="col-span-6 flex justify-end mt-1">
                                                        <span className="text-xs text-gray-500">单位：分/千token</span>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    ))}
                </div>

                <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-gray-600">
                    <p className="font-medium mb-1">提示:</p>
                    <ul className="list-disc list-inside space-y-1">
                        <li>所有价格的单位都是【分/千token】</li>
                        <li>第一个区间必须从0开始，最后一个区间必须到+∞</li>
                        <li>启用输出区间后，可以根据输出Token数量设置不同价格</li>
                        <li>缓存相关价格为可选字段，适用于支持缓存的模型</li>
                    </ul>
                </div>
            </div>
        );
    });

CompletionPriceEditor.displayName = 'CompletionPriceEditor';
