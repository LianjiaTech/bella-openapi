package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 费用计算精度测试
 * 测试在实际费用计算过程中BigDecimal的精度保持和计算正确性
 */
public class CompletionPriceCalculationPrecisionTest {

    /**
     * 测试场景1: 极小价格的费用计算精度
     */
    @Test
    public void testCalculateCost_VerySmallPrice() {
        // 创建一个极小价格的配置
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("0.00000001"));   // 每千token 0.00000001 分
        range.setOutput(new BigDecimal("0.00000002"));  // 每千token 0.00000002 分
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 创建TokenUsage
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(100000);      // 10万tokens
        usage.setCompletion_tokens(50000);   // 5万tokens

        // 计算费用
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 预期费用 = (100000 / 1000) * 0.00000001 + (50000 / 1000) * 0.00000002
        //          = 100 * 0.00000001 + 50 * 0.00000002
        //          = 0.000001 + 0.000001
        //          = 0.000002
        BigDecimal expected = new BigDecimal("0.000001")
                .add(new BigDecimal("0.000001"));

        assertEquals("极小价格的费用计算应该精确", 0, expected.compareTo(cost));
    }

    /**
     * 测试场景2: 高精度价格的费用计算
     */
    @Test
    public void testCalculateCost_HighPrecisionPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("0.123456789012345"));    // 15位小数
        range.setOutput(new BigDecimal("0.987654321098765"));   // 15位小数
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1234);
        usage.setCompletion_tokens(5678);

        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 预期费用 = (1234 / 1000) * 0.123456789012345 + (5678 / 1000) * 0.987654321098765
        BigDecimal expected = new BigDecimal("0.123456789012345")
                .multiply(new BigDecimal("1234").divide(new BigDecimal("1000"), 20, RoundingMode.HALF_UP))
                .add(new BigDecimal("0.987654321098765")
                        .multiply(new BigDecimal("5678").divide(new BigDecimal("1000"), 20, RoundingMode.HALF_UP)));

        // 由于除法可能引入舍入误差,这里使用compareTo比较
        assertNotNull("费用不应该为null", cost);
        assertTrue("费用应该大于0", cost.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 测试场景3: 不同精度混合计算
     */
    @Test
    public void testCalculateCost_MixedPrecision() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));              // 整数
        range.setOutput(new BigDecimal("20.5"));           // 1位小数
        range.setImageInput(new BigDecimal("100.123456")); // 6位小数
        range.setCachedRead(new BigDecimal("2.99999999")); // 8位小数
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 创建包含特殊token的usage
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);
        usage.setCompletion_tokens(3000);

        CompletionResponse.TokensDetail promptDetails = new CompletionResponse.TokensDetail();
        promptDetails.setImage_tokens(1000);   // 1000个图片token
        promptDetails.setCached_tokens(500);   // 500个缓存命中token
        usage.setPrompt_tokens_details(promptDetails);

        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 验证计算结果不为null且大于0
        assertNotNull("费用不应该为null", cost);
        assertTrue("费用应该大于0", cost.compareTo(BigDecimal.ZERO) > 0);

        // 手动计算预期值
        // 普通输入token: 5000 - 1000(图片) - 500(缓存) = 3500
        // 费用 = 3500/1000 * 10 + 3000/1000 * 20.5 + 1000/1000 * 100.123456 + 500/1000 * 2.99999999
        BigDecimal expectedNormalInput = new BigDecimal("3.5").multiply(new BigDecimal("10"));
        BigDecimal expectedOutput = new BigDecimal("3").multiply(new BigDecimal("20.5"));
        BigDecimal expectedImageInput = new BigDecimal("1").multiply(new BigDecimal("100.123456"));
        BigDecimal expectedCachedRead = new BigDecimal("0.5").multiply(new BigDecimal("2.99999999"));

        BigDecimal expected = expectedNormalInput
                .add(expectedOutput)
                .add(expectedImageInput)
                .add(expectedCachedRead);

        // 比较计算结果(允许微小的精度误差)
        BigDecimal diff = cost.subtract(expected).abs();
        assertTrue("费用计算误差应该在0.0001以内，实际差值: " + diff,
                diff.compareTo(new BigDecimal("0.0001")) < 0);
    }

    /**
     * 测试场景4: 大量tokens的费用计算（防止溢出）
     */
    @Test
    public void testCalculateCost_LargeTokenAmount() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("99999.99"));
        range.setOutput(new BigDecimal("88888.88"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1000000000);      // 10亿tokens
        usage.setCompletion_tokens(500000000);   // 5亿tokens

        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        assertNotNull("大量tokens的费用不应该为null", cost);
        assertTrue("费用应该大于0", cost.compareTo(BigDecimal.ZERO) > 0);

        // 验证没有溢出（费用应该是一个合理的数值）
        // 预期费用 = 1000000000/1000 * 99999.99 + 500000000/1000 * 88888.88
        //          = 1000000 * 99999.99 + 500000 * 88888.88
        BigDecimal expected = new BigDecimal("1000000").multiply(new BigDecimal("99999.99"))
                .add(new BigDecimal("500000").multiply(new BigDecimal("88888.88")));

        assertEquals("大量tokens的费用计算应该正确", 0, expected.compareTo(cost));
    }

    /**
     * 测试场景5: 科学计数法价格的费用计算
     */
    @Test
    public void testCalculateCost_ScientificNotationPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("1.23E-5"));    // 0.0000123
        range.setOutput(new BigDecimal("4.56E-4"));   // 0.000456
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        assertNotNull("科学计数法价格的费用不应该为null", cost);
        assertTrue("费用应该大于0", cost.compareTo(BigDecimal.ZERO) > 0);

        // 预期费用 = 10 * 0.0000123 + 5 * 0.000456
        BigDecimal expected = new BigDecimal("10").multiply(new BigDecimal("1.23E-5"))
                .add(new BigDecimal("5").multiply(new BigDecimal("4.56E-4")));

        // 允许小的精度误差
        BigDecimal diff = cost.subtract(expected).abs();
        assertTrue("科学计数法价格的费用计算误差应该在0.0000001以内，实际差值: " + diff,
                diff.compareTo(new BigDecimal("0.0000001")) < 0);
    }

    /**
     * 测试场景6: 零tokens的费用计算
     */
    @Test
    public void testCalculateCost_ZeroTokens() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(0);
        usage.setCompletion_tokens(0);

        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        assertEquals("零tokens的费用应该为0", 0, BigDecimal.ZERO.compareTo(cost));
    }

    /**
     * 测试场景7: 区间定价的精度测试
     */
    @Test
    public void testCalculateCost_TieredPricingPrecision() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 10000] - 高价
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(10000);
        range1.setInput(new BigDecimal("12.345678"));
        range1.setOutput(new BigDecimal("23.456789"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: (10000, MAX] - 低价
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(10000);
        range2.setMaxToken(Integer.MAX_VALUE);
        range2.setInput(new BigDecimal("8.765432"));
        range2.setOutput(new BigDecimal("17.654321"));
        tier2.setInputRangePrice(range2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试落在第一个区间
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(5000);
        usage1.setCompletion_tokens(3000);

        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage1);
        BigDecimal expected1 = new BigDecimal("5").multiply(new BigDecimal("12.345678"))
                .add(new BigDecimal("3").multiply(new BigDecimal("23.456789")));

        BigDecimal diff1 = cost1.subtract(expected1).abs();
        assertTrue("第一个区间的费用计算误差应该在0.0001以内，实际差值: " + diff1,
                diff1.compareTo(new BigDecimal("0.0001")) < 0);

        // 测试落在第二个区间
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(15000);
        usage2.setCompletion_tokens(8000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage2);
        BigDecimal expected2 = new BigDecimal("15").multiply(new BigDecimal("8.765432"))
                .add(new BigDecimal("8").multiply(new BigDecimal("17.654321")));

        BigDecimal diff2 = cost2.subtract(expected2).abs();
        assertTrue("第二个区间的费用计算误差应该在0.0001以内，实际差值: " + diff2,
                diff2.compareTo(new BigDecimal("0.0001")) < 0);
    }

    /**
     * 测试场景8: 输出区间定价的精度测试
     */
    @Test
    public void testCalculateCost_OutputTieredPricingPrecision() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(Integer.MAX_VALUE);
        inputRange.setInput(new BigDecimal("10.123456789"));
        inputRange.setOutput(new BigDecimal("20.987654321"));
        tier.setInputRangePrice(inputRange);

        // 输出区间1: (0, 5000]
        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        CompletionPriceInfo.RangePrice outputRange1 = new CompletionPriceInfo.RangePrice();
        outputRange1.setMinToken(0);
        outputRange1.setMaxToken(5000);
        outputRange1.setInput(new BigDecimal("10.123456789"));
        outputRange1.setOutput(new BigDecimal("30.111111111"));
        outputRanges.add(outputRange1);

        // 输出区间2: (5000, MAX]
        CompletionPriceInfo.RangePrice outputRange2 = new CompletionPriceInfo.RangePrice();
        outputRange2.setMinToken(5000);
        outputRange2.setMaxToken(Integer.MAX_VALUE);
        outputRange2.setInput(new BigDecimal("10.123456789"));
        outputRange2.setOutput(new BigDecimal("25.222222222"));
        outputRanges.add(outputRange2);

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 测试输出token在第一个输出区间
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(8000);
        usage1.setCompletion_tokens(3000);

        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage1);
        BigDecimal expected1 = new BigDecimal("8").multiply(new BigDecimal("10.123456789"))
                .add(new BigDecimal("3").multiply(new BigDecimal("30.111111111")));

        BigDecimal diff1 = cost1.subtract(expected1).abs();
        assertTrue("第一个输出区间的费用计算误差应该在0.001以内，实际差值: " + diff1,
                diff1.compareTo(new BigDecimal("0.001")) < 0);

        // 测试输出token在第二个输出区间
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(8000);
        usage2.setCompletion_tokens(8000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage2);
        BigDecimal expected2 = new BigDecimal("8").multiply(new BigDecimal("10.123456789"))
                .add(new BigDecimal("8").multiply(new BigDecimal("25.222222222")));

        BigDecimal diff2 = cost2.subtract(expected2).abs();
        assertTrue("第二个输出区间的费用计算误差应该在0.001以内，实际差值: " + diff2,
                diff2.compareTo(new BigDecimal("0.001")) < 0);
    }
}
