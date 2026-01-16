package com.ke.bella.openapi.protocol.completion;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * CompletionPriceInfo.matchRangePrice 方法的单元测试
 * 测试各种场景下的价格区间匹配逻辑
 */
public class CompletionPriceInfoMatchTest {

    /**
     * 测试场景1: 只有输入梯度，没有输出梯度
     * 输入token在第一个区间
     */
    @Test
    public void testMatchRangePrice_OnlyInputRange_FirstTier() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();

        // 构建价格结构: 输入区间 (0, 1000], (1000, 5000], (5000, MAX]
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 1000]
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(1000);
        range1.setInput(new BigDecimal("10"));
        range1.setOutput(new BigDecimal("20"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: (1000, 5000]
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(1000);
        range2.setMaxToken(5000);
        range2.setInput(new BigDecimal("8"));
        range2.setOutput(new BigDecimal("16"));
        tier2.setInputRangePrice(range2);
        tiers.add(tier2);

        // Tier 3: (5000, MAX]
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range3 = new CompletionPriceInfo.RangePrice();
        range3.setMinToken(5000);
        range3.setMaxToken(Integer.MAX_VALUE);
        range3.setInput(new BigDecimal("5"));
        range3.setOutput(new BigDecimal("10"));
        tier3.setInputRangePrice(range3);
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        // 测试匹配第一个区间
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(500, 100);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到tier1的价格", new BigDecimal("10"), result.getInput());
        assertEquals("应该匹配到tier1的价格", new BigDecimal("20"), result.getOutput());
    }

    /**
     * 测试场景2: 只有输入梯度，输入token在第二个区间
     */
    @Test
    public void testMatchRangePrice_OnlyInputRange_SecondTier() {
        CompletionPriceInfo priceInfo = createBasicThreeTierPriceInfo();

        // 测试匹配第二个区间
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(3000, 100);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到tier2的价格", new BigDecimal("8"), result.getInput());
        assertEquals("应该匹配到tier2的价格", new BigDecimal("16"), result.getOutput());
    }

    /**
     * 测试场景3: 只有输入梯度，输入token在最后一个区间
     */
    @Test
    public void testMatchRangePrice_OnlyInputRange_LastTier() {
        CompletionPriceInfo priceInfo = createBasicThreeTierPriceInfo();

        // 测试匹配最后一个区间（大量tokens）
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(10000, 100);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到tier3的价格", new BigDecimal("5"), result.getInput());
        assertEquals("应该匹配到tier3的价格", new BigDecimal("10"), result.getOutput());
    }

    /**
     * 测试场景4: 边界值测试 - 输入token正好等于区间边界
     */
    @Test
    public void testMatchRangePrice_BoundaryValues() {
        CompletionPriceInfo priceInfo = createBasicThreeTierPriceInfo();

        // token=1 应该匹配第一个区间 (0, 1000]
        CompletionPriceInfo.RangePrice result1 = priceInfo.matchRangePrice(1, 100);
        assertNotNull("token=1应该匹配到第一个区间", result1);
        assertEquals(new BigDecimal("10"), result1.getInput());

        // token=1000 应该匹配第一个区间 (0, 1000]
        CompletionPriceInfo.RangePrice result2 = priceInfo.matchRangePrice(1000, 100);
        assertNotNull("token=1000应该匹配到第一个区间", result2);
        assertEquals(new BigDecimal("10"), result2.getInput());

        // token=1001 应该匹配第二个区间 (1000, 5000]
        CompletionPriceInfo.RangePrice result3 = priceInfo.matchRangePrice(1001, 100);
        assertNotNull("token=1001应该匹配到第二个区间", result3);
        assertEquals(new BigDecimal("8"), result3.getInput());

        // token=5000 应该匹配第二个区间 (1000, 5000]
        CompletionPriceInfo.RangePrice result4 = priceInfo.matchRangePrice(5000, 100);
        assertNotNull("token=5000应该匹配到第二个区间", result4);
        assertEquals(new BigDecimal("8"), result4.getInput());

        // token=5001 应该匹配第三个区间 (5000, MAX]
        CompletionPriceInfo.RangePrice result5 = priceInfo.matchRangePrice(5001, 100);
        assertNotNull("token=5001应该匹配到第三个区间", result5);
        assertEquals(new BigDecimal("5"), result5.getInput());
    }

    /**
     * 测试场景5: 特殊边界 - inputToken=0
     */
    @Test
    public void testMatchRangePrice_ZeroInputToken() {
        CompletionPriceInfo priceInfo = createBasicThreeTierPriceInfo();

        // inputToken=0 应该匹配第一个区间（因为第一个区间的minToken=0）
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(0, 100);
        assertNotNull("inputToken=0应该匹配到第一个区间", result);
        assertEquals(new BigDecimal("10"), result.getInput());
    }

    /**
     * 测试场景6: 有输入和输出梯度，输出token在第一个输出区间
     */
    @Test
    public void testMatchRangePrice_WithOutputRange_FirstOutputTier() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // 构建一个有输出梯度的tier
        // 输入区间: (0, 10000]
        // 输出区间: (0, 1000], (1000, 5000], (5000, MAX]
        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();

        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(10000);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(inputRange);

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();

        // 输出区间1: (0, 1000]
        CompletionPriceInfo.RangePrice outputRange1 = new CompletionPriceInfo.RangePrice();
        outputRange1.setMinToken(0);
        outputRange1.setMaxToken(1000);
        outputRange1.setInput(new BigDecimal("10"));
        outputRange1.setOutput(new BigDecimal("30"));
        outputRanges.add(outputRange1);

        // 输出区间2: (1000, 5000]
        CompletionPriceInfo.RangePrice outputRange2 = new CompletionPriceInfo.RangePrice();
        outputRange2.setMinToken(1000);
        outputRange2.setMaxToken(5000);
        outputRange2.setInput(new BigDecimal("10"));
        outputRange2.setOutput(new BigDecimal("25"));
        outputRanges.add(outputRange2);

        // 输出区间3: (5000, MAX]
        CompletionPriceInfo.RangePrice outputRange3 = new CompletionPriceInfo.RangePrice();
        outputRange3.setMinToken(5000);
        outputRange3.setMaxToken(Integer.MAX_VALUE);
        outputRange3.setInput(new BigDecimal("10"));
        outputRange3.setOutput(new BigDecimal("20"));
        outputRanges.add(outputRange3);

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        // 测试匹配第一个输出区间
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 500);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到第一个输出区间的价格", new BigDecimal("30"), result.getOutput());
    }

    /**
     * 测试场景7: 有输入和输出梯度，输出token在第二个输出区间
     */
    @Test
    public void testMatchRangePrice_WithOutputRange_SecondOutputTier() {
        CompletionPriceInfo priceInfo = createPriceInfoWithOutputRanges();

        // 测试匹配第二个输出区间
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 3000);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到第二个输出区间的价格", new BigDecimal("25"), result.getOutput());
    }

    /**
     * 测试场景8: 有输入和输出梯度，输出token在最后一个输出区间
     */
    @Test
    public void testMatchRangePrice_WithOutputRange_LastOutputTier() {
        CompletionPriceInfo priceInfo = createPriceInfoWithOutputRanges();

        // 测试匹配最后一个输出区间
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 10000);
        assertNotNull("应该匹配到价格", result);
        assertEquals("应该匹配到最后一个输出区间的价格", new BigDecimal("20"), result.getOutput());
    }

    /**
     * 测试场景9: 有输入和输出梯度，输出token边界值测试
     */
    @Test
    public void testMatchRangePrice_WithOutputRange_BoundaryValues() {
        CompletionPriceInfo priceInfo = createPriceInfoWithOutputRanges();

        // outputToken=1 应该匹配第一个输出区间
        CompletionPriceInfo.RangePrice result1 = priceInfo.matchRangePrice(5000, 1);
        assertNotNull(result1);
        assertEquals(new BigDecimal("30"), result1.getOutput());

        // outputToken=1000 应该匹配第一个输出区间
        CompletionPriceInfo.RangePrice result2 = priceInfo.matchRangePrice(5000, 1000);
        assertNotNull(result2);
        assertEquals(new BigDecimal("30"), result2.getOutput());

        // outputToken=1001 应该匹配第二个输出区间
        CompletionPriceInfo.RangePrice result3 = priceInfo.matchRangePrice(5000, 1001);
        assertNotNull(result3);
        assertEquals(new BigDecimal("25"), result3.getOutput());

        // outputToken=5000 应该匹配第二个输出区间
        CompletionPriceInfo.RangePrice result4 = priceInfo.matchRangePrice(5000, 5000);
        assertNotNull(result4);
        assertEquals(new BigDecimal("25"), result4.getOutput());

        // outputToken=5001 应该匹配第三个输出区间
        CompletionPriceInfo.RangePrice result5 = priceInfo.matchRangePrice(5000, 5001);
        assertNotNull(result5);
        assertEquals(new BigDecimal("20"), result5.getOutput());
    }

    /**
     * 测试场景10: 有输入和输出梯度，outputToken=0的特殊情况
     */
    @Test
    public void testMatchRangePrice_WithOutputRange_ZeroOutputToken() {
        CompletionPriceInfo priceInfo = createPriceInfoWithOutputRanges();

        // outputToken=0 应该匹配第一个输出区间（因为第一个输出区间的minToken=0）
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 0);
        assertNotNull("outputToken=0应该匹配到第一个输出区间", result);
        assertEquals(new BigDecimal("30"), result.getOutput());
    }

    /**
     * 测试场景11: 多个输入tier，每个tier都有输出梯度
     */
    @Test
    public void testMatchRangePrice_MultipleInputTiersWithOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: 输入 (0, 5000]，输出梯度 (0, 1000], (1000, MAX]
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange1 = new CompletionPriceInfo.RangePrice();
        inputRange1.setMinToken(0);
        inputRange1.setMaxToken(5000);
        inputRange1.setInput(new BigDecimal("10"));
        inputRange1.setOutput(new BigDecimal("20"));
        tier1.setInputRangePrice(inputRange1);

        List<CompletionPriceInfo.RangePrice> outputRanges1 = new ArrayList<>();
        CompletionPriceInfo.RangePrice out1_1 = new CompletionPriceInfo.RangePrice();
        out1_1.setMinToken(0);
        out1_1.setMaxToken(1000);
        out1_1.setInput(new BigDecimal("10"));
        out1_1.setOutput(new BigDecimal("30"));
        outputRanges1.add(out1_1);

        CompletionPriceInfo.RangePrice out1_2 = new CompletionPriceInfo.RangePrice();
        out1_2.setMinToken(1000);
        out1_2.setMaxToken(Integer.MAX_VALUE);
        out1_2.setInput(new BigDecimal("10"));
        out1_2.setOutput(new BigDecimal("25"));
        outputRanges1.add(out1_2);
        tier1.setOutputRangePrices(outputRanges1);
        tiers.add(tier1);

        // Tier 2: 输入 (5000, MAX]，输出梯度 (0, 2000], (2000, MAX]
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange2 = new CompletionPriceInfo.RangePrice();
        inputRange2.setMinToken(5000);
        inputRange2.setMaxToken(Integer.MAX_VALUE);
        inputRange2.setInput(new BigDecimal("8"));
        inputRange2.setOutput(new BigDecimal("16"));
        tier2.setInputRangePrice(inputRange2);

        List<CompletionPriceInfo.RangePrice> outputRanges2 = new ArrayList<>();
        CompletionPriceInfo.RangePrice out2_1 = new CompletionPriceInfo.RangePrice();
        out2_1.setMinToken(0);
        out2_1.setMaxToken(2000);
        out2_1.setInput(new BigDecimal("8"));
        out2_1.setOutput(new BigDecimal("20"));
        outputRanges2.add(out2_1);

        CompletionPriceInfo.RangePrice out2_2 = new CompletionPriceInfo.RangePrice();
        out2_2.setMinToken(2000);
        out2_2.setMaxToken(Integer.MAX_VALUE);
        out2_2.setInput(new BigDecimal("8"));
        out2_2.setOutput(new BigDecimal("15"));
        outputRanges2.add(out2_2);
        tier2.setOutputRangePrices(outputRanges2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        // 测试：输入在tier1，输出在tier1的第一个输出区间
        CompletionPriceInfo.RangePrice result1 = priceInfo.matchRangePrice(3000, 500);
        assertNotNull(result1);
        assertEquals(new BigDecimal("10"), result1.getInput());
        assertEquals(new BigDecimal("30"), result1.getOutput());

        // 测试：输入在tier1，输出在tier1的第二个输出区间
        CompletionPriceInfo.RangePrice result2 = priceInfo.matchRangePrice(3000, 5000);
        assertNotNull(result2);
        assertEquals(new BigDecimal("10"), result2.getInput());
        assertEquals(new BigDecimal("25"), result2.getOutput());

        // 测试：输入在tier2，输出在tier2的第一个输出区间
        CompletionPriceInfo.RangePrice result3 = priceInfo.matchRangePrice(10000, 1000);
        assertNotNull(result3);
        assertEquals(new BigDecimal("8"), result3.getInput());
        assertEquals(new BigDecimal("20"), result3.getOutput());

        // 测试：输入在tier2，输出在tier2的第二个输出区间
        CompletionPriceInfo.RangePrice result4 = priceInfo.matchRangePrice(10000, 5000);
        assertNotNull(result4);
        assertEquals(new BigDecimal("8"), result4.getInput());
        assertEquals(new BigDecimal("15"), result4.getOutput());
    }

    /**
     * 测试场景12: 输入token不匹配任何区间（理论上不应该发生，但需要测试）
     */
    @Test
    public void testMatchRangePrice_InputTokenNotMatched() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // 只创建一个覆盖 (0, 1000] 的区间，不覆盖完整范围
        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(1000);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(range);
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        // 输入token=2000超出了唯一区间的范围
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(2000, 100);
        assertNull("输入token不在任何区间内应该返回null", result);
    }

    /**
     * 测试场景13: 输出token不匹配任何输出区间
     */
    @Test
    public void testMatchRangePrice_OutputTokenNotMatched() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(10000);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(inputRange);

        // 只创建一个覆盖 (0, 1000] 的输出区间
        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        CompletionPriceInfo.RangePrice outputRange = new CompletionPriceInfo.RangePrice();
        outputRange.setMinToken(0);
        outputRange.setMaxToken(1000);
        outputRange.setInput(new BigDecimal("10"));
        outputRange.setOutput(new BigDecimal("30"));
        outputRanges.add(outputRange);

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        // 输出token=2000超出了输出区间的范围
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 2000);
        assertNull("输出token不在任何输出区间内应该返回null", result);
    }

    /**
     * 测试场景14: 空的tier列表
     */
    @Test
    public void testMatchRangePrice_EmptyTiers() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setTiers(new ArrayList<>());

        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(1000, 500);
        assertNull("空的tier列表应该返回null", result);
    }

    /**
     * 测试场景15: null的tier列表
     */
    @Test(expected = NullPointerException.class)
    public void testMatchRangePrice_NullTiers() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setTiers(null);

        // 应该抛出 NullPointerException
        priceInfo.matchRangePrice(1000, 500);
    }

    /**
     * 测试场景16: 输出梯度为空列表
     */
    @Test
    public void testMatchRangePrice_EmptyOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(10000);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(inputRange);
        tier.setOutputRangePrices(new ArrayList<>()); // 空的输出梯度列表

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        // 输出梯度为空列表时，应该返回inputRangePrice
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 1000);
        assertNotNull("输出梯度为空时应该返回inputRangePrice", result);
        assertEquals(new BigDecimal("10"), result.getInput());
        assertEquals(new BigDecimal("20"), result.getOutput());
    }

    /**
     * 测试场景17: 包含缓存价格的匹配
     */
    @Test
    public void testMatchRangePrice_WithCachePrices() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        range.setCachedRead(new BigDecimal("2"));
        range.setCachedCreation(new BigDecimal("5"));
        tier.setInputRangePrice(range);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 1000);
        assertNotNull(result);
        assertEquals(new BigDecimal("2"), result.getCachedRead());
        assertEquals(new BigDecimal("5"), result.getCachedCreation());
    }

    /**
     * 测试场景18: 包含图片价格的匹配
     */
    @Test
    public void testMatchRangePrice_WithImagePrices() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        range.setImageInput(new BigDecimal("100"));
        range.setImageOutput(new BigDecimal("200"));
        tier.setInputRangePrice(range);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(5000, 1000);
        assertNotNull(result);
        assertEquals(new BigDecimal("100"), result.getImageInput());
        assertEquals(new BigDecimal("200"), result.getImageOutput());
    }

    /**
     * 测试场景19: 极大值测试 - Integer.MAX_VALUE
     */
    @Test
    public void testMatchRangePrice_MaxIntegerValue() {
        CompletionPriceInfo priceInfo = createBasicThreeTierPriceInfo();

        // 测试最大整数值
        CompletionPriceInfo.RangePrice result = priceInfo.matchRangePrice(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertNotNull("最大整数值应该能匹配", result);
        assertEquals(new BigDecimal("5"), result.getInput());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建基础的三层输入梯度价格信息（无输出梯度）
     */
    private CompletionPriceInfo createBasicThreeTierPriceInfo() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 1000]
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range1 = new CompletionPriceInfo.RangePrice();
        range1.setMinToken(0);
        range1.setMaxToken(1000);
        range1.setInput(new BigDecimal("10"));
        range1.setOutput(new BigDecimal("20"));
        tier1.setInputRangePrice(range1);
        tiers.add(tier1);

        // Tier 2: (1000, 5000]
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range2 = new CompletionPriceInfo.RangePrice();
        range2.setMinToken(1000);
        range2.setMaxToken(5000);
        range2.setInput(new BigDecimal("8"));
        range2.setOutput(new BigDecimal("16"));
        tier2.setInputRangePrice(range2);
        tiers.add(tier2);

        // Tier 3: (5000, MAX]
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range3 = new CompletionPriceInfo.RangePrice();
        range3.setMinToken(5000);
        range3.setMaxToken(Integer.MAX_VALUE);
        range3.setInput(new BigDecimal("5"));
        range3.setOutput(new BigDecimal("10"));
        tier3.setInputRangePrice(range3);
        tiers.add(tier3);

        priceInfo.setTiers(tiers);
        return priceInfo;
    }

    /**
     * 创建带有输出梯度的价格信息
     */
    private CompletionPriceInfo createPriceInfoWithOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(10000);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(inputRange);

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();

        // 输出区间1: (0, 1000]
        CompletionPriceInfo.RangePrice outputRange1 = new CompletionPriceInfo.RangePrice();
        outputRange1.setMinToken(0);
        outputRange1.setMaxToken(1000);
        outputRange1.setInput(new BigDecimal("10"));
        outputRange1.setOutput(new BigDecimal("30"));
        outputRanges.add(outputRange1);

        // 输出区间2: (1000, 5000]
        CompletionPriceInfo.RangePrice outputRange2 = new CompletionPriceInfo.RangePrice();
        outputRange2.setMinToken(1000);
        outputRange2.setMaxToken(5000);
        outputRange2.setInput(new BigDecimal("10"));
        outputRange2.setOutput(new BigDecimal("25"));
        outputRanges.add(outputRange2);

        // 输出区间3: (5000, MAX]
        CompletionPriceInfo.RangePrice outputRange3 = new CompletionPriceInfo.RangePrice();
        outputRange3.setMinToken(5000);
        outputRange3.setMaxToken(Integer.MAX_VALUE);
        outputRange3.setInput(new BigDecimal("10"));
        outputRange3.setOutput(new BigDecimal("20"));
        outputRanges.add(outputRange3);

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        return priceInfo;
    }
}
