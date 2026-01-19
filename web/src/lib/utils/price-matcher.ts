import { Tier, RangePrice } from '@/lib/types/openapi';

/**
 * 价格匹配结果
 */
export interface PriceMatchResult {
  rangePrice: RangePrice | null;
  inputRange: string;
  outputRange?: string;
  tierIndex: number;
  outputTierIndex?: number;
}

/**
 * 匹配价格区间 - 复刻后端的 matchRangePrice 逻辑
 * @param tiers 价格区间列表
 * @param inputToken 输入token数
 * @param outputToken 输出token数
 * @returns 匹配结果
 */
export function matchRangePrice(
  tiers: Tier[] | undefined,
  inputToken: number,
  outputToken: number
): PriceMatchResult {
  const defaultResult: PriceMatchResult = {
    rangePrice: null,
    inputRange: '-',
    tierIndex: -1,
  };

  if (!tiers || tiers.length === 0) {
    return defaultResult;
  }

  for (let i = 0; i < tiers.length; i++) {
    const tier = tiers[i];
    const inputRange = tier.inputRangePrice;

    if (!inputRange) {
      continue;
    }

    // 检查输入token是否匹配
    if (!matchToken(inputToken, inputRange.minToken, inputRange.maxToken)) {
      continue;
    }

    const outputRanges = tier.outputRangePrices;

    // 如果没有输出梯度，直接返回输入区间的价格
    if (!outputRanges || outputRanges.length === 0) {
      return {
        rangePrice: inputRange,
        inputRange: formatTokenRange(inputRange.minToken, inputRange.maxToken),
        tierIndex: i,
      };
    }

    // 有输出梯度，查找匹配的输出区间
    for (let j = 0; j < outputRanges.length; j++) {
      const outputRange = outputRanges[j];
      if (matchToken(outputToken, outputRange.minToken, outputRange.maxToken)) {
        return {
          rangePrice: outputRange,
          inputRange: formatTokenRange(inputRange.minToken, inputRange.maxToken),
          outputRange: formatTokenRange(outputRange.minToken, outputRange.maxToken),
          tierIndex: i,
          outputTierIndex: j,
        };
      }
    }
  }

  return defaultResult;
}

/**
 * 判断token是否在指定区间内（前开后闭区间）
 * @param token 要匹配的token数
 * @param minToken 区间最小值
 * @param maxToken 区间最大值
 * @returns 是否匹配
 */
function matchToken(token: number, minToken: number, maxToken: number): boolean {
  // 特殊情况：token=0 且 minToken=0
  if (token === 0 && minToken === 0) {
    return true;
  }
  // 前开后闭区间：(minToken, maxToken]
  return token > minToken && token <= maxToken;
}

/**
 * 格式化token范围显示（前开后闭区间）
 * @param min 最小值
 * @param max 最大值
 * @returns 格式化的区间字符串
 */
export function formatTokenRange(min: number, max: number): string {
  if (max === 2147483647 || max === Number.MAX_SAFE_INTEGER) {
    return `(${min.toLocaleString()},+∞)`;
  }
  return `(${min.toLocaleString()},${max.toLocaleString()}]`;
}

/**
 * 计算费用
 * @param tokens token数量
 * @param pricePerK 每千token的价格
 * @returns 费用
 */
export function calculateCost(tokens: number, pricePerK: number): number {
  return (tokens / 1000) * pricePerK;
}
