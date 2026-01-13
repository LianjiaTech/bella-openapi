'use client';

import React, {useState, useEffect} from 'react';
import {Dialog, DialogHeader, DialogTitle, DialogOverlay, DialogPortal} from '@/components/ui/dialog';
import {Model, PriceTier, PricingMode} from '@/lib/types/openapi';
import {formatTokenRange} from '@/components/meta/price-display';
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

const TOKEN_PER_WORD = 1.5;

// 检查值是否在区间内
function containsValue(min: number | null | undefined, max: number | null | undefined, value: number): boolean {
  const minValue = min ?? 0;
  // 左开右闭 (min, max]，但第一个区间从 0 开始，包含 0
  const afterMin = (minValue === 0) ? value >= 0 : value > minValue;
  const beforeMax = max === null || max === undefined || value <= max;
  return afterMin && beforeMax;
}

// 根据tokens找到对应的价格区间
function findMatchingTier(tiers: PriceTier[], inputTokens: number, outputTokens: number): PriceTier | null {
  for (const tier of tiers) {
    const inputMatch = containsValue(tier.minInputTokens, tier.maxInputTokens, inputTokens);

    // 如果没有定义输出token范围，只需匹配输入
    if (tier.minOutputTokens === null || tier.minOutputTokens === undefined) {
      if (inputMatch) return tier;
      continue;
    }

    const outputMatch = containsValue(tier.minOutputTokens, tier.maxOutputTokens, outputTokens);

    if (inputMatch && outputMatch) {
      return tier;
    }
  }
  return null;
}

