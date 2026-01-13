import React, {useState, useEffect} from 'react';
import {Card} from "@/components/ui/card";
import {Label} from "@/components/ui/label";
import {Input} from "@/components/ui/input";
import {Button} from "@/components/ui/button";
import {RadioGroup, RadioGroupItem} from "@/components/ui/radio-group";
import {Alert, AlertDescription} from "@/components/ui/alert";
import {AlertCircle, Plus, Trash2} from "lucide-react";

interface Tier {
    minInputTokens: number;
    maxInputTokens: number | null;
    minOutputTokens: number | null;
    maxOutputTokens: number | null;
    inputPrice: string;
    outputPrice: string;
    imageInputPrice?: string;
    imageOutputPrice?: string;
    cachedReadPrice?: string;
    cachedCreationPrice?: string;
}

interface PriceInfo {
    mode: 'fixed' | 'tiered';
    input?: string;
    output?: string;
    imageInput?: string;
    imageOutput?: string;
    cachedRead?: string;
    cachedCreation?: string;
    unit?: string;
    batchDiscount?: number;
    tiers?: Tier[];
}

interface PriceInfoEditorProps {
    value: string;
    onUpdate: (value: string) => void;
    onValidate?: () => string | null;
    endpoint?: string;
}

export function PriceInfoEditor({
                                    value,
                                    onUpdate,
                                    onValidate,
                                    endpoint
                                }: PriceInfoEditorProps) {
    // 判断是否支持区间定价：只有 /v1/chat/completions 支持
    const supportsTieredPricing = endpoint === '/v1/chat/completions';
    const [priceInfo, setPriceInfo] = useState<PriceInfo>(() => {
        try {
            const parsed = JSON.parse(value || '{}');
            return {
                mode: parsed.mode || 'fixed',
                ...parsed
            };
        } catch {
            return {mode: 'fixed'};
        }
    });

    const [validationError, setValidationError] = useState<string | null>(null);

    // 验证价格区间配置
    const validateTiers = (tiers: Tier[]): string | null => {
        if (!tiers || tiers.length < 2) {
            return '区间定价至少需要两个价格区间';
        }

        const basicError = validateBasicFields(tiers);
        if (basicError) return basicError;
        const sorted = [...tiers].sort((a, b) => {
            if (a.minInputTokens !== b.minInputTokens) {
                return (a.minInputTokens || 0) - (b.minInputTokens || 0);
            }
            return (a.minOutputTokens || 0) - (b.minOutputTokens || 0);
        });
        return validateCompleteCoverage(sorted);
    };

    const validateBasicFields = (tiers: Tier[]): string | null => {
        for (let i = 0; i < tiers.length; i++) {
            const t = tiers[i];
            const tierLabel = `区间 ${i + 1}`;

            // 输入token区间验证
            if (t.minInputTokens == null || t.minInputTokens < 0) {
                return `${tierLabel}：最小输入Token必须 >= 0`;
            }

            if (t.maxInputTokens != null && t.minInputTokens >= t.maxInputTokens) {
                return `${tierLabel}：最小输入Token必须 < 最大输入Token`;
            }

            // 必填价格验证
            const inputPrice = parseFloat(t.inputPrice);
            if (!t.inputPrice || isNaN(inputPrice) || inputPrice <= 0) {
                return `${tierLabel}：输入Token单价必须 > 0`;
            }

            const outputPrice = parseFloat(t.outputPrice);
            if (!t.outputPrice || isNaN(outputPrice) || outputPrice <= 0) {
                return `${tierLabel}：输出Token单价必须 > 0`;
            }

            // 可选价格验证（如果填写必须 > 0）
            const optionalPrices = [
                {value: t.imageInputPrice, name: '图像输入价格'},
                {value: t.imageOutputPrice, name: '图像输出价格'},
                {value: t.cachedReadPrice, name: '缓存读取价格'},
                {value: t.cachedCreationPrice, name: '缓存创建价格'}
            ];

            for (const price of optionalPrices) {
                if (price.value) {
                    const val = parseFloat(price.value);
                    if (isNaN(val) || val <= 0) {
                        return `${tierLabel}：${price.name}必须 > 0`;
                    }
                }
            }

            // 输出token区间验证
            if (t.maxOutputTokens != null && t.minOutputTokens == null) {
                return `${tierLabel}：不能只设置最大输出Token，必须同时设置最小输出Token`;
            }

            if (t.minOutputTokens != null) {
                if (t.minOutputTokens < 0) {
                    return `${tierLabel}：最小输出Token必须 >= 0`;
                }
                if (t.maxOutputTokens != null && t.minOutputTokens >= t.maxOutputTokens) {
                    return `${tierLabel}：最小输出Token必须 < 最大输出Token`;
                }
            }
        }

        return null;
    };

    // 验证完整覆盖性
    const validateCompleteCoverage = (sorted: Tier[]): string | null => {
        if (sorted[0].minInputTokens !== 0) {
            return '第一个区间的最小输入Token必须为 0';
        }

        let i = 0;
        let expectedInputStart: number | null = 0;
        while (i < sorted.length) {
            const curr = sorted[i];
            if (curr.minInputTokens !== expectedInputStart) {
                if (expectedInputStart === null) {
                    return '输入区间存在冗余：已有区间延伸到无穷，不应再有后续区间';
                }
                if (curr.minInputTokens < expectedInputStart) {
                    return `输入区间重叠：区间起点 ${curr.minInputTokens} 小于期望起点 ${expectedInputStart}`;
                }
                return `输入区间不连续：${expectedInputStart} 到 ${curr.minInputTokens} 之间存在空隙`;
            }

            const groupInputMin = curr.minInputTokens;
            const groupInputMax = curr.maxInputTokens;
            const groupStart = i;
            while (i < sorted.length &&
            sorted[i].minInputTokens === groupInputMin &&
            sorted[i].maxInputTokens === groupInputMax) {
                i++;
            }

            const group = sorted.slice(groupStart, i);

            // 验证同组内的输出区间完整覆盖
            const outputError = validateOutputCoverageInGroup(group, groupInputMin, groupInputMax);
            if (outputError) return outputError;
            expectedInputStart = groupInputMax;
        }

        // 最后一个输入区间的max必须为null
        if (expectedInputStart !== null) {
            return '最后一个区间的最大输入Token必须为空（表示∞）';
        }

        return null;
    };


    // 验证组内输出区间完整覆盖
    const validateOutputCoverageInGroup = (
        group: Tier[],
        groupInputMin: number,
        groupInputMax: number | null
    ): string | null => {
        const rangeLabel = `输入区间 [${groupInputMin}, ${groupInputMax === null ? '∞' : groupInputMax}]`;

        if (group.length === 1 && group[0].minOutputTokens == null) {
            return null;
        }

        const hasUndefinedOutput = group.some(t => t.minOutputTokens == null);
        if (hasUndefinedOutput) {
            if (group.length > 1) {
                return `${rangeLabel} 内有多个价格区间，必须全部定义输出Token区间`;
            }
            return `${rangeLabel} 的输出Token区间定义不完整`;
        }

        if (group[0].minOutputTokens !== 0) {
            return `${rangeLabel} 的输出Token区间必须从 0 开始`;
        }

        let expectedOutputStart: number | null = 0;
        for (let j = 0; j < group.length; j++) {
            const t = group[j];
            if (t.minOutputTokens !== expectedOutputStart) {
                if (expectedOutputStart === null) {
                    return `${rangeLabel} 的输出区间存在冗余：已有输出区间延伸到无穷`;
                }
                if (t.minOutputTokens! < expectedOutputStart) {
                    return `${rangeLabel} 的输出区间重叠：起点 ${t.minOutputTokens} 小于期望起点 ${expectedOutputStart}`;
                }
                return `${rangeLabel} 的输出区间不连续：${expectedOutputStart} 到 ${t.minOutputTokens} 之间存在空隙`;
            }
            expectedOutputStart = t.maxOutputTokens;
        }

        // 最后一个输出区间的max必须为null
        if (expectedOutputStart !== null) {
            return `${rangeLabel}：最后一个区间的最大输出Token必须为空（表示∞）`;
        }

        return null;
    };

    const handleModeChange = (mode: 'fixed' | 'tiered') => {
        const newPriceInfo = mode === 'tiered' && !priceInfo.tiers
            ? {
                ...priceInfo,
                mode,
                tiers: [{
                    minInputTokens: 0,
                    maxInputTokens: null,
                    minOutputTokens: null,
                    maxOutputTokens: null,
                    inputPrice: priceInfo.input || '',
                    outputPrice: priceInfo.output || '',
                }]
            }
            : {...priceInfo, mode};

        setPriceInfo(newPriceInfo);
        updateOutput(newPriceInfo);
    };

    const addTier = () => {
        const tiers = priceInfo.tiers || [];
        const lastTier = tiers[tiers.length - 1];

        const newTier: Tier = {
            minInputTokens: lastTier?.maxInputTokens || 0, // 从上一个区间的max开始
            maxInputTokens: null, // 最后一个区间，max为null
            minOutputTokens: null,
            maxOutputTokens: null,
            inputPrice: lastTier?.inputPrice || '',
            outputPrice: lastTier?.outputPrice || '',
        };

        const newPriceInfo = {
            ...priceInfo,
            tiers: [...tiers, newTier]
        };

        setPriceInfo(newPriceInfo);
        updateOutput(newPriceInfo);
    };

    const removeTier = (index: number) => {
        const tiers = priceInfo.tiers || [];
        const newPriceInfo = {
            ...priceInfo,
            tiers: tiers.filter((_, i) => i !== index)
        };
        setPriceInfo(newPriceInfo);
        updateOutput(newPriceInfo);
    };

    const updateTier = (index: number, field: keyof Tier, value: any) => {
        const tiers = [...(priceInfo.tiers || [])];
        tiers[index] = {...tiers[index], [field]: value};

        if (field === 'maxInputTokens' && index < tiers.length - 1 && value != null) {
            tiers[index + 1].minInputTokens = value;
        }

        const newPriceInfo = {...priceInfo, tiers};
        setPriceInfo(newPriceInfo);
        updateOutput(newPriceInfo);
    };

    // 将 priceInfo 转换为 JSON 并更新父组件
    const updateOutput = (info: PriceInfo) => {
        const output: any = {
            mode: info.mode,
            unit: info.unit || '分/千token',
            batchDiscount: info.batchDiscount || 1.0,
        };

        if (info.mode === 'fixed') {
            output.input = parseFloat(info.input || '0');
            output.output = parseFloat(info.output || '0');
            if (info.imageInput) output.imageInput = parseFloat(info.imageInput);
            if (info.imageOutput) output.imageOutput = parseFloat(info.imageOutput);
            if (info.cachedRead) output.cachedRead = parseFloat(info.cachedRead);
            if (info.cachedCreation) output.cachedCreation = parseFloat(info.cachedCreation);
        } else {
            output.tiers = (info.tiers || []).map((t) => ({
                minInputTokens: t.minInputTokens,
                maxInputTokens: t.maxInputTokens,
                minOutputTokens: t.minOutputTokens,
                maxOutputTokens: t.maxOutputTokens,
                inputPrice: parseFloat(t.inputPrice),
                outputPrice: parseFloat(t.outputPrice),
                imageInputPrice: t.imageInputPrice ? parseFloat(t.imageInputPrice) : undefined,
                imageOutputPrice: t.imageOutputPrice ? parseFloat(t.imageOutputPrice) : undefined,
                cachedReadPrice: t.cachedReadPrice ? parseFloat(t.cachedReadPrice) : undefined,
                cachedCreationPrice: t.cachedCreationPrice ? parseFloat(t.cachedCreationPrice) : undefined,
            }));
        }

        onUpdate(JSON.stringify(output));
    };

    // 暴露校验方法给父组件
    useEffect(() => {
        if (onValidate) {
            // 这是一个hack，通过闭包暴露校验函数
            (window as any).__validatePriceInfo = () => {
                if (priceInfo.mode === 'tiered') {
                    return validateTiers(priceInfo.tiers || []);
                }
                return null;
            };
        }
    }, [priceInfo, onValidate]);

    return (
        <div className="space-y-4 bg-white bg-opacity-50 p-4 rounded-lg">
            <div className="flex items-center justify-between">
                <Label className="text-sm font-medium text-gray-700">价格信息</Label>
            </div>

            {validationError && (
                <Alert variant="destructive">
                    <AlertCircle className="h-4 w-4"/>
                    <AlertDescription>{validationError}</AlertDescription>
                </Alert>
            )}

            <div className="space-y-4">
                {supportsTieredPricing && (
                    <div className="space-y-2">
                        <Label>定价模式</Label>
                        <RadioGroup
                            value={priceInfo.mode}
                            onValueChange={(v) => handleModeChange(v as 'fixed' | 'tiered')}
                        >
                            <div className="flex items-center space-x-2">
                                <RadioGroupItem value="fixed" id="fixed"/>
                                <Label htmlFor="fixed" className="font-normal cursor-pointer">固定单价</Label>
                            </div>
                            <div className="flex items-center space-x-2">
                                <RadioGroupItem value="tiered" id="tiered"/>
                                <Label htmlFor="tiered" className="font-normal cursor-pointer">区间定价</Label>
                            </div>
                        </RadioGroup>
                    </div>
                )}

                {(priceInfo.mode === 'fixed' || !supportsTieredPricing) ? (
                    <Card className="p-4 space-y-4">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>输入Token单价（分/千token）*</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.input || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, input: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="0.0000"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>输出Token单价（分/千token）*</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.output || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, output: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="0.0000"
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>图片输入Token单价（可选）</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.imageInput || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, imageInput: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="图片输入token单价"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>图片输出Token单价（可选）</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.imageOutput || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, imageOutput: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="图片输出token单价"
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>命中缓存Token单价（可选）</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.cachedRead || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, cachedRead: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="命中缓存token单价"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>创建缓存Token单价（可选）</Label>
                                <Input
                                    type="number"
                                    min="0"
                                    step="0.0001"
                                    value={priceInfo.cachedCreation || ''}
                                    onChange={(e) => {
                                        const newInfo = {...priceInfo, cachedCreation: e.target.value};
                                        setPriceInfo(newInfo);
                                        updateOutput(newInfo);
                                    }}
                                    placeholder="创建缓存token单价"
                                />
                            </div>
                        </div>
                    </Card>
                ) : (
                    <div className="space-y-4">
                        {(priceInfo.tiers || []).map((tier, index) => (
                            <Card key={index} className="p-4 space-y-4">
                                <div className="flex items-center justify-between">
                                    <h4 className="font-medium">区间 {index + 1}</h4>
                                    {index > 0 && (
                                        <Button
                                            onClick={() => removeTier(index)}
                                            size="sm"
                                            variant="ghost"
                                            className="text-red-500 hover:text-red-700"
                                        >
                                            <Trash2 className="h-4 w-4"/>
                                        </Button>
                                    )}
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label>最小输入Token</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            value={tier.minInputTokens}
                                            disabled={index === 0}
                                            onChange={(e) => updateTier(index, 'minInputTokens', parseInt(e.target.value) || 0)}
                                            className={index === 0 ? 'bg-gray-100' : ''}
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>最大输入Token</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            value={
                                                index === (priceInfo.tiers?.length || 0) - 1
                                                    ? '' // 最后一个区间不显示数字
                                                    : tier.maxInputTokens || ''
                                            }
                                            disabled={index === (priceInfo.tiers?.length || 0) - 1} // 最后一个区间禁用
                                            onChange={(e) => updateTier(index, 'maxInputTokens', e.target.value ? parseInt(e.target.value) : null)}
                                            placeholder={
                                                index === (priceInfo.tiers?.length || 0) - 1
                                                    ? "∞" // 最后一个区间显示无穷
                                                    : "上限值"
                                            }
                                            className={index === (priceInfo.tiers?.length || 0) - 1 ? 'bg-gray-100' : ''}
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label>最小输出Token（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            value={tier.minOutputTokens ?? ''}
                                            disabled={index === 0 && (tier.minOutputTokens != null || tier.maxOutputTokens != null)}
                                            onChange={(e) => {
                                                const val = e.target.value ? parseInt(e.target.value) : null;
                                                // 如果是第一个区间且正在启用输出token限制，强制设为0
                                                updateTier(index, 'minOutputTokens', index === 0 && val != null ? 0 : val);
                                            }}
                                            placeholder="不限"
                                            className={index === 0 && (tier.minOutputTokens != null || tier.maxOutputTokens != null) ? 'bg-gray-100' : ''}
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>最大输出Token（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            value={
                                                index === (priceInfo.tiers?.length || 0) - 1 && tier.minOutputTokens != null
                                                    ? '' // 最后一个区间且定义了输出区间，不显示数字
                                                    : tier.maxOutputTokens ?? ''
                                            }
                                            disabled={index === (priceInfo.tiers?.length || 0) - 1 && tier.minOutputTokens != null} // 最后一个区间且有输出限制时禁用
                                            onChange={(e) => updateTier(index, 'maxOutputTokens', e.target.value ? parseInt(e.target.value) : null)}
                                            placeholder={
                                                index === (priceInfo.tiers?.length || 0) - 1 && tier.minOutputTokens != null
                                                    ? "∞" // 最后一个区间显示无穷
                                                    : "不填表示∞"
                                            }
                                            className={index === (priceInfo.tiers?.length || 0) - 1 && tier.minOutputTokens != null ? 'bg-gray-100' : ''}
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label>输入Token单价（分/千token）*</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.inputPrice}
                                            onChange={(e) => updateTier(index, 'inputPrice', e.target.value)}
                                            placeholder="0.0000"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>输出Token单价（分/千token）*</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.outputPrice}
                                            onChange={(e) => updateTier(index, 'outputPrice', e.target.value)}
                                            placeholder="0.0000"
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label>图片输入Token单价（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.imageInputPrice || ''}
                                            onChange={(e) => updateTier(index, 'imageInputPrice', e.target.value)}
                                            placeholder="图片输入token单价"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>图片输出Token单价（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.imageOutputPrice || ''}
                                            onChange={(e) => updateTier(index, 'imageOutputPrice', e.target.value)}
                                            placeholder="图片输出token单价"
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label>命中缓存Token单价（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.cachedReadPrice || ''}
                                            onChange={(e) => updateTier(index, 'cachedReadPrice', e.target.value)}
                                            placeholder="命中缓存token单价"
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <Label>创建缓存Token单价（可选）</Label>
                                        <Input
                                            type="number"
                                            min="0"
                                            step="0.0001"
                                            value={tier.cachedCreationPrice || ''}
                                            onChange={(e) => updateTier(index, 'cachedCreationPrice', e.target.value)}
                                            placeholder="创建缓存token单价"
                                        />
                                    </div>
                                </div>
                            </Card>
                        ))}

                        <Button
                            onClick={addTier}
                            variant="outline"
                            className="w-full border-2 border-dashed hover:bg-accent"
                            type="button"
                        >
                            <Plus className="h-4 w-4 mr-2"/>
                            添加区间
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
}
