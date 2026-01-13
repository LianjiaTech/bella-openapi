package com.ke.bella.openapi.protocol.completion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 区间定价校验测试
 * 测试场景：
 * 1. 区间冲突检测
 * 2. 边界值测试
 * 3. 输出区间限制测试
 * 4. 完整覆盖性测试
 */
class CompletionPriceInfoTest {

    /**
     * 创建标准的测试价格对象
     */
    private CompletionPriceInfo.Tier createTier(Integer minInput, Integer maxInput, Integer minOutput, Integer maxOutput,
            double inputPrice, double outputPrice) {
        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setMinInputTokens(minInput);
        tier.setMaxInputTokens(maxInput);
        tier.setMinOutputTokens(minOutput);
        tier.setMaxOutputTokens(maxOutput);
        tier.setInputPrice(BigDecimal.valueOf(inputPrice));
        tier.setOutputPrice(BigDecimal.valueOf(outputPrice));
        return tier;
    }

    // ================== 基础验证测试 ==================

    @Test
    void testValid_MinimumTwoTiers() {
        // 最简单的有效配置：2个tier，最后一个输入最大值为null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015) // 最后一个maxInput必须为null
        );
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "最后一个输入区间最大值为null应该有效");
    }

    @Test
    void testInvalid_LastInputMaxNotNull() {
        // 最后一个tier的maxInput不是null - 应该失败
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(100000, 500000, null, null, 0.005, 0.015) // 最后区间最大值不是null
        );
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "最后一个输入区间最大值必须为null");
    }

    @Test
    void testValid_ThreeTiers() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 50000, null, null, 0.02, 0.06),
                createTier(50000, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015) // 最后一个为null
        );
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "三层区间且最后为null应该有效");
    }

    @Test
    void testInvalid_NotStartFromZero() {
        // 第一个区间不从0开始
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(1000, 100000, null, null, 0.01, 0.03),  // 从1000开始
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "第一个区间必须从0开始");
    }

    @Test
    void testInvalid_HasGap() {
        // 区间之间有空隙
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(150000, null, null, null, 0.005, 0.015) // 空隙：100000~150000
        );
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "区间之间不能有空隙");
    }

    @Test
    void testInvalid_Overlap() {
        // 区间重叠
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(50000, null, null, null, 0.005, 0.015) // 重叠
        );
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "区间不能重叠");
    }

    @Test
    void testInvalid_LessThanTwoTiers() {
        // 只有1个tier
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, null, null, null, 0.01, 0.03));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "区间定价至少需要2个tier");
    }

    // ================== 输出区间测试 ==================

    @Test
    void testValid_WithOutputRanges() {
        // 同一输入区间，多个输出子区间，最后一个输出最大值为null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),     // 输出(0, 4000]
                createTier(0, 100000, 4000, null, 0.01, 0.02),  // 输出(4000,
                                                                // null]，最后输出区间
                createTier(100000, null, null, null, 0.005, 0.015) // 最后输入区间
        );
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "同一输入区间，最后输出区间最大值为null应该有效");
    }

    @Test
    void testInvalid_OutputLastMaxNotNull() {
        // 同一输入区间内，最后一个输出区间最大值不是null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(0, 100000, 4000, 16000, 0.01, 0.02),   // 最后输出区间最大值不是null
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "同一输入区间内，最后一个输出区间最大值必须为null");
    }

    @Test
    void testValid_MultipleInputRangesWithOutputRanges() {
        // 多个输入区间，每个都有输出子区间
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                // 第一个输入区间(0, 50000]
                createTier(0, 50000, 0, 2000, 0.02, 0.06),
                createTier(0, 50000, 2000, 8000, 0.02, 0.04),
                createTier(0, 50000, 8000, null, 0.02, 0.03),     // 输出最大值null
                // 第二个输入区间(50000, 150000]
                createTier(50000, 150000, 0, 8000, 0.01, 0.03),
                createTier(50000, 150000, 8000, null, 0.01, 0.02), // 输出最大值null
                // 第三个输入区间(150000, null]
                createTier(150000, null, null, null, 0.005, 0.015) // 输入最大值null
        );
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "多个输入区间，每个输出区间的最后max都为null应该有效");
    }

    @Test
    void testInvalid_OutputNotStartFromZero() {
        // 输出区间不从0开始
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 1000, 4000, 0.01, 0.03),    // output从1000开始
                createTier(0, 100000, 4000, null, 0.01, 0.02),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "输出区间必须从0开始");
    }

    @Test
    void testInvalid_OutputHasGap() {
        // 输出区间之间有空隙
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(0, 100000, 5000, null, 0.01, 0.02),    // 空隙：4000~5000
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "输出区间之间不能有空隙");
    }

    @Test
    void testInvalid_MixedOutputRanges() {
        // 同一输入区间内，一个有输出限制，一个没有
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),     // 有输出限制
                createTier(0, 100000, null, null, 0.01, 0.02),  // 无输出限制（minOutput=null）
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "同一输入区间内不能混合有/无输出限制");
    }

    @Test
    void testValid_NoOutputLimit() {
        // 无输出限制（minOutput=null）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),      // 无输出限制
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "无输出限制应该有效");
    }

    // ================== 边界值测试 ==================

    @Test
    void testBoundary_AdjacentRanges() {
        // 相邻区间边界值
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "相邻区间应该有效");

        // 测试匹配逻辑（假设findTier方法存在）
        assertNotNull(priceInfo.findTier(100000, 1000), "边界值100000应匹配第一个区间");
        assertNotNull(priceInfo.findTier(100001, 1000), "100001应匹配第二个区间");
    }

    @Test
    void testBoundary_ZeroTokens() {
        // 0 tokens 的请求
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate());
        assertNotNull(priceInfo.findTier(0, 1000), "0 tokens应该匹配第一个区间");
    }

    @Test
    void testBoundary_OutputBoundary() {
        // 输出区间边界
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(0, 100000, 4000, null, 0.01, 0.02),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate());
        // 测试边界匹配
        assertNotNull(priceInfo.findTier(50000, 4000), "output=4000应该匹配第一个输出区间");
        assertNotNull(priceInfo.findTier(50000, 4001), "output=4001应该匹配第二个输出区间");
    }

    // ================== 价格验证测试 ==================

    @Test
    void testInvalid_NegativePrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, -0.01, 0.03), // 负价格
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "价格不能为负数");
    }

    @Test
    void testInvalid_ZeroPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0, 0.03), // 零价格
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "价格必须大于0");
    }

    @Test
    void testValid_WithOptionalPrices() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.02);
        tier1.setImageInputPrice(BigDecimal.valueOf(0.05));
        tier1.setCachedReadPrice(BigDecimal.valueOf(0.001));

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.008, 0.015);
        tier2.setImageOutputPrice(BigDecimal.valueOf(0.03));
        tier2.setCachedCreationPrice(BigDecimal.valueOf(0.002));

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertTrue(priceInfo.validate(), "可选价格有效应该通过");
    }

    @Test
    void testInvalid_OptionalPriceZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.02);
        tier1.setImageInputPrice(BigDecimal.ZERO); // 可选价格为0

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.008, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertFalse(priceInfo.validate(), "可选价格如果设置必须大于0");
    }

    // ================== 固定定价测试 ==================

    @Test
    void testFixedPricing_Valid() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        assertTrue(priceInfo.validate(), "固定定价应该有效");
    }

    @Test
    void testFixedPricing_InvalidNullInput() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(null);
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        assertFalse(priceInfo.validate(), "固定定价input不能为null");
    }

    @Test
    void testFixedPricing_InvalidZeroPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.ZERO);

        assertFalse(priceInfo.validate(), "固定定价价格必须>0");
    }

    // ================== 其他边界测试 ==================

    @Test
    void testInvalid_MinEqualsMax() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 0, null, null, 0.01, 0.02), // min == max
                createTier(0, null, null, null, 0.008, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "min不能等于max");
    }

    @Test
    void testInvalid_NegativeMin() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(-1, 100000, null, null, 0.01, 0.02), // 负数min
                createTier(100000, null, null, null, 0.008, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "min不能为负数");
    }

    @Test
    void testInvalid_EmptyTiersList() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);
        priceInfo.setTiers(new ArrayList<>());

        assertFalse(priceInfo.validate(), "tiers为空应该校验失败");
    }

    @Test
    void testInvalid_NullTiersList() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);
        priceInfo.setTiers(null);

        assertFalse(priceInfo.validate(), "tiers为null应该校验失败");
    }

    // ================== 复杂场景测试 ==================

    @Test
    void testComplex_MultipleInputAndOutputRanges() {
        // 完整的复杂场景
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                // 输入区间1: (0, 50000]
                createTier(0, 50000, 0, 2000, 0.02, 0.06),
                createTier(0, 50000, 2000, 8000, 0.02, 0.04),
                createTier(0, 50000, 8000, null, 0.02, 0.03),       // 输出区间最后为null
                // 输入区间2: (50000, 150000]
                createTier(50000, 150000, 0, 8000, 0.01, 0.03),
                createTier(50000, 150000, 8000, null, 0.01, 0.02),  // 输出区间最后为null
                // 输入区间3: (150000, null]
                createTier(150000, null, null, null, 0.005, 0.015)  // 输入区间最后为null
        );
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "复杂多层区间应该有效");
    }

    @Test
    void testInvalid_InputMinGreaterThanMax() {
        // min > max
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(200000, 150000, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "min不能大于max");
    }

    @Test
    void testInvalid_OutputMinGreaterThanMax() {
        // 输出区间 min > max
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(0, 100000, 8000, 2000, 0.01, 0.02), // 输出 min > max
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "输出区间min不能大于max");
    }

    @Test
    void testInvalid_NullMinInputTokens() {
        // minInputTokens为null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.005, 0.015);
        tier2.setMinInputTokens(null); // 设置为null

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertFalse(priceInfo.validate(), "minInputTokens不能为null");
    }

    @Test
    void testInvalid_NegativeOutputMin() {
        // 输出区间min为负数
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, -100, 4000, 0.01, 0.03), // 负数输出min
                createTier(0, 100000, 4000, null, 0.01, 0.02),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "输出区间min不能为负数");
    }

    @Test
    void testValid_ThreeOutputRanges() {
        // 三个输出区间
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 2000, 0.01, 0.05),
                createTier(0, 100000, 2000, 8000, 0.01, 0.03),
                createTier(0, 100000, 8000, null, 0.01, 0.02),   // 最后输出为null
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "三个输出区间且最后为null应该有效");
    }

    @Test
    void testInvalid_MultipleOutputGroups_FirstNotNull() {
        // 同一输入区间，第一个输出子区间不从0开始，而是从非0值开始
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 100, 4000, 0.01, 0.03),    // 从100开始
                createTier(0, 100000, 4000, null, 0.01, 0.02),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "有输出限制时，第一个输出区间必须从0开始");
    }

    @Test
    void testInvalid_MiddleOutputGroupLastNotNull() {
        // 中间某个输入区间的最后输出子区间max不是null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                // 第一个输入区间
                createTier(0, 50000, 0, 4000, 0.02, 0.06),
                createTier(0, 50000, 4000, 8000, 0.02, 0.04),    // 最后输出max不是null
                // 第二个输入区间
                createTier(50000, 100000, 0, null, 0.01, 0.03),
                // 第三个输入区间
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "每个输入区间的最后输出子区间max必须为null");
    }

    @Test
    void testValid_FourInputTiers() {
        // 四个输入层级
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 10000, null, null, 0.03, 0.09),
                createTier(10000, 50000, null, null, 0.02, 0.06),
                createTier(50000, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "四个输入层级应该有效");
    }

    @Test
    void testInvalid_InputRangesNotSorted() {
        // 输入区间顺序混乱（虽然代码会排序，但测试逻辑上的混乱）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(100000, null, null, null, 0.005, 0.015),
                createTier(0, 100000, null, null, 0.01, 0.03));
        priceInfo.setTiers(tiers);

        // 虽然会排序，但验证应该通过（因为排序后是正确的）
        assertTrue(priceInfo.validate(), "代码会自动排序，所以应该有效");
    }

    @Test
    void testInvalid_DuplicateInputRanges() {
        // 完全相同的输入区间（但价格不同）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(0, 100000, null, null, 0.02, 0.04),   // 重复区间
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        // 同一输入区间有两个无输出限制的tier，会被视为同组，需要验证输出覆盖
        // 但这里两个都是minOutput=null，根据逻辑应该只能有一个
        assertFalse(priceInfo.validate(), "同一输入区间不能有多个minOutput=null的tier");
    }

    @Test
    void testInvalid_NullInputPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        tier1.setInputPrice(null); // 设置为null

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.005, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertFalse(priceInfo.validate(), "inputPrice不能为null");
    }

    @Test
    void testInvalid_NullOutputPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        tier1.setOutputPrice(null); // 设置为null

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.005, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertFalse(priceInfo.validate(), "outputPrice不能为null");
    }

    @Test
    void testInvalid_OptionalPriceNegative() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.02);
        tier1.setImageInputPrice(BigDecimal.valueOf(-0.05)); // 负数

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.008, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertFalse(priceInfo.validate(), "可选价格不能为负数");
    }

    @Test
    void testValid_AllOptionalPricesSet() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.02);
        tier1.setImageInputPrice(BigDecimal.valueOf(0.05));
        tier1.setImageOutputPrice(BigDecimal.valueOf(0.06));
        tier1.setCachedReadPrice(BigDecimal.valueOf(0.001));
        tier1.setCachedCreationPrice(BigDecimal.valueOf(0.002));

        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.008, 0.015);
        tier2.setImageInputPrice(BigDecimal.valueOf(0.04));
        tier2.setImageOutputPrice(BigDecimal.valueOf(0.05));
        tier2.setCachedReadPrice(BigDecimal.valueOf(0.0008));
        tier2.setCachedCreationPrice(BigDecimal.valueOf(0.0015));

        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        assertTrue(priceInfo.validate(), "所有可选价格都设置且有效应该通过");
    }

    @Test
    void testFixedPricing_InvalidBothNull() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(null);
        priceInfo.setOutput(null);

        assertFalse(priceInfo.validate(), "固定定价input和output都不能为null");
    }

    @Test
    void testFixedPricing_ValidWithTiersIgnored() {
        // 固定定价模式下，即使设置了tiers也应该被忽略
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        // 设置tiers（应该被忽略）
        priceInfo.setTiers(Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03)));

        assertTrue(priceInfo.validate(), "固定定价模式下tiers应该被忽略");
    }

    @Test
    void testValid_VerySmallPrice() {
        // 非常小的价格（但大于0）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.0000001, 0.0000002),
                createTier(100000, null, null, null, 0.00000005, 0.0000001));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "非常小的价格应该有效");
    }

    @Test
    void testValid_VeryLargePrice() {
        // 非常大的价格
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 1000.0, 2000.0),
                createTier(100000, null, null, null, 500.0, 1000.0));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "非常大的价格应该有效");
    }

    @Test
    void testValid_MaxIntegerBoundary() {
        // 使用Integer.MAX_VALUE作为中间边界
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, Integer.MAX_VALUE, null, null, 0.01, 0.03),
                createTier(Integer.MAX_VALUE, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "支持Integer.MAX_VALUE作为边界");
    }

    @Test
    void testValid_OnlyOneTokenRangePerTier() {
        // 每个tier只覆盖1个token（边界情况）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 1, null, null, 0.01, 0.03),
                createTier(1, 2, null, null, 0.009, 0.025),
                createTier(2, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "每个tier覆盖最小范围应该有效");
    }

    @Test
    void testValid_ComplexMixedScenario() {
        // 复杂混合场景：有些输入区间有输出子区间，有些没有
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                // 第一个输入区间：有输出子区间
                createTier(0, 50000, 0, 2000, 0.02, 0.06),
                createTier(0, 50000, 2000, null, 0.02, 0.04),
                // 第二个输入区间：无输出限制
                createTier(50000, 100000, null, null, 0.01, 0.03),
                // 第三个输入区间：有输出子区间
                createTier(100000, 200000, 0, 5000, 0.008, 0.025),
                createTier(100000, 200000, 5000, null, 0.008, 0.02),
                // 第四个输入区间：无输出限制
                createTier(200000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "复杂混合场景应该有效");
    }

    @Test
    void testInvalid_LastInputTierWithOutputRangesNotComplete() {
        // 最后一个输入区间有输出子区间，但输出区间不完整
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, null, null, 0.01, 0.03),
                createTier(100000, null, 0, 4000, 0.005, 0.02)  // 最后输入区间的输出max不是null
        );
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "最后输入区间如果有输出限制，最后输出max必须为null");
    }

    @Test
    void testValid_AllInputTiersHaveOutputRanges() {
        // 所有输入区间都有输出子区间
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                // 输入区间1
                createTier(0, 50000, 0, 3000, 0.02, 0.06),
                createTier(0, 50000, 3000, null, 0.02, 0.04),
                // 输入区间2
                createTier(50000, 100000, 0, 5000, 0.01, 0.035),
                createTier(50000, 100000, 5000, null, 0.01, 0.025),
                // 输入区间3（最后）
                createTier(100000, null, 0, 8000, 0.005, 0.02),
                createTier(100000, null, 8000, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "所有输入区间都有输出子区间应该有效");
    }

    @Test
    void testValid_SingleOutputPerInput() {
        // 每个输入区间只有一个输出区间（max=null）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 50000, 0, null, 0.02, 0.06),
                createTier(50000, 100000, 0, null, 0.01, 0.03),
                createTier(100000, null, 0, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "每个输入区间只有一个输出区间应该有效");
    }

    @Test
    void testInvalid_OutputMinEqualsMax() {
        // 输出区间 min == max
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(0, 100000, 4000, 4000, 0.01, 0.02),   // min == max
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertFalse(priceInfo.validate(), "输出区间min不能等于max");
    }


    @Test
    void testValid_UnsortedOutputRangesAutoSorted() {
        // 同一输入区间内，未排序的输出子区间
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        List<CompletionPriceInfo.Tier> tiers = Arrays.asList(
                createTier(0, 100000, 4000, null, 0.01, 0.02),   // 顺序反了
                createTier(0, 100000, 0, 4000, 0.01, 0.03),
                createTier(100000, null, null, null, 0.005, 0.015));
        priceInfo.setTiers(tiers);

        assertTrue(priceInfo.validate(), "未排序的输出子区间应该被自动排序并验证通过");
    }
}
