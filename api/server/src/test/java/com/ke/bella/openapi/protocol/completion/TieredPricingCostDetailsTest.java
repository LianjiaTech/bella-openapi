package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 区间定价成本详细信息测试
 * 测试多层级价格（Tiered Pricing）的成本明细正确性
 */
public class TieredPricingCostDetailsTest {

    /**
     * 测试场景1: 输入区间定价（不同输入token范围有不同价格）
     */
    @Test
    public void testInputTieredPricingCostDetails() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 10000] tokens - 高价
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(10000);
        range1.setInput(new BigDecimal("15"));
        range1.setOutput(new BigDecimal("30"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: (10000, MAX] tokens - 低价
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(10000);
        range2.setMaxToken(Integer.MAX_VALUE);
        range2.setInput(new BigDecimal("10"));
        range2.setOutput(new BigDecimal("20"));
        tier2.setInputRangePrice(range2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试：落在第一个区间
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(5000);    // 第一区间
        usage1.setCompletion_tokens(3000);

        CostDetails details1 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage1);

        assertNotNull("成本明细不应为null", details1);
        assertNotNull("输入明细不应为null", details1.getInputDetails());
        assertEquals("应该有1个输入明细项", 1, details1.getInputDetails().size());

        CostDetails.CostDetailItem inputItem1 = details1.getInputDetails().get(0);
        assertEquals("输入token数应为5000", Integer.valueOf(5000), inputItem1.getTokens());
        assertEquals("输入单价应为15（第一区间）", new BigDecimal("15"), inputItem1.getUnitPrice());

        // 测试：落在第二个区间
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(15000);   // 第二区间
        usage2.setCompletion_tokens(8000);

        CostDetails details2 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage2);

        CostDetails.CostDetailItem inputItem2 = details2.getInputDetails().get(0);
        assertEquals("输入token数应为15000", Integer.valueOf(15000), inputItem2.getTokens());
        assertEquals("输入单价应为10（第二区间）", new BigDecimal("10"), inputItem2.getUnitPrice());

        // 验证总成本计算正确
        BigDecimal expectedCost1 = new BigDecimal("5").multiply(new BigDecimal("15"))
                .add(new BigDecimal("3").multiply(new BigDecimal("30")));
        assertEquals("第一区间总成本应正确", 0, expectedCost1.compareTo(details1.getTotalCost()));

        BigDecimal expectedCost2 = new BigDecimal("15").multiply(new BigDecimal("10"))
                .add(new BigDecimal("8").multiply(new BigDecimal("20")));
        assertEquals("第二区间总成本应正确", 0, expectedCost2.compareTo(details2.getTotalCost()));
    }

    /**
     * 测试场景2: 输出区间定价
     */
    @Test
    public void testOutputTieredPricingCostDetails() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();

        // 输入价格（统一）
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(Integer.MAX_VALUE);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(new BigDecimal("20"));  // 默认输出价格
        tier.setInputRangePrice(inputRange);

        // 输出区间价格
        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();

        // 输出区间1: (0, 5000] - 标准价格
        CompletionPriceInfo.RangePrice outputRange1 = new CompletionPriceInfo.RangePrice();
        outputRange1.setMinToken(0);
        outputRange1.setMaxToken(5000);
        outputRange1.setInput(new BigDecimal("10"));
        outputRange1.setOutput(new BigDecimal("30"));
        outputRanges.add(outputRange1);

        // 输出区间2: (5000, MAX] - 优惠价格
        CompletionPriceInfo.RangePrice outputRange2 = new CompletionPriceInfo.RangePrice();
        outputRange2.setMinToken(5000);
        outputRange2.setMaxToken(Integer.MAX_VALUE);
        outputRange2.setInput(new BigDecimal("10"));
        outputRange2.setOutput(new BigDecimal("25"));
        outputRanges.add(outputRange2);

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试：输出在第一个区间
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(8000);
        usage1.setCompletion_tokens(3000);  // 第一区间

        CostDetails details1 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage1);

        assertNotNull("输出明细不应为null", details1.getOutputDetails());
        CostDetails.CostDetailItem outputItem1 = details1.getOutputDetails().get(0);
        assertEquals("输出单价应为30（第一区间）", new BigDecimal("30"), outputItem1.getUnitPrice());

        // 测试：输出在第二个区间
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(8000);
        usage2.setCompletion_tokens(8000);  // 第二区间

        CostDetails details2 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage2);

        CostDetails.CostDetailItem outputItem2 = details2.getOutputDetails().get(0);
        assertEquals("输出单价应为25（第二区间）", new BigDecimal("25"), outputItem2.getUnitPrice());
    }

    /**
     * 测试场景3: 输入输出同时分区间的复杂场景
     */
    @Test
    public void testComplexTieredPricingWithBothInputAndOutput() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: 小规模使用
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(20000);
        range1.setInput(new BigDecimal("12"));
        range1.setOutput(new BigDecimal("24"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: 大规模使用
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(20000);
        range2.setMaxToken(Integer.MAX_VALUE);
        range2.setInput(new BigDecimal("8"));
        range2.setOutput(new BigDecimal("16"));
        tier2.setInputRangePrice(range2);

        // 为Tier 2添加输出区间定价
        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        CompletionPriceInfo.RangePrice outputRange1 = new CompletionPriceInfo.RangePrice();
        outputRange1.setMinToken(0);
        outputRange1.setMaxToken(10000);
        outputRange1.setInput(new BigDecimal("8"));
        outputRange1.setOutput(new BigDecimal("18"));
        outputRanges.add(outputRange1);

        CompletionPriceInfo.RangePrice outputRange2 = new CompletionPriceInfo.RangePrice();
        outputRange2.setMinToken(10000);
        outputRange2.setMaxToken(Integer.MAX_VALUE);
        outputRange2.setInput(new BigDecimal("8"));
        outputRange2.setOutput(new BigDecimal("14"));
        outputRanges.add(outputRange2);

        tier2.setOutputRangePrices(outputRanges);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试：大规模使用 + 大量输出
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(30000);   // Tier 2 输入
        usage.setCompletion_tokens(12000); // Tier 2 输出区间2

        CostDetails details = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        assertNotNull("成本明细不应为null", details);
        assertNotNull("输入明细不应为null", details.getInputDetails());
        assertNotNull("输出明细不应为null", details.getOutputDetails());

        CostDetails.CostDetailItem inputItem = details.getInputDetails().get(0);
        CostDetails.CostDetailItem outputItem = details.getOutputDetails().get(0);

        assertEquals("输入应使用Tier 2价格", new BigDecimal("8"), inputItem.getUnitPrice());
        assertEquals("输出应使用Tier 2输出区间2价格", new BigDecimal("14"), outputItem.getUnitPrice());

        // 验证成本计算
        BigDecimal expectedCost = new BigDecimal("30").multiply(new BigDecimal("8"))
                .add(new BigDecimal("12").multiply(new BigDecimal("14")));
        assertEquals("总成本应正确", 0, expectedCost.compareTo(details.getTotalCost()));

        // 验证明细总和
        BigDecimal detailsSum = inputItem.getCost().add(outputItem.getCost());
        assertEquals("明细总和应等于总成本", 0, details.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景4: 边界值测试（刚好在区间边界）
     */
    @Test
    public void testTieredPricingBoundaryValues() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 10000]
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(10000);
        range1.setInput(new BigDecimal("20"));
        range1.setOutput(new BigDecimal("40"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: (10000, MAX]
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(10000);
        range2.setMaxToken(Integer.MAX_VALUE);
        range2.setInput(new BigDecimal("15"));
        range2.setOutput(new BigDecimal("30"));
        tier2.setInputRangePrice(range2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试边界值：10000（刚好在边界）
        CompletionResponse.TokenUsage usageBoundary = new CompletionResponse.TokenUsage();
        usageBoundary.setPrompt_tokens(10000);
        usageBoundary.setCompletion_tokens(5000);

        CostDetails detailsBoundary = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usageBoundary);

        assertNotNull("边界值应有成本明细", detailsBoundary);
        CostDetails.CostDetailItem inputItem = detailsBoundary.getInputDetails().get(0);

        // 边界值应该使用第一个区间的价格（<=）
        assertEquals("边界值10000应使用第一区间价格", new BigDecimal("20"), inputItem.getUnitPrice());

        // 测试边界值+1：10001（刚好超出边界）
        CompletionResponse.TokenUsage usageJustOver = new CompletionResponse.TokenUsage();
        usageJustOver.setPrompt_tokens(10001);
        usageJustOver.setCompletion_tokens(5000);

        CostDetails detailsJustOver = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usageJustOver);

        CostDetails.CostDetailItem inputItemJustOver = detailsJustOver.getInputDetails().get(0);

        // 10001应该使用第二个区间的价格
        assertEquals("10001应使用第二区间价格", new BigDecimal("15"), inputItemJustOver.getUnitPrice());
    }

    /**
     * 测试场景5: 区间定价 + 特殊token（缓存、图片）
     */
    @Test
    public void testTieredPricingWithSpecialTokens() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        range.setCachedRead(new BigDecimal("2"));
        range.setImageInput(new BigDecimal("100"));
        tier.setInputRangePrice(range);
        tiers.add(tier);

        priceInfo.setTiers(tiers);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail promptDetail = new CompletionResponse.TokensDetail();
        promptDetail.setCached_tokens(2000);
        promptDetail.setImage_tokens(1000);
        usage.setPrompt_tokens_details(promptDetail);

        CostDetails details = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 验证有3个输入明细项
        assertNotNull("输入明细不应为null", details.getInputDetails());
        assertEquals("应该有3个输入明细项", 3, details.getInputDetails().size());

        // 验证各类型明细的单价
        CostDetails.CostDetailItem cachedItem = findDetailByType(details.getInputDetails(), "cached_tokens");
        CostDetails.CostDetailItem imageItem = findDetailByType(details.getInputDetails(), "image_tokens");
        CostDetails.CostDetailItem promptItem = findDetailByType(details.getInputDetails(), "prompt_tokens");

        assertNotNull("应有缓存token明细", cachedItem);
        assertNotNull("应有图片token明细", imageItem);
        assertNotNull("应有普通prompt明细", promptItem);

        assertEquals("缓存token单价应正确", new BigDecimal("2"), cachedItem.getUnitPrice());
        assertEquals("图片token单价应正确", new BigDecimal("100"), imageItem.getUnitPrice());
        assertEquals("普通prompt单价应正确", new BigDecimal("10"), promptItem.getUnitPrice());

        // 验证token数量正确分配
        assertEquals("缓存token数量", Integer.valueOf(2000), cachedItem.getTokens());
        assertEquals("图片token数量", Integer.valueOf(1000), imageItem.getTokens());
        assertEquals("普通prompt数量应扣除特殊token", Integer.valueOf(7000), promptItem.getTokens());

        // 验证总和一致性
        BigDecimal detailsSum = cachedItem.getCost()
                .add(imageItem.getCost())
                .add(promptItem.getCost())
                .add(details.getOutputDetails().get(0).getCost());
        assertEquals("明细总和应等于总成本", 0, details.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 辅助方法：根据类型查找明细项
     */
    private CostDetails.CostDetailItem findDetailByType(List<CostDetails.CostDetailItem> details, String type) {
        if(details == null) {
            return null;
        }
        return details.stream()
                .filter(item -> type.equals(item.getType()))
                .findFirst()
                .orElse(null);
    }
}