export function CostCalculatorModal({
                                      isOpen,
                                      onClose,
                                      model,
                                      currentSessionTokens,
                                    }: CostCalculatorModalProps) {
  // 单次请求参数
  const [avgInputTokensPerRequest, setAvgInputTokensPerRequest] = useState<number>(1000);
  const [avgOutputTokensPerRequest, setAvgOutputTokensPerRequest] = useState<number>(500);
  const [dailyRequestCount, setDailyRequestCount] = useState<number>(100);

  // 价格信息
  const [pricingMode, setPricingMode] = useState<PricingMode>(PricingMode.FIXED);
  const [fixedPrices, setFixedPrices] = useState({
    input: 0,
    output: 0,
    cachedRead: 0,
    cachedCreation: 0,
  });
  const [priceTiers, setPriceTiers] = useState<PriceTier[]>([]);
  const [priceUnit, setPriceUnit] = useState<string>('分/千token');
  const [hasCachePrice, setHasCachePrice] = useState<boolean>(false);

  useEffect(() => {
    if (model?.priceDetails?.priceInfo) {
      const priceInfo = model.priceDetails.priceInfo;
      const mode = priceInfo.mode || PricingMode.FIXED;
      setPricingMode(mode);

      const unit = priceInfo.unit || model.priceDetails.unit || '分/千token';
      setPriceUnit(unit);
      setHasCachePrice(!!(priceInfo.cachedRead || priceInfo.cachedCreation));

      if (mode === PricingMode.FIXED) {
        setFixedPrices({
          input: priceInfo.input || 0,
          output: priceInfo.output || 0,
          cachedRead: priceInfo.cachedRead || 0,
          cachedCreation: priceInfo.cachedCreation || 0,
        });
      } else if (mode === PricingMode.TIERED && priceInfo.tiers) {
        setPriceTiers(priceInfo.tiers);
      }
    }
  }, [model]);

  // 计算当前会话费用
  const calculateCurrentSessionCost = () => {
    const inputTokens = currentSessionTokens.inputTokens;
    const outputTokens = currentSessionTokens.outputTokens;
    const cacheReadTokens = currentSessionTokens.cacheReadTokens || 0;
    const cacheCreationTokens = currentSessionTokens.cacheCreationTokens || 0;

    let inputCost = 0;
    let outputCost = 0;
    let cacheReadCost = 0;
    let cacheCreationCost = 0;
    let tierInfo = '';

    if (pricingMode === PricingMode.FIXED) {
      inputCost = (inputTokens / 1000) * fixedPrices.input;
      outputCost = (outputTokens / 1000) * fixedPrices.output;
      cacheReadCost = (cacheReadTokens / 1000) * fixedPrices.cachedRead;
      cacheCreationCost = (cacheCreationTokens / 1000) * fixedPrices.cachedCreation;
    } else if (pricingMode === PricingMode.TIERED) {
      const matchedTier = findMatchingTier(priceTiers, inputTokens, outputTokens);
      if (matchedTier) {
        inputCost = (inputTokens / 1000) * matchedTier.inputPrice;
        outputCost = (outputTokens / 1000) * matchedTier.outputPrice;
        cacheReadCost = (cacheReadTokens / 1000) * (matchedTier.cachedReadPrice || 0);
        cacheCreationCost = (cacheCreationTokens / 1000) * (matchedTier.cachedCreationPrice || 0);

        tierInfo = `价格区间: 输入 ${formatTokenRange(matchedTier.minInputTokens, matchedTier.maxInputTokens)} tokens`;
        if (matchedTier.minOutputTokens !== null && matchedTier.minOutputTokens !== undefined) {
          tierInfo += `, 输出 ${formatTokenRange(matchedTier.minOutputTokens, matchedTier.maxOutputTokens)} tokens`;
        }
      } else {
        tierInfo = '未找到匹配的价格区间';
      }
    }

    return {
      inputCost,
      outputCost,
      cacheReadCost,
      cacheCreationCost,
      totalCost: inputCost + outputCost + cacheReadCost + cacheCreationCost,
      tierInfo
    };
  };

  // 计算预估费用（基于单次请求和每日请求量）
  const calculateEstimatedCost = () => {
    const singleInputTokens = avgInputTokensPerRequest;
    const singleOutputTokens = avgOutputTokensPerRequest;

    let inputPricePerK = 0;
    let outputPricePerK = 0;
    let tierInfo = '';

    if (pricingMode === PricingMode.FIXED) {
      inputPricePerK = fixedPrices.input;
      outputPricePerK = fixedPrices.output;
    } else if (pricingMode === PricingMode.TIERED) {
      const matchedTier = findMatchingTier(priceTiers, singleInputTokens, singleOutputTokens);
      if (matchedTier) {
        inputPricePerK = matchedTier.inputPrice;
        outputPricePerK = matchedTier.outputPrice;

        tierInfo = `价格区间: 输入 ${formatTokenRange(matchedTier.minInputTokens, matchedTier.maxInputTokens)} tokens`;
        if (matchedTier.minOutputTokens !== null && matchedTier.minOutputTokens !== undefined) {
          tierInfo += `, 输出 ${formatTokenRange(matchedTier.minOutputTokens, matchedTier.maxOutputTokens)} tokens`;
        }
      } else {
        tierInfo = '未找到匹配的价格区间';
      }
    }

    // 单次请求费用（分）
    const singleInputCostFen = (singleInputTokens / 1000) * inputPricePerK;
    const singleOutputCostFen = (singleOutputTokens / 1000) * outputPricePerK;
    const singleTotalCostFen = singleInputCostFen + singleOutputCostFen;

    // 每日费用（元）
    const dailyInputCost = (singleInputCostFen * dailyRequestCount) / 100;
    const dailyOutputCost = (singleOutputCostFen * dailyRequestCount) / 100;
    const dailyTotalCost = dailyInputCost + dailyOutputCost;

    // 每月费用（元）
    const monthlyTotalCost = dailyTotalCost * 30;

    // 成本占比
    let inputPercent = 0;
    let outputPercent = 0;
    if (dailyTotalCost > 0) {
      inputPercent = (dailyInputCost / dailyTotalCost) * 100;
      outputPercent = (dailyOutputCost / dailyTotalCost) * 100;
    }

    // 每日总token数
    const dailyInputTokens = singleInputTokens * dailyRequestCount;
    const dailyOutputTokens = singleOutputTokens * dailyRequestCount;

    return {
      singleInputCostFen: singleInputCostFen / 100, // 转为元
      singleOutputCostFen: singleOutputCostFen / 100,
      singleTotalCostFen: singleTotalCostFen / 100,
      dailyInputCost,
      dailyOutputCost,
      dailyTotalCost,
      monthlyTotalCost,
      inputPercent,
      outputPercent,
      dailyInputTokens,
      dailyOutputTokens,
      tierInfo
    };
  };

  const formatCurrency = (value: number, decimals: number = 2, forceYuan: boolean = false): string => {
    const isFen = priceUnit?.includes('分');
    const symbol = (forceYuan || !isFen) ? '元' : '分';
    return `${value.toFixed(decimals)}${symbol}`;
  };

  const formatPrice = (price: number): string => {
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
              className="fixed left-[50%] top-[50%] z-50 w-full max-w-4xl translate-x-[-50%] translate-y-[-50%] border bg-white shadow-lg duration-200 sm:rounded-lg max-h-[90vh] flex flex-col overflow-hidden">
            {/* 固定的标题栏 */}
            <div className="flex-shrink-0 border-b border-gray-200 px-6 py-4">
              <DialogHeader>
                <DialogTitle className="text-xl font-bold">费用计算器</DialogTitle>
              </DialogHeader>
            </div>

            {/* 可滚动的内容区域 */}
            <div className="overflow-y-auto flex-1 px-6 py-4 space-y-6">
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

                    {/* 显示匹配的价格区间 */}
                    {pricingMode === PricingMode.TIERED && sessionCost.tierInfo && (
                        <div className="mt-2 pt-2 border-t border-blue-200">
                          <p className="text-xs text-gray-600">{sessionCost.tierInfo}</p>
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
                  <p className="text-xs text-gray-500">
                    定价模式: {pricingMode === PricingMode.FIXED ? '固定价格' : '阶梯价格'}
                  </p>

                  <div className="mt-2 space-y-2">
                    {pricingMode === PricingMode.FIXED ? (
                        // 固定价格显示
                        <>
                          <div className="flex justify-between text-sm">
                            <span className="text-gray-600">输入价格:</span>
                            <span className="font-bold text-blue-600">
                          {formatPrice(fixedPrices.input)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                        </span>
                          </div>
                          <div className="flex justify-between text-sm">
                            <span className="text-gray-600">输出价格:</span>
                            <span className="font-bold text-red-600">
                          {formatPrice(fixedPrices.output)} {priceUnit?.includes('分') ? '分' : '元'}/1K Tokens
                        </span>
                          </div>
                          {hasCachePrice && (
                              <>
                                <div className="flex justify-between text-sm">
                                  <span className="text-gray-600">缓存读取:</span>
                                  <span className="font-bold text-green-600">
                              {formatPrice(fixedPrices.cachedRead)} {priceUnit?.includes('分') ? '分' : '元'}/1K
                            </span>
                                </div>
                                <div className="flex justify-between text-sm">
                                  <span className="text-gray-600">缓存创建:</span>
                                  <span className="font-bold text-purple-600">
                              {formatPrice(fixedPrices.cachedCreation)} {priceUnit?.includes('分') ? '分' : '元'}/1K
                            </span>
                                </div>
                              </>
                          )}
                        </>
                    ) : (
                        // 阶梯价格显示
                        <div className="space-y-2 max-h-48 overflow-y-auto">
                          {priceTiers.map((tier, index) => (
                              <div key={index} className="p-2 bg-gray-50 rounded text-xs space-y-1">
                                <div className="font-semibold text-gray-700">
                                  区间 {index + 1}:
                                </div>
                                <div className="text-gray-600">
                                  输入: {formatTokenRange(tier.minInputTokens, tier.maxInputTokens)} tokens
                                </div>
                                {(tier.minOutputTokens !== null && tier.minOutputTokens !== undefined) && (
                                    <div className="text-gray-600">
                                      输出: {formatTokenRange(tier.minOutputTokens, tier.maxOutputTokens)} tokens
                                    </div>
                                )}
                                <div className="flex justify-between pt-1 border-t border-gray-200">
                            <span className="text-blue-600">
                              入: {formatPrice(tier.inputPrice)}{priceUnit?.includes('分') ? '分' : '元'}/1K
                            </span>
                                  <span className="text-red-600">
                              出: {formatPrice(tier.outputPrice)}{priceUnit?.includes('分') ? '分' : '元'}/1K
                            </span>
                                </div>
                              </div>
                          ))}
                        </div>
                    )}
                  </div>
                </div>
              </div>

              {/* 第二行：自定义输入输出和费用预估 */}
              <div className="space-y-6">
                {/* 自定义单次请求参数 */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-800 text-sm">自定义使用量</h3>
                    <p className="text-xs text-gray-500">
                      映射参考: 中文 1字≈1.5token, 英文 4字符≈1token
                    </p>
                  </div>

                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    {/* 平均输入 tokens (单次) */}
                    <div className="space-y-2">
                      <p className="text-sm text-gray-600">平均输入 Tokens (单次):</p>
                      <div className="flex items-center gap-2">
                        <input
                            type="number"
                            value={avgInputTokensPerRequest}
                            onChange={(e) => setAvgInputTokensPerRequest(parseFloat(e.target.value) || 0)}
                            min="0"
                            step="100"
                            className="flex-1 p-2 border border-gray-300 rounded-md text-right text-lg font-bold pr-1"
                        />
                        <span
                            className="text-sm font-medium text-gray-800 whitespace-nowrap">tokens</span>
                      </div>
                    </div>

                    {/* 平均输出 tokens (单次) */}
                    <div className="space-y-2">
                      <p className="text-sm text-gray-600">平均输出 Tokens (单次):</p>
                      <div className="flex items-center gap-2">
                        <input
                            type="number"
                            value={avgOutputTokensPerRequest}
                            onChange={(e) => setAvgOutputTokensPerRequest(parseFloat(e.target.value) || 0)}
                            min="0"
                            step="100"
                            className="flex-1 p-2 border border-gray-300 rounded-md text-right text-lg font-bold pr-1"
                        />
                        <span
                            className="text-sm font-medium text-gray-800 whitespace-nowrap">tokens</span>
                      </div>
                    </div>

                    {/* 每日平均请求量 */}
                    <div className="space-y-2">
                      <p className="text-sm text-gray-600">每日平均请求量:</p>
                      <div className="flex items-center gap-2">
                        <input
                            type="number"
                            value={dailyRequestCount}
                            onChange={(e) => setDailyRequestCount(parseFloat(e.target.value) || 0)}
                            min="0"
                            step="10"
                            className="flex-1 p-2 border border-gray-300 rounded-md text-right text-lg font-bold pr-1"
                        />
                        <span
                            className="text-sm font-medium text-gray-800 whitespace-nowrap">次</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 费用预估 */}
                <div
                    className="p-4 bg-gradient-to-br from-emerald-50 to-green-50 border-2 border-emerald-200 rounded-lg space-y-4">
                  <h3 className="font-semibold text-gray-800 text-sm">费用预估 (单位: 元)</h3>

                  {/* 显示匹配的价格区间 */}
                  {pricingMode === PricingMode.TIERED && estimatedCost.tierInfo && (
                      <div
                          className="p-2 bg-white/50 rounded text-xs text-gray-700 border border-emerald-300">
                        <span className="font-semibold">匹配区间: </span>
                        {estimatedCost.tierInfo}
                      </div>
                  )}

                  {/* 单次请求费用 */}
                  <div>
                    <p className="text-sm text-gray-500 mb-1">单次请求费用</p>
                    <p className="text-xl font-bold text-gray-700">
                      {formatCurrency(estimatedCost.singleTotalCostFen, 6, true)}
                    </p>
                    <div className="text-xs text-gray-500 mt-1">
                      输入: {formatCurrency(estimatedCost.singleInputCostFen, 6, true)} |
                      输出: {formatCurrency(estimatedCost.singleOutputCostFen, 6, true)}
                    </div>
                  </div>

                  {/* 日均费用 */}
                  <div>
                    <p className="text-sm text-gray-500 mb-1">
                      日均费用 ({dailyRequestCount} 次请求)
                    </p>
                    <p className="text-3xl font-extrabold text-emerald-600">
                      {formatCurrency(estimatedCost.dailyTotalCost, 4, true)}
                    </p>
                    <div className="text-xs text-gray-500 mt-1">
                      每日总计: {formatNumber(estimatedCost.dailyInputTokens + estimatedCost.dailyOutputTokens)} tokens
                    </div>
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

                  {/* 月均费用 */}
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
                  {pricingMode === PricingMode.TIERED && '阶梯价格会根据单次请求的token数量自动匹配对应区间。'}
                </p>
              </div>
            </div>

            {/* 关闭按钮 */}
            <DialogPrimitive.Close
                className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none">
              <Cross2Icon className="h-4 w-4"/>
              <span className="sr-only">Close</span>
            </DialogPrimitive.Close>
          </DialogPrimitive.Content>
        </DialogPortal>
      </Dialog>
  );
}
