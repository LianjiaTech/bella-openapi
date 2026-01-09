'use client';

import React, {useState, useEffect} from 'react';
import {Dialog, DialogHeader, DialogTitle, DialogOverlay, DialogPortal} from '@/components/ui/dialog';
import {Model} from '@/lib/types/openapi';
import * as DialogPrimitive from "@radix-ui/react-dialog";
import {Cross2Icon} from "@radix-ui/react-icons";

interface CostCalculatorModalProps {
    isOpen: boolean;
    onClose: () => void;
    model: Model | null;
    currentSessionTokens: {
        inputTokens: number;
        outputTokens: number;
        cacheCreationTokens?: number;
        cacheReadTokens?: number;
    };
}

const TOKEN_PER_WORD = 1.5; // 1字 ≈ 1.5 tokens (估算)

export function CostCalculatorModal({
                                        isOpen,
                                        onClose,
                                        model,
                                        currentSessionTokens,
                                    }: CostCalculatorModalProps) {
    const [customInputTokens, setCustomInputTokens] = useState<number>(100000); // tokens
    const [customOutputTokens, setCustomOutputTokens] = useState<number>(50000); // tokens

    // 解析价格信息
    const [inputPricePerK, setInputPricePerK] = useState<number>(0);
    const [outputPricePerK, setOutputPricePerK] = useState<number>(0);
    const [cachedReadPricePerK, setCachedReadPricePerK] = useState<number>(0);
    const [cachedCreationPricePerK, setCachedCreationPricePerK] = useState<number>(0);
    const [priceUnit, setPriceUnit] = useState<string>('分/千token');
    const [hasCachePrice, setHasCachePrice] = useState<boolean>(false);

    useEffect(() => {
        if (model?.priceDetails?.priceInfo) {
            const {input, output, cachedRead, cachedCreation, unit} = model.priceDetails.priceInfo;
            // 直接使用原始价格，不做转换（保持"分"为单位）
            setInputPricePerK(Number(input) || 0);
            setOutputPricePerK(Number(output) || 0);
            setCachedReadPricePerK(Number(cachedRead) || 0);
            setCachedCreationPricePerK(Number(cachedCreation) || 0);
            setPriceUnit(unit || '分/千token');
            setHasCachePrice(!!(cachedRead || cachedCreation));
        }
    }, [model]);

    // 计算当前会话费用
    const calculateCurrentSessionCost = () => {
        const inputCost = (currentSessionTokens.inputTokens / 1000) * inputPricePerK;
        const outputCost = (currentSessionTokens.outputTokens / 1000) * outputPricePerK;
        const cacheReadCost = ((currentSessionTokens.cacheReadTokens || 0) / 1000) * cachedReadPricePerK;
        const cacheCreationCost = ((currentSessionTokens.cacheCreationTokens || 0) / 1000) * cachedCreationPricePerK;
        return {
            inputCost,
            outputCost,
            cacheReadCost,
            cacheCreationCost,
            totalCost: inputCost + outputCost + cacheReadCost + cacheCreationCost
        };
    };

    // 计算预估费用
    const calculateEstimatedCost = () => {
        // 直接使用用户输入的 token 数量
        const inputTokens = customInputTokens; // 总输入tokens
        const outputTokens = customOutputTokens; // 总输出tokens

        // 计算费用（注意：价格是"分"为单位，需要转换为"元"）
        const dailyInputCostFen = (inputTokens / 1000) * inputPricePerK; // 分
        const dailyOutputCostFen = (outputTokens / 1000) * outputPricePerK; // 分

        // 转换为元
        const dailyInputCost = dailyInputCostFen / 100; // 元
        const dailyOutputCost = dailyOutputCostFen / 100; // 元
        const dailyTotalCost = dailyInputCost + dailyOutputCost; // 元
        const monthlyTotalCost = dailyTotalCost * 30; // 元

        // 计算对应的字数（用于提示）
        const inputWords = Math.round(inputTokens / TOKEN_PER_WORD);
        const outputWords = Math.round(outputTokens / TOKEN_PER_WORD);

        // 计算成本占比
        let inputPercent = 0;
        let outputPercent = 0;
        if (dailyTotalCost > 0) {
            inputPercent = (dailyInputCost / dailyTotalCost) * 100;
            outputPercent = (dailyOutputCost / dailyTotalCost) * 100;
        }

        return {
            dailyInputCost,
            dailyOutputCost,
            dailyTotalCost,
            monthlyTotalCost,
            inputPercent,
            outputPercent,
            inputTokens,
            outputTokens,
            inputWords,
            outputWords
        };
    };

    const formatCurrency = (value: number, decimals: number = 2, forceYuan: boolean = false): string => {
        // 如果强制使用元（费用预估时），或单位本身就是元，则显示"元"
        const isFen = priceUnit?.includes('分');
        const symbol = (forceYuan || !isFen) ? '元' : '分';
        return `${value.toFixed(decimals)}${symbol}`;
    };

    const formatPrice = (price: number): string => {
        // 格式化价格显示，保留足够的精度
        return price < 1 ? price.toFixed(4) : price.toFixed(2);
    };

    const formatNumber = (value: number): string => {
        return new Intl.NumberFormat('zh-CN').format(Math.round(value));
    };

    const sessionCost = calculateCurrentSessionCost();
    const estimatedCost = calculateEstimatedCost();

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogPortal>
                <DialogOverlay className="bg-black/5"/>
                <DialogPrimitive.Content
                    className="fixed left-[50%] top-[50%] z-50 w-full max-w-4xl translate-x-[-50%] translate-y-[-50%] border bg-white shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] sm:rounded-lg max-h-[90vh] flex flex-col overflow-hidden">
                    {/* 固定的标题栏 */}
                    <div className="flex-shrink-0 border-b border-gray-200 px-6 py-4">
                        <DialogHeader>
                            <DialogTitle className="text-xl font-bold">费用计算器</DialogTitle>
                        </DialogHeader>
                    </div>

                    {/* 可滚动的内容区域 */}
                    <div
                        className="overflow-y-auto flex-1 px-6 py-4 space-y-6 scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-gray-100">
                        {/* 第一行：当前会话统计 和 模型信息 */}
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                            {/* 当前会话统计 */}
                            <div
                                className="p-4 bg-gradient-to-br from-blue-50 to-indigo-50 border-2 border-blue-200 rounded-lg space-y-3">
                                <h3 className="font-semibold text-gray-800">当前会话统计</h3>

                                <div className="space-y-2">
                                    <div className="flex justify-between items-center text-sm">
                                        <span className="text-gray-600">输入 Token:</span>
                                        <span className="font-bold text-blue-600">
                    {formatNumber(currentSessionTokens.inputTokens)}
                  </span>
                                    </div>
                                    <div className="flex justify-between items-center text-sm">
                                        <span className="text-gray-600">输出 Token:</span>
                                        <span className="font-bold text-green-600">
                    {formatNumber(currentSessionTokens.outputTokens)}
                  </span>
                                    </div>
                                    {hasCachePrice && (currentSessionTokens.cacheReadTokens || 0) > 0 && (
                                        <div className="flex justify-between items-center text-sm">
                                            <span className="text-gray-600">缓存读取 Token:</span>
                                            <span className="font-bold text-green-600">
                      {formatNumber(currentSessionTokens.cacheReadTokens || 0)}
                    </span>
                                        </div>
                                    )}
                                    {hasCachePrice && (currentSessionTokens.cacheCreationTokens || 0) > 0 && (
                                        <div className="flex justify-between items-center text-sm">
                                            <span className="text-gray-600">缓存创建 Token:</span>
                                            <span className="font-bold text-purple-600">
                      {formatNumber(currentSessionTokens.cacheCreationTokens || 0)}
                    </span>
                                        </div>
                                    )}
                                    <div className="border-t border-blue-200 pt-2 mt-2">
                                        <div className="flex justify-between items-center">
                                            <span className="text-sm font-medium text-gray-700">已使用费用:</span>
                                            <span className="text-2xl font-extrabold text-indigo-600">
                      {formatCurrency(sessionCost.totalCost, 4)}
                    </span>
                                        </div>
                                        <div className="text-xs text-gray-500 mt-1 space-y-0.5">
                                            <div className="flex justify-between">
                                                <span>输入成本:</span>
                                                <span>{formatCurrency(sessionCost.inputCost, 4)}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span>输出成本:</span>
                                                <span>{formatCurrency(sessionCost.outputCost, 4)}</span>
                                            </div>
                                            {hasCachePrice && (currentSessionTokens.cacheReadTokens || 0) > 0 && (
                                                <div className="flex justify-between">
                                                    <span>缓存读取成本:</span>
                                                    <span>{formatCurrency(sessionCost.cacheReadCost || 0, 4)}</span>
                                                </div>
                                            )}
                                            {hasCachePrice && (currentSessionTokens.cacheCreationTokens || 0) > 0 && (
                                                <div className="flex justify-between">
                                                    <span>缓存创建成本:</span>
                                                    <span>{formatCurrency(sessionCost.cacheCreationCost || 0, 4)}</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* 模型信息 */}
                            <div className="p-4 border border-gray-200 rounded-lg space-y-2">
                                <p className="text-sm text-gray-500">当前模型:</p>
                                <p className="text-lg font-semibold text-gray-800">{model?.modelName || '--'}</p>

                                <div className="mt-2 space-y-1">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-gray-600">输入价格:</span>
                                        <span className="font-bold text-blue-600">
                    {formatPrice(inputPricePerK)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                  </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-gray-600">输出价格:</span>
                                        <span className="font-bold text-red-600">
                    {formatPrice(outputPricePerK)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                  </span>
                                    </div>
                                    {hasCachePrice && (
                                        <>
                                            <div className="flex justify-between text-sm">
                                                <span className="text-gray-600">缓存读取价格:</span>
                                                <span className="font-bold text-green-600">
                        {formatPrice(cachedReadPricePerK)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                      </span>
                                            </div>
                                            <div className="flex justify-between text-sm">
                                                <span className="text-gray-600">缓存创建价格:</span>
                                                <span className="font-bold text-purple-600">
                        {formatPrice(cachedCreationPricePerK)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                      </span>
                                            </div>
                                        </>
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* 第二行：自定义输入输出和费用预估 */}
                        <div className="space-y-6">
                            {/* 自定义输入输出 tokens */}
                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <h3 className="font-semibold text-gray-800 text-sm">自定义每日使用量</h3>
                                    <p className="text-xs text-gray-500">
                                        映射参考: 中文 1字≈1.5token, 英文 4字符≈1token
                                    </p>
                                </div>

                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                                    {/* 输入 tokens */}
                                    <div className="space-y-2">
                                        <p className="text-sm text-gray-600">输入 Tokens (每日):</p>
                                        <div className="flex items-center gap-2">
                                            <input
                                                type="number"
                                                value={customInputTokens}
                                                onChange={(e) => setCustomInputTokens(parseFloat(e.target.value) || 0)}
                                                min="0"
                                                step="1000"
                                                className="flex-1 p-2 border border-gray-300 rounded-md text-right text-lg font-bold"
                                            />
                                            <span className="text-sm font-medium text-gray-800">tokens</span>
                                        </div>
                                    </div>

                                    {/* 输出 tokens */}
                                    <div className="space-y-2">
                                        <p className="text-sm text-gray-600">输出 Tokens (每日):</p>
                                        <div className="flex items-center gap-2">
                                            <input
                                                type="number"
                                                value={customOutputTokens}
                                                onChange={(e) => setCustomOutputTokens(parseFloat(e.target.value) || 0)}
                                                min="0"
                                                step="1000"
                                                className="flex-1 p-2 border border-gray-300 rounded-md text-right text-lg font-bold"
                                            />
                                            <span className="text-sm font-medium text-gray-800">tokens</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* 费用预估 */}
                            <div
                                className="p-4 bg-gradient-to-br from-emerald-50 to-green-50 border-2 border-emerald-200 rounded-lg space-y-4">
                                <h3 className="font-semibold text-gray-800 text-sm">费用预估 (单位: 元)</h3>

                                <div>
                                    <p className="text-sm text-gray-500 mb-1">日均费用</p>
                                    <p className="text-3xl font-extrabold text-emerald-600">
                                        {formatCurrency(estimatedCost.dailyTotalCost, 4, true)}
                                    </p>
                                </div>

                                {/* 成本占比进度条 */}
                                <div>
                                    <p className="text-sm text-gray-500 mb-2">成本占比</p>
                                    <div className="h-3 rounded-full overflow-hidden bg-gray-200 flex">
                                        <div
                                            className="bg-blue-500"
                                            style={{width: `${estimatedCost.inputPercent}%`}}
                                            title={`输入成本: ${formatCurrency(estimatedCost.dailyInputCost, 4, true)}`}
                                        />
                                        <div
                                            className="bg-red-500"
                                            style={{width: `${estimatedCost.outputPercent}%`}}
                                            title={`输出成本: ${formatCurrency(estimatedCost.dailyOutputCost, 4, true)}`}
                                        />
                                    </div>
                                    <div className="flex justify-between text-xs font-medium text-gray-600 mt-1">
                  <span className="text-blue-600">
                    输入: {estimatedCost.inputPercent.toFixed(1)}%
                  </span>
                                        <span className="text-red-600">
                    输出: {estimatedCost.outputPercent.toFixed(1)}%
                  </span>
                                    </div>
                                </div>

                                <div className="border-t border-emerald-200 pt-3">
                                    <div className="flex justify-between items-center">
                                        <span className="text-sm text-gray-600">月均费用 (30天):</span>
                                        <span className="text-xl font-bold text-gray-800">
                    {formatCurrency(estimatedCost.monthlyTotalCost, 2, true)}
                  </span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* 提示信息 */}
                        <div className="p-3 bg-yellow-50 border-l-4 border-yellow-400 text-yellow-800 rounded-lg">
                            <p className="text-xs">
                                <span className="font-semibold">温馨提示:</span>
                                以上费用为预估值，实际费用可能因使用情况而异。Token 统计为客户端粗略估算，实际计费以服务端返回为准。
                            </p>
                        </div>
                    </div>

                    {/* 关闭按钮 */}
                    <DialogPrimitive.Close
                        className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground">
                        <Cross2Icon className="h-4 w-4"/>
                        <span className="sr-only">Close</span>
                    </DialogPrimitive.Close>
                </DialogPrimitive.Content>
            </DialogPortal>
        </Dialog>
    );
}
