'use client';

import React, { useState, useEffect } from 'react';
import { Dialog, DialogHeader, DialogTitle, DialogOverlay, DialogPortal } from '@/components/ui/dialog';
import { Model } from '@/lib/types/openapi';
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Cross2Icon } from "@radix-ui/react-icons";
import { RenderAllTiersTable } from '@/components/meta/price-display';
import { matchRangePrice, calculateCost } from '@/lib/utils/price-matcher';

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

export function CostCalculatorModal({
  isOpen,
  onClose,
  model,
  currentSessionTokens,
}: CostCalculatorModalProps) {
  // 自定义使用量状态
  const [avgInputTokens, setAvgInputTokens] = useState<number>(1000); // 平均输入tokens（单次）
  const [avgOutputTokens, setAvgOutputTokens] = useState<number>(500); // 平均输出tokens（单次）
  const [dailyRequestCount, setDailyRequestCount] = useState<number>(100); // 每日请求次数

  const [priceUnit, setPriceUnit] = useState<string>('分/千token');

  useEffect(() => {
    if (model?.priceDetails?.unit) {
      setPriceUnit(model.priceDetails.unit);
    }
  }, [model]);

  // 计算当前会话的匹配结果和费用
  const calculateSessionCost = () => {
    const tiers = model?.priceDetails?.priceInfo?.tiers;
    const matchResult = matchRangePrice(
        tiers,
        currentSessionTokens.inputTokens,
        currentSessionTokens.outputTokens
    );

    if (!matchResult.rangePrice) {
      return {
        matchResult,
        inputCost: 0,
        outputCost: 0,
        cacheReadCost: 0,
        cacheCreationCost: 0,
        totalCost: 0,
        inputPricePerK: 0,
        outputPricePerK: 0,
        cachedReadPricePerK: 0,
        cachedCreationPricePerK: 0,
      };
    }

    const rangePrice = matchResult.rangePrice;
    const inputPricePerK = Number(rangePrice.input) || 0;
    const outputPricePerK = Number(rangePrice.output) || 0;
    const cachedReadPricePerK = Number(rangePrice.cachedRead) || 0;
    const cachedCreationPricePerK = Number(rangePrice.cachedCreation) || 0;

    const inputCost = calculateCost(currentSessionTokens.inputTokens, inputPricePerK);
    const outputCost = calculateCost(currentSessionTokens.outputTokens, outputPricePerK);
    const cacheReadCost = calculateCost(currentSessionTokens.cacheReadTokens || 0, cachedReadPricePerK);
    const cacheCreationCost = calculateCost(currentSessionTokens.cacheCreationTokens || 0, cachedCreationPricePerK);
    const totalCost = inputCost + outputCost + cacheReadCost + cacheCreationCost;

    return {
      matchResult,
      inputCost,
      outputCost,
      cacheReadCost,
      cacheCreationCost,
      totalCost,
      inputPricePerK,
      outputPricePerK,
      cachedReadPricePerK,
      cachedCreationPricePerK,
    };
  };

  // 计算预估费用（基于平均单次tokens和每日请求次数）
  const calculateEstimatedCost = () => {
    const tiers = model?.priceDetails?.priceInfo?.tiers;
    const matchResult = matchRangePrice(tiers, avgInputTokens, avgOutputTokens);

    if (!matchResult.rangePrice) {
      return {
        matchResult,
        dailyInputCost: 0,
        dailyOutputCost: 0,
        dailyTotalCost: 0,
        monthlyTotalCost: 0,
        inputPercent: 0,
        outputPercent: 0,
        totalInputTokens: 0,
        totalOutputTokens: 0,
        inputPricePerK: 0,
        outputPricePerK: 0,
      };
    }

    const rangePrice = matchResult.rangePrice;
    const inputPricePerK = Number(rangePrice.input) || 0;
    const outputPricePerK = Number(rangePrice.output) || 0;

    // 每日总tokens = 单次tokens * 每日请求次数
    const totalInputTokens = avgInputTokens * dailyRequestCount;
    const totalOutputTokens = avgOutputTokens * dailyRequestCount;

    // 计算费用（单位：分）
    const dailyInputCostFen = calculateCost(totalInputTokens, inputPricePerK);
    const dailyOutputCostFen = calculateCost(totalOutputTokens, outputPricePerK);

    // 转换为元
    const dailyInputCost = dailyInputCostFen / 100;
    const dailyOutputCost = dailyOutputCostFen / 100;
    const dailyTotalCost = dailyInputCost + dailyOutputCost;
    const monthlyTotalCost = dailyTotalCost * 30;

    // 计算成本占比
    let inputPercent = 0;
    let outputPercent = 0;
    if (dailyTotalCost > 0) {
      inputPercent = (dailyInputCost / dailyTotalCost) * 100;
      outputPercent = (dailyOutputCost / dailyTotalCost) * 100;
    }

    return {
      matchResult,
      dailyInputCost,
      dailyOutputCost,
      dailyTotalCost,
      monthlyTotalCost,
      inputPercent,
      outputPercent,
      totalInputTokens,
      totalOutputTokens,
      inputPricePerK,
      outputPricePerK,
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

  const sessionCost = calculateSessionCost();
  const estimatedCost = calculateEstimatedCost();
  const hasCachePrice = !!(sessionCost.cachedReadPricePerK || sessionCost.cachedCreationPricePerK);

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogPortal>
        <DialogOverlay className="bg-black/5" />
        <DialogPrimitive.Content className="fixed left-[50%] top-[50%] z-50 w-full max-w-5xl translate-x-[-50%] translate-y-[-50%] border bg-white shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] sm:rounded-lg max-h-[90vh] flex flex-col overflow-hidden">
        {/* 固定的标题栏 */}
        <div className="flex-shrink-0 border-b border-gray-200 px-6 py-4">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">费用计算器</DialogTitle>
          </DialogHeader>
        </div>

        {/* 可滚动的内容区域 */}
        <div className="overflow-y-auto flex-1 px-6 py-4 space-y-6 scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-gray-100">
          {/* 第一行：当前会话统计 和 模型信息 */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* 当前会话统计 */}
            <div className="p-4 bg-gradient-to-br from-blue-50 to-indigo-50 border-2 border-blue-200 rounded-lg space-y-3">
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

                {/* 匹配的价格区间 */}
                {sessionCost.matchResult.rangePrice && (
                    <div className="mt-3 p-2 bg-white/60 rounded border border-blue-200">
                      <div className="text-xs space-y-0.5">
                        <div className="text-gray-700 flex flex-wrap items-center gap-x-4">
                          <span className="font-medium text-gray-600">匹配区间:</span>
                          <span><span className="font-medium">输入范围:</span> {sessionCost.matchResult.inputRange} tokens</span>
                          {sessionCost.matchResult.outputRange && (
                              <span><span className="font-medium">输出范围:</span> {sessionCost.matchResult.outputRange} tokens</span>
                          )}
                        </div>
                        <div className="grid grid-cols-2 gap-2 mt-2 pt-2 border-t border-blue-200">
                          <div className="text-gray-700">
                            输入价格: <span className="font-semibold">{formatPrice(sessionCost.inputPricePerK)}</span> {priceUnit}
                          </div>
                          <div className="text-gray-700">
                            输出价格: <span className="font-semibold">{formatPrice(sessionCost.outputPricePerK)}</span> {priceUnit}
                          </div>
                        </div>
                      </div>
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
                      <span>{formatCurrency(sessionCost.inputCost, 6)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>输出成本:</span>
                      <span>{formatCurrency(sessionCost.outputCost, 6)}</span>
                    </div>
                    {hasCachePrice && (currentSessionTokens.cacheReadTokens || 0) > 0 && (
                      <div className="flex justify-between">
                        <span>缓存读取成本:</span>
                        <span>{formatCurrency(sessionCost.cacheReadCost || 0, 6)}</span>
                      </div>
                    )}
                    {hasCachePrice && (currentSessionTokens.cacheCreationTokens || 0) > 0 && (
                      <div className="flex justify-between">
                        <span>缓存创建成本:</span>
                        <span>{formatCurrency(sessionCost.cacheCreationCost || 0, 6)}</span>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* 模型信息 */}
            <div className="p-4 border border-gray-200 rounded-lg space-y-2 overflow-y-auto max-h-96">
              <div className="flex justify-between items-center">
              <p className="text-sm text-gray-500">当前模型:</p>
              <p className="text-xs text-gray-500">({priceUnit})</p>
              </div>
              <p className="text-lg font-semibold text-gray-800">{model?.modelName || '--'}</p>

              {/* 区间价格展示 */}
              {model?.priceDetails?.priceInfo?.tiers &&
                  model.priceDetails.priceInfo.tiers.length > 0 && (
              <div className="mt-2 space-y-1">
                <p className="text-xs text-gray-600 mb-2">费用:</p>
                <RenderAllTiersTable
                    tiers={model.priceDetails.priceInfo.tiers.filter(
                        tier => tier.inputRangePrice
                )}
                />
              </div>
                  )}
            </div>
          </div>

          {/* 第二行：自定义使用量预估 */}
          <div className="space-y-4">
            <h3 className="font-semibold text-gray-800 text-base">费用预估</h3>

            {/* 输入参数 */}
            <div className="p-4 bg-gray-50 rounded-lg space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-medium text-gray-700">自定义使用参数</h4>
                <p className="text-xs text-gray-500">
                  参考: 中文 1字≈1.5token, 英文 4字符≈1token
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* 平均输入 tokens */}
                <div className="space-y-2">
                  <label className="text-sm text-gray-600">平均输入 Tokens (单次):</label>
                  <div className="flex items-center gap-2">
                    <input
                        type="number"
                        value={avgInputTokens}
                        onChange={(e) => setAvgInputTokens(parseFloat(e.target.value) || 0)}
                        min="0"
                        step="100"
                        className="flex-1 p-2 border border-gray-300 rounded-md text-right text-base font-semibold"
                    />
                    <span className="text-sm font-medium text-gray-700">tokens</span>
                  </div>
                </div>

                  {/* 平均输出 tokens */}
                <div className="space-y-2">
                  <label className="text-sm text-gray-600">平均输出 Tokens (单次):</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={avgOutputTokens}
                      onChange={(e) => setAvgOutputTokens(parseFloat(e.target.value) || 0)}
                      min="0"
                      step="100"
                      className="flex-1 p-2 border border-gray-300 rounded-md text-right text-base font-semibold"
                    />
                    <span className="text-sm font-medium text-gray-700">tokens</span>
                  </div>
                </div>

                {/* 每日请求次数 */}
                <div className="space-y-2">
                  <label className="text-sm text-gray-600">每日请求次数:</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      value={dailyRequestCount}
                      onChange={(e) => setDailyRequestCount(parseFloat(e.target.value) || 0)}
                      min="0"
                      step="10"
                      className="flex-1 p-2 border border-gray-300 rounded-md text-right text-base font-semibold"
                    />
                    <span className="text-sm font-medium text-gray-700">次</span>
                  </div>
                </div>
              </div>
            </div>

                {/* 匹配的价格区间和费用预估结果 */}
            <div className="p-4 bg-gradient-to-br from-emerald-50 to-green-50 border-2 border-emerald-200 rounded-lg space-y-4">
              {estimatedCost.matchResult.rangePrice ? (
                  <>
                    {/* 匹配的区间信息 */}
              <div className="p-3 bg-white/70 rounded border border-emerald-300">
                <div className="text-xs space-y-1">
                  <div className="text-gray-700 flex flex-wrap items-center gap-x-4">
                    <span className="font-medium text-gray-600">匹配区间:</span>
                    <span><span className="font-medium">输入范围:</span> {estimatedCost.matchResult.inputRange} tokens</span>
                    {estimatedCost.matchResult.outputRange && (
                        <span><span className="font-medium">输出范围:</span> {estimatedCost.matchResult.outputRange} tokens</span>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-2 mt-2 pt-2 border-t border-emerald-200">
                    <div className="text-gray-700">
                      输入价格: <span className="font-semibold">{formatPrice(estimatedCost.inputPricePerK)}</span> {priceUnit}
                    </div>
                    <div className="text-gray-700">
                      输出价格: <span className="font-semibold">{formatPrice(estimatedCost.outputPricePerK)}</span> {priceUnit}
                    </div>
                  </div>
                </div>
              </div>

              {/* 成本明细 */}
              <div className="text-sm space-y-2 bg-white/50 p-3 rounded border border-emerald-200">
                <div className="flex justify-between items-center">
                  <span className="text-gray-600">输入成本:</span>
                  <span className="font-semibold text-blue-600">
                {formatCurrency(estimatedCost.dailyInputCost, 4, true)}
              </span>
                </div>
                <div className="text-xs text-gray-500 ml-4">
                  {avgInputTokens.toLocaleString()} tokens × {dailyRequestCount} 次 × {formatPrice(estimatedCost.inputPricePerK)} {priceUnit}
                </div>
                <div className="flex justify-between items-center mt-2">
                  <span className="text-gray-600">输出成本:</span>
                  <span className="font-semibold text-red-600">
                {formatCurrency(estimatedCost.dailyOutputCost, 4, true)}
              </span>
                </div>
                <div className="text-xs text-gray-500 ml-4">
                  {avgOutputTokens.toLocaleString()} tokens × {dailyRequestCount} 次 × {formatPrice(estimatedCost.outputPricePerK)} {priceUnit}
                </div>
              </div>

              {/* 费用统计 */}
              <div>
                <p className="text-sm text-gray-600 mb-1">日均费用</p>
                <p className="text-3xl font-extrabold text-emerald-600">
                  {formatCurrency(estimatedCost.dailyTotalCost, 4, true)}
                </p>
              </div>

              {/* 成本占比进度条 */}
              <div>
                <p className="text-sm text-gray-600 mb-2">成本占比</p>
                <div className="h-3 rounded-full overflow-hidden bg-gray-200 flex">
                  <div
                    className="bg-blue-500"
                    style={{ width: `${estimatedCost.inputPercent}%` }}
                    title={`输入成本: ${formatCurrency(estimatedCost.dailyInputCost, 4, true)}`}
                  />
                  <div
                    className="bg-red-500"
                    style={{ width: `${estimatedCost.outputPercent}%` }}
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
              </>
              ) : (
                  <div className="text-center text-gray-500 py-4">
                    <p className="text-sm">未能匹配到价格区间</p>
                    <p className="text-xs mt-1">请检查输入/输出 tokens 是否在有效范围内</p>
                  </div>
              )}
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
        <DialogPrimitive.Close className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground">
          <Cross2Icon className="h-4 w-4" />
          <span className="sr-only">Close</span>
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
      </DialogPortal>
    </Dialog>
  );
}
