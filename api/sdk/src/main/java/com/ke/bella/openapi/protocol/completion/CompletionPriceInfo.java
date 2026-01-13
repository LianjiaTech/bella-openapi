package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal input;
    private BigDecimal output;
    private BigDecimal imageInput;
    private BigDecimal imageOutput;
    private BigDecimal cachedRead;
    private BigDecimal cachedCreation;
    private String unit = "分/千token";
    private double batchDiscount = 1.0;

    private PricingMode mode = PricingMode.FIXED;
    private List<Tier> tiers;

    public BigDecimal getCachedCreation() {
        if(cachedCreation == null && cachedRead != null && input != null) {
            return BigDecimal.valueOf(input.doubleValue() * 1.25);
        }
        return cachedCreation;
    }

    public Tier findTier(int inputTokens, int outputTokens) {
        for (Tier tier : tiers) {
            if(tier.matches(inputTokens, outputTokens)) {
                return tier;
            }
        }
        return null;
    }

    @Override
    public Map<String, String> description() {
        if(this.mode == PricingMode.TIERED) {
            return Collections.emptyMap();
        }

        Map<String, String> map = new LinkedHashMap<>();
        map.put("input", "输入token单价（分/千token）");
        map.put("output", "输出token单价（分/千token）");
        map.put("cachedRead", "命中缓存token单价（分/千token）");
        map.put("cachedCreation", "创建缓存token单价（分/千token）");
        map.put("imageInput", "图片输入token单价（分/千token）");
        map.put("imageOutput", "图片输出token单价（分/千token）");
        return map;
    }

    @AllArgsConstructor
    public enum PricingMode {
        FIXED("fixed", "固定单价"),
        TIERED("tiered", "区间计价");

        private final String code;
        @Getter
        private final String description;

        @JsonCreator
        public static PricingMode fromCode(String code) {
            for (PricingMode mode : PricingMode.values()) {
                if(mode.code.equals(code)) {
                    return mode;
                }
            }
            return null;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

    }

    @Data
    public static class Tier implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer minInputTokens;
        private Integer maxInputTokens;
        private Integer minOutputTokens;
        private Integer maxOutputTokens;

        private BigDecimal inputPrice;
        private BigDecimal outputPrice;
        private BigDecimal imageInputPrice;
        private BigDecimal imageOutputPrice;
        private BigDecimal cachedReadPrice;
        private BigDecimal cachedCreationPrice;

        public boolean matches(int inputTokens, int outputTokens) {
            boolean inputMatch = containsValue(minInputTokens, maxInputTokens, inputTokens);
            if(minOutputTokens == null && maxOutputTokens == null) {
                return inputMatch;
            }

            boolean outputMatch = containsValue(minOutputTokens, maxOutputTokens, outputTokens);
            return inputMatch && outputMatch;
        }

        private boolean containsValue(Integer min, Integer max, int value) {
            // 左开右闭 (min, max]，但第一个区间从 0 开始，包含 0
            boolean afterMin = min == 0 ? value >= 0 : value > min;
            boolean beforeMax = (max == null) || (value <= max);
            return afterMin && beforeMax;
        }
    }

    public boolean validate() {
        if(mode == PricingMode.FIXED) {
            if(input == null || output == null) {
                return false;
            }
            return input.compareTo(BigDecimal.ZERO) > 0 && output.compareTo(BigDecimal.ZERO) > 0;
        } else {
            return validateTiered();
        }
    }

    private boolean validateTiered() {
        // 1. tiers必须存在，且至少2个
        if(tiers == null || tiers.isEmpty() || tiers.size() < 2) {
            return false;
        }

        // 2. 基础字段校验
        if(!validateBasicFields()) {
            return false;
        }

        List<Tier> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparing(Tier::getMinInputTokens)
                .thenComparing(t -> t.minOutputTokens != null ? t.minOutputTokens : 0));

        // 3. 区间覆盖校验
        return validateCompleteCoverage(sorted);
    }

    private boolean validateBasicFields() {
        for (Tier tier : tiers) {
            // 1. 输入token区间验证
            if(tier.minInputTokens == null || tier.minInputTokens < 0) {
                return false;
            }
            if(tier.maxInputTokens != null && tier.minInputTokens >= tier.maxInputTokens) {
                return false;
            }

            // 2. 必填价格验证
            if(tier.inputPrice == null || tier.inputPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    tier.outputPrice == null || tier.outputPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            // 3. 可选价格验证
            if((tier.imageInputPrice != null && tier.imageInputPrice.compareTo(BigDecimal.ZERO) <= 0) ||
                    (tier.imageOutputPrice != null && tier.imageOutputPrice.compareTo(BigDecimal.ZERO) <= 0) ||
                    (tier.cachedReadPrice != null && tier.cachedReadPrice.compareTo(BigDecimal.ZERO) <= 0) ||
                    (tier.cachedCreationPrice != null && tier.cachedCreationPrice.compareTo(BigDecimal.ZERO) <= 0)) {
                return false;
            }

            // 4. 输出token区间验证
            if(tier.minOutputTokens != null) {
                if(tier.minOutputTokens < 0) {
                    return false;
                }
                if(tier.maxOutputTokens != null && tier.minOutputTokens >= tier.maxOutputTokens) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 验证完整覆盖性 1. 区间从0开始 2. 相邻区间的边界连续（curr.max == next.min） 3.
     * 最后一个输入区间的最大值必须null，表示∞
     */

    private boolean validateCompleteCoverage(List<Tier> sorted) {
        if(sorted.get(0).minInputTokens == null || sorted.get(0).minInputTokens != 0) {
            return false;
        }

        int i = 0;
        Integer expectedInputStart = 0;
        while (i < sorted.size()) {
            Tier curr = sorted.get(i);
            if(!curr.minInputTokens.equals(expectedInputStart)) {
                return false; // 输入区间不连续或有重叠
            }

            // 收集所有相同输入区间的tier
            Integer groupInputMin = curr.minInputTokens;
            Integer groupInputMax = curr.maxInputTokens;
            int groupStart = i;
            while (i < sorted.size() &&
                    sorted.get(i).minInputTokens.equals(groupInputMin) &&
                    Objects.equals(sorted.get(i).maxInputTokens, groupInputMax)) {
                i++;
            }

            List<Tier> group = sorted.subList(groupStart, i);
            // 验证同组内的输出区间完整覆盖
            if(!validateOutputCoverageInGroup(group)) {
                return false;
            }
            expectedInputStart = groupInputMax;
        }
        return expectedInputStart == null;
    }

    /**
     * 验证组内输出区间完整覆盖 1. 第一个输出区间从0开始 2. 相邻输出区间的边界连续 3. 最后一个输出区间最大值可以为null，表示∞
     */

    private boolean validateOutputCoverageInGroup(List<Tier> group) {
        if(group.size() == 1 && group.get(0).minOutputTokens == null) {
            return true;
        }

        for (Tier tier : group) {
            if(tier.minOutputTokens == null) {
                return group.size() == 1;
            }
        }

        if(group.get(0).minOutputTokens != 0) {
            return false;
        }

        // 检查输出区间的边界连续性
        Integer expectedOutputStart = 0;
        for (Tier tier : group) {
            if(!tier.minOutputTokens.equals(expectedOutputStart)) {
                return false;
            }
            expectedOutputStart = tier.maxOutputTokens;
        }
        return expectedOutputStart == null;
    }
}
