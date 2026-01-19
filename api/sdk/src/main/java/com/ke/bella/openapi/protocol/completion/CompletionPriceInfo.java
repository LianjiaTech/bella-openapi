package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    private String unit = "分/千token";
    private double batchDiscount = 1.0;
    private List<Tier> tiers;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("tiers", "区间价格列表");
        return map;
    }

    @Data
    public static class Tier implements Serializable {
        private static final long serialVersionUID = 1L;
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
    public static class RangePrice implements Serializable {
        private static final long serialVersionUID = 1L;
        private int minToken;
        private int maxToken;
        private BigDecimal input;
        private BigDecimal output;
        private BigDecimal imageInput;
        private BigDecimal imageOutput;
        private BigDecimal cachedRead;
        private BigDecimal cachedCreation;

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

            // 验证可选价格字段:如果不为null,则必须>0
            return Stream.of(imageInput, imageOutput, cachedRead, cachedCreation)
                    .filter(Objects::nonNull)
                    .allMatch(price -> price.compareTo(BigDecimal.ZERO) > 0);
        }
    }

    public RangePrice matchRangePrice(int inputToken, int outputToken) {
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

        throw new IllegalStateException("未匹配到任何输入价格区间，inputToken=" + inputToken + ", outputToken=" + outputToken);
    }

    public boolean validate() {
        // tiers必须存在且不为空
        if(tiers == null || tiers.isEmpty()) {
            return false;
        }

        // 验证每个tier的结构有效性
        if(!tiers.stream().allMatch(Tier::validateTier)) {
            return false;
        }

        // 验证所有inputRangePrice的token范围覆盖性
        List<RangePrice> inputRanges = tiers.stream().map(Tier::getInputRangePrice).collect(Collectors.toList());
        if(!isValidCoverage(inputRanges)) {
            return false;
        }

        // 验证每个tier的outputRangePrices的覆盖性
        return tiers.stream().filter(tier -> tier.getOutputRangePrices() != null && !tier.getOutputRangePrices().isEmpty())
                .allMatch(tier -> isValidCoverage(tier.getOutputRangePrices()));
    }

    public boolean isValidCoverage(List<RangePrice> intervals) {
        if(intervals == null || intervals.isEmpty()) {
            return false;
        }

        // 1. 按起始位置排序
        intervals.sort(Comparator.comparingInt(RangePrice::getMinToken));

        // 2. 检查第一个区间是否从0开始
        if(intervals.get(0).getMinToken() != 0) {
            return false;
        }

        // 3. 检查连续性
        int expectedStart = 0;
        for (RangePrice interval : intervals) {
            if(interval.getMinToken() != expectedStart) {
                return false;
            }
            expectedStart = interval.getMaxToken();
        }

        // 4. 检查最后一个区间是否到达Integer.MAX_VALUE
        return expectedStart == Integer.MAX_VALUE;
    }
}
