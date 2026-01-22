package com.ke.bella.openapi.protocol.completion;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableSortedMap;
import com.ke.bella.openapi.protocol.IProtocolProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesPriceInfo implements IProtocolProperty {

    /**
     * 价格单位：分/千token
     */
    private String unit = "分/千token";

    /**
     * 阶梯价格列表（用于token计费）
     * 支持根据输入输出token数量匹配不同的价格区间
     */
    private List<Tier> tiers;

    /**
     * 工具使用价格配置（分/次）
     * 用于基于工具调用的计费，如 Doubao 的 web_search
     * Key: 工具名称（如 "web_search", "code_interpreter"）
     * Value: 单价（分/次）
     * 示例: {"web_search": 0.5, "code_interpreter": 1.0}
     */
    private Map<String, BigDecimal> toolPrices;

    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of(
                "tiers", "阶梯价格列表(用于token计费,单位:分/千token)",
                "toolPrices", "工具调用价格配置(Map<工具名,单价(分/次)>,可选)");
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tier {
        private RangePrice inputRangePrice;
        private List<RangePrice> outputRangePrices;

        public boolean validateTier() {
            if(inputRangePrice == null || !inputRangePrice.validateRangePrice()) {
                return false;
            }

            if(outputRangePrices != null) {
                if(outputRangePrices.isEmpty()) {
                    return false;
                }
                return outputRangePrices.stream().allMatch(RangePrice::validateRangePrice);
            }
            return true;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RangePrice {
        private int minToken;
        private int maxToken;
        private BigDecimal input;
        private BigDecimal output;
        private BigDecimal cachedInput;
        private BigDecimal reasoningOutput;

        public boolean match(int token) {
            if(token == 0 && minToken == 0) {
                return true;
            }
            return token > minToken && token <= maxToken;
        }

        public boolean validateRangePrice() {
            if(minToken < 0 || maxToken < 0 || minToken >= maxToken) {
                return false;
            }

            if(input == null || input.compareTo(BigDecimal.ZERO) <= 0 || output == null || output.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            if(cachedInput != null && cachedInput.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            if(reasoningOutput != null && reasoningOutput.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            return true;
        }
    }

    public RangePrice matchRangePrice(int inputToken, int outputToken) {
        if(tiers == null || tiers.isEmpty()) {
            throw new IllegalStateException("tiers列表为空，无法匹配价格区间");
        }

        for (Tier tier : tiers) {
            RangePrice inputRange = tier.getInputRangePrice();
            if(!inputRange.match(inputToken)) {
                continue;
            }

            List<RangePrice> outputRanges = tier.getOutputRangePrices();
            if(outputRanges == null || outputRanges.isEmpty()) {
                return inputRange;
            }

            for (RangePrice outputRange : outputRanges) {
                if(outputRange.match(outputToken)) {
                    return outputRange;
                }
            }
        }

        throw new IllegalStateException("未匹配到任何价格区间，inputToken=" + inputToken + ", outputToken=" + outputToken);
    }

    public boolean validate() {
        if(tiers == null || tiers.isEmpty()) {
            return false;
        }

        if(!tiers.stream().allMatch(Tier::validateTier)) {
            return false;
        }

        List<RangePrice> inputRanges = tiers.stream().map(Tier::getInputRangePrice).collect(Collectors.toList());
        if(!isValidCoverage(inputRanges)) {
            return false;
        }

        return tiers.stream().filter(tier -> tier.getOutputRangePrices() != null && !tier.getOutputRangePrices().isEmpty())
                .allMatch(tier -> isValidCoverage(tier.getOutputRangePrices()));
    }

    private boolean isValidCoverage(List<RangePrice> intervals) {
        if(intervals == null || intervals.isEmpty()) {
            return false;
        }

        intervals.sort(Comparator.comparingInt(RangePrice::getMinToken));

        if(intervals.get(0).getMinToken() != 0) {
            return false;
        }

        int expectedStart = 0;
        for (RangePrice interval : intervals) {
            if(interval.getMinToken() != expectedStart) {
                return false;
            }
            expectedStart = interval.getMaxToken();
        }

        return expectedStart == Integer.MAX_VALUE;
    }
}
