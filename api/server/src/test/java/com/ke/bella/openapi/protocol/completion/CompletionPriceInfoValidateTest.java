package com.ke.bella.openapi.protocol.completion;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * CompletionPriceInfo.validate() 方法的单元测试
 * 全面覆盖所有验证场景和边界情况
 */
public class CompletionPriceInfoValidateTest {

    // ========== 基本验证测试 ==========

    /**
     * 测试场景1: tiers为null - 应该返回false
     */
    @Test
    public void testValidate_NullTiers() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setTiers(null);

        assertFalse("tiers为null应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景2: tiers为空列表 - 应该返回false
     */
    @Test
    public void testValidate_EmptyTiers() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setTiers(new ArrayList<>());

        assertFalse("tiers为空列表应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景3: 有效的单个tier（只有inputRangePrice，无outputRangePrices）
     */
    @Test
    public void testValidate_ValidSingleTier_NoOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("有效的单个tier应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景4: 有效的多个tier（只有inputRangePrice，无outputRangePrices）
     */
    @Test
    public void testValidate_ValidMultipleTiers_NoOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: (0, 1000]
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // Tier 2: (1000, 5000]
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1000, 5000, "8", "16"));
        tiers.add(tier2);

        // Tier 3: (5000, MAX]
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        tier3.setInputRangePrice(createValidRangePrice(5000, Integer.MAX_VALUE, "5", "10"));
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        assertTrue("有效的多个tier应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景5: 有效的单个tier带输出梯度
     */
    @Test
    public void testValidate_ValidSingleTier_WithOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 1000, "10", "30"));
        outputRanges.add(createValidRangePrice(1000, Integer.MAX_VALUE, "10", "25"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("有效的单个tier带输出梯度应该返回true", priceInfo.validate());
    }

    // ========== Tier结构验证测试 ==========

    /**
     * 测试场景6: tier的inputRangePrice为null - 应该返回false
     */
    @Test
    public void testValidate_Tier_NullInputRangePrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(null);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("inputRangePrice为null应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景7: tier的inputRangePrice无效（minToken >= maxToken）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_MinTokenEqualMaxToken() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(1000, 1000, "10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("minToken == maxToken应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景8: tier的inputRangePrice无效（minToken > maxToken）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_MinTokenGreaterThanMaxToken() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(5000, 1000, "10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("minToken > maxToken应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景9: tier的inputRangePrice无效（input价格为null）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_NullInputPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(Integer.MAX_VALUE);
        inputRange.setInput(null);
        inputRange.setOutput(new BigDecimal("20"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("input价格为null应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景10: tier的inputRangePrice无效（output价格为null）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_NullOutputPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = new CompletionPriceInfo.RangePrice();
        inputRange.setMinToken(0);
        inputRange.setMaxToken(Integer.MAX_VALUE);
        inputRange.setInput(new BigDecimal("10"));
        inputRange.setOutput(null);
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("output价格为null应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景11: tier的inputRangePrice无效（input价格 <= 0）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_InputPriceZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "0", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("input价格为0应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景12: tier的inputRangePrice无效（input价格为负数）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_InputPriceNegative() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "-10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("input价格为负数应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景13: tier的inputRangePrice无效（output价格 <= 0）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_OutputPriceZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "0");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("output价格为0应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景14: tier的inputRangePrice无效（minToken为负数）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_NegativeMinToken() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(-1, Integer.MAX_VALUE, "10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("minToken为负数应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景15: tier的inputRangePrice无效（maxToken为负数）
     */
    @Test
    public void testValidate_Tier_InvalidInputRangePrice_NegativeMaxToken() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, -1, "10", "20");
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("maxToken为负数应该返回false", priceInfo.validate());
    }

    // ========== OutputRangePrices验证测试 ==========

    /**
     * 测试场景16: tier的outputRangePrices为空列表 - 应该返回false
     */
    @Test
    public void testValidate_Tier_EmptyOutputRangePrices() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));
        tier.setOutputRangePrices(new ArrayList<>());

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("outputRangePrices为空列表应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景17: tier的outputRangePrices包含无效的RangePrice
     */
    @Test
    public void testValidate_Tier_InvalidOutputRangePrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 1000, "10", "30"));
        outputRanges.add(createValidRangePrice(1000, 1000, "10", "25")); // 无效：minToken == maxToken

        tier.setOutputRangePrices(outputRanges);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("outputRangePrices包含无效的RangePrice应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景18: 可选价格字段验证 - imageInput有效（>0）
     */
    @Test
    public void testValidate_OptionalPriceFields_ValidImageInput() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setImageInput(new BigDecimal("100"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("imageInput有效时应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景19: 可选价格字段验证 - imageInput无效（<=0）
     */
    @Test
    public void testValidate_OptionalPriceFields_InvalidImageInput() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setImageInput(new BigDecimal("0"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("imageInput为0应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景20: 可选价格字段验证 - imageOutput无效（<=0）
     */
    @Test
    public void testValidate_OptionalPriceFields_InvalidImageOutput() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setImageOutput(new BigDecimal("-1"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("imageOutput为负数应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景21: 可选价格字段验证 - cachedRead无效（<=0）
     */
    @Test
    public void testValidate_OptionalPriceFields_InvalidCachedRead() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setCachedRead(BigDecimal.ZERO);
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("cachedRead为0应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景22: 可选价格字段验证 - cachedCreation无效（<=0）
     */
    @Test
    public void testValidate_OptionalPriceFields_InvalidCachedCreation() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setCachedCreation(new BigDecimal("-5"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("cachedCreation为负数应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景23: 可选价格字段验证 - 所有可选字段都有效
     */
    @Test
    public void testValidate_OptionalPriceFields_AllValid() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange = createValidRangePrice(0, Integer.MAX_VALUE, "10", "20");
        inputRange.setImageInput(new BigDecimal("100"));
        inputRange.setImageOutput(new BigDecimal("200"));
        inputRange.setCachedRead(new BigDecimal("2"));
        inputRange.setCachedCreation(new BigDecimal("5"));
        tier.setInputRangePrice(inputRange);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("所有可选字段都有效时应该返回true", priceInfo.validate());
    }

    // ========== InputRangePrice覆盖性验证测试 ==========

    /**
     * 测试场景24: inputRangePrice覆盖性 - 第一个区间不从0开始
     */
    @Test
    public void testValidate_InputRangeCoverage_NotStartFromZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // 第一个区间从100开始，而不是0
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(100, 1000, "10", "20"));
        tiers.add(tier1);

        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1000, Integer.MAX_VALUE, "8", "16"));
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertFalse("第一个区间不从0开始应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景25: inputRangePrice覆盖性 - 最后一个区间不到达Integer.MAX_VALUE
     */
    @Test
    public void testValidate_InputRangeCoverage_NotEndWithMaxValue() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // 最后一个区间到5000，而不是Integer.MAX_VALUE
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1000, 5000, "8", "16"));
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertFalse("最后一个区间不到达Integer.MAX_VALUE应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景26: inputRangePrice覆盖性 - 区间有间隙
     */
    @Test
    public void testValidate_InputRangeCoverage_HasGap() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // 第二个区间从2000开始，有间隙 (1000, 2000)
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(2000, Integer.MAX_VALUE, "8", "16"));
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertFalse("区间有间隙应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景27: inputRangePrice覆盖性 - 区间有重叠
     */
    @Test
    public void testValidate_InputRangeCoverage_HasOverlap() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // 第二个区间从500开始，与第一个区间重叠
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(500, Integer.MAX_VALUE, "8", "16"));
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertFalse("区间有重叠应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景28: inputRangePrice覆盖性 - 乱序的区间（验证会自动排序并检查）
     */
    @Test
    public void testValidate_InputRangeCoverage_UnorderedRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // 故意乱序添加
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        tier3.setInputRangePrice(createValidRangePrice(5000, Integer.MAX_VALUE, "5", "10"));
        tiers.add(tier3);

        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1000, 5000, "8", "16"));
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertTrue("乱序的有效区间应该通过验证（自动排序）", priceInfo.validate());
    }

    // ========== OutputRangePrices覆盖性验证测试 ==========

    /**
     * 测试场景29: outputRangePrices覆盖性 - 第一个区间不从0开始
     */
    @Test
    public void testValidate_OutputRangeCoverage_NotStartFromZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        // 第一个输出区间从100开始，而不是0
        outputRanges.add(createValidRangePrice(100, 1000, "10", "30"));
        outputRanges.add(createValidRangePrice(1000, Integer.MAX_VALUE, "10", "25"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("输出区间第一个不从0开始应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景30: outputRangePrices覆盖性 - 最后一个区间不到达Integer.MAX_VALUE
     */
    @Test
    public void testValidate_OutputRangeCoverage_NotEndWithMaxValue() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 1000, "10", "30"));
        // 最后一个输出区间到5000，而不是Integer.MAX_VALUE
        outputRanges.add(createValidRangePrice(1000, 5000, "10", "25"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("输出区间最后一个不到达Integer.MAX_VALUE应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景31: outputRangePrices覆盖性 - 区间有间隙
     */
    @Test
    public void testValidate_OutputRangeCoverage_HasGap() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 1000, "10", "30"));
        // 第二个区间从2000开始，有间隙
        outputRanges.add(createValidRangePrice(2000, Integer.MAX_VALUE, "10", "25"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertFalse("输出区间有间隙应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景32: outputRangePrices覆盖性 - 单个输出区间覆盖全部
     */
    @Test
    public void testValidate_OutputRangeCoverage_SingleRangeFullCoverage() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, Integer.MAX_VALUE, "10", "30"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("单个输出区间覆盖全部应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景33: outputRangePrices覆盖性 - 多个输出区间完整覆盖
     */
    @Test
    public void testValidate_OutputRangeCoverage_MultipleRangesFullCoverage() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));

        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 1000, "10", "30"));
        outputRanges.add(createValidRangePrice(1000, 5000, "10", "25"));
        outputRanges.add(createValidRangePrice(5000, Integer.MAX_VALUE, "10", "20"));
        tier.setOutputRangePrices(outputRanges);

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("多个输出区间完整覆盖应该返回true", priceInfo.validate());
    }

    // ========== 复杂场景测试 ==========

    /**
     * 测试场景34: 多个tier，部分有输出梯度，部分没有
     */
    @Test
    public void testValidate_MultipleTiers_MixedOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: 无输出梯度
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // Tier 2: 有输出梯度
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1000, 5000, "8", "16"));
        List<CompletionPriceInfo.RangePrice> outputRanges = new ArrayList<>();
        outputRanges.add(createValidRangePrice(0, 2000, "8", "20"));
        outputRanges.add(createValidRangePrice(2000, Integer.MAX_VALUE, "8", "18"));
        tier2.setOutputRangePrices(outputRanges);
        tiers.add(tier2);

        // Tier 3: 无输出梯度
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        tier3.setInputRangePrice(createValidRangePrice(5000, Integer.MAX_VALUE, "5", "10"));
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        assertTrue("多个tier混合输出梯度应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景35: 多个tier，每个都有输出梯度，所有都有效
     */
    @Test
    public void testValidate_MultipleTiers_AllWithValidOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 5000, "10", "20"));
        List<CompletionPriceInfo.RangePrice> outputRanges1 = new ArrayList<>();
        outputRanges1.add(createValidRangePrice(0, 1000, "10", "30"));
        outputRanges1.add(createValidRangePrice(1000, Integer.MAX_VALUE, "10", "25"));
        tier1.setOutputRangePrices(outputRanges1);
        tiers.add(tier1);

        // Tier 2
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(5000, Integer.MAX_VALUE, "8", "16"));
        List<CompletionPriceInfo.RangePrice> outputRanges2 = new ArrayList<>();
        outputRanges2.add(createValidRangePrice(0, 2000, "8", "20"));
        outputRanges2.add(createValidRangePrice(2000, Integer.MAX_VALUE, "8", "18"));
        tier2.setOutputRangePrices(outputRanges2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertTrue("多个tier都有有效输出梯度应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景36: 多个tier，其中一个tier的输出梯度无效
     */
    @Test
    public void testValidate_MultipleTiers_OneWithInvalidOutputRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: 输出梯度有效
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 5000, "10", "20"));
        List<CompletionPriceInfo.RangePrice> outputRanges1 = new ArrayList<>();
        outputRanges1.add(createValidRangePrice(0, 1000, "10", "30"));
        outputRanges1.add(createValidRangePrice(1000, Integer.MAX_VALUE, "10", "25"));
        tier1.setOutputRangePrices(outputRanges1);
        tiers.add(tier1);

        // Tier 2: 输出梯度无效（有间隙）
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(5000, Integer.MAX_VALUE, "8", "16"));
        List<CompletionPriceInfo.RangePrice> outputRanges2 = new ArrayList<>();
        outputRanges2.add(createValidRangePrice(0, 2000, "8", "20"));
        // 有间隙：从3000开始
        outputRanges2.add(createValidRangePrice(3000, Integer.MAX_VALUE, "8", "18"));
        tier2.setOutputRangePrices(outputRanges2);
        tiers.add(tier2);

        priceInfo.setTiers(tiers);

        assertFalse("其中一个tier的输出梯度无效应该返回false", priceInfo.validate());
    }

    // ========== 边界值测试 ==========

    /**
     * 测试场景37: 边界值 - 单个区间覆盖所有可能值 (0, MAX]
     */
    @Test
    public void testValidate_BoundaryValue_SingleRangeFullSpectrum() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("单个区间覆盖所有可能值应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景38: 边界值 - 价格为非常小的正数
     */
    @Test
    public void testValidate_BoundaryValue_VerySmallPrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "0.00001", "0.00002"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("非常小的正数价格应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景39: 边界值 - 价格为非常大的数
     */
    @Test
    public void testValidate_BoundaryValue_VeryLargePrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "999999999", "999999999"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("非常大的价格应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景40: 边界值 - 多个区间，每个区间宽度为1
     */
    @Test
    public void testValidate_BoundaryValue_MultipleNarrowRanges() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1, "10", "20"));
        tiers.add(tier1);

        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(1, 2, "8", "16"));
        tiers.add(tier2);

        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        tier3.setInputRangePrice(createValidRangePrice(2, Integer.MAX_VALUE, "5", "10"));
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        assertTrue("多个窄区间应该返回true", priceInfo.validate());
    }

    // ========== 特殊场景测试 ==========

    /**
     * 测试场景41: outputRangePrices为null（允许，表示不使用输出梯度）
     */
    @Test
    public void testValidate_OutputRangePrices_Null() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "10", "20"));
        tier.setOutputRangePrices(null); // 明确设置为null

        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("outputRangePrices为null应该返回true", priceInfo.validate());
    }

    /**
     * 测试场景42: 多tier场景 - 验证多个tier中有一个tier无效
     */
    @Test
    public void testValidate_MultipleTiers_OneInvalidTier() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // Tier 1: 有效
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 1000, "10", "20"));
        tiers.add(tier1);

        // Tier 2: 无效 (inputRangePrice为null)
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(null);
        tiers.add(tier2);

        // Tier 3: 有效
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        tier3.setInputRangePrice(createValidRangePrice(1000, Integer.MAX_VALUE, "5", "10"));
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        assertFalse("其中一个tier无效应该返回false", priceInfo.validate());
    }

    /**
     * 测试场景43: 实际使用场景 - 复杂的多层级价格结构
     */
    @Test
    public void testValidate_RealWorldScenario_ComplexPricingStructure() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        // 小规模用量 (0, 10000]: 按输出token分3档
        CompletionPriceInfo.Tier tier1 = new CompletionPriceInfo.Tier();
        tier1.setInputRangePrice(createValidRangePrice(0, 10000, "12", "24"));
        List<CompletionPriceInfo.RangePrice> outputRanges1 = new ArrayList<>();
        outputRanges1.add(createValidRangePrice(0, 1000, "12", "35"));
        outputRanges1.add(createValidRangePrice(1000, 5000, "12", "30"));
        outputRanges1.add(createValidRangePrice(5000, Integer.MAX_VALUE, "12", "28"));
        tier1.setOutputRangePrices(outputRanges1);
        tiers.add(tier1);

        // 中规模用量 (10000, 100000]: 按输出token分2档
        CompletionPriceInfo.Tier tier2 = new CompletionPriceInfo.Tier();
        tier2.setInputRangePrice(createValidRangePrice(10000, 100000, "10", "20"));
        List<CompletionPriceInfo.RangePrice> outputRanges2 = new ArrayList<>();
        outputRanges2.add(createValidRangePrice(0, 5000, "10", "25"));
        outputRanges2.add(createValidRangePrice(5000, Integer.MAX_VALUE, "10", "22"));
        tier2.setOutputRangePrices(outputRanges2);
        tiers.add(tier2);

        // 大规模用量 (100000, MAX]: 固定价格，无输出梯度
        CompletionPriceInfo.Tier tier3 = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice inputRange3 = createValidRangePrice(100000, Integer.MAX_VALUE, "8", "16");
        inputRange3.setCachedRead(new BigDecimal("2"));
        inputRange3.setCachedCreation(new BigDecimal("4"));
        tier3.setInputRangePrice(inputRange3);
        tiers.add(tier3);

        priceInfo.setTiers(tiers);

        assertTrue("复杂的实际使用场景应该通过验证", priceInfo.validate());
    }

    // ========== 货币精度测试 ==========

    /**
     * 测试场景44: 货币精度 - 极小的正数价格（8位小数）
     */
    @Test
    public void testValidate_CurrencyPrecision_VerySmallDecimal() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "0.00000001", "0.00000002"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("极小的价格（8位小数）应该通过验证", priceInfo.validate());
    }

    /**
     * 测试场景45: 货币精度 - 验证BigDecimal精度保持
     */
    @Test
    public void testValidate_CurrencyPrecision_BigDecimalPrecision() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);

        // 使用字符串构造BigDecimal，保证精度
        BigDecimal inputPrice = new BigDecimal("0.123456789012345");
        BigDecimal outputPrice = new BigDecimal("0.987654321098765");
        range.setInput(inputPrice);
        range.setOutput(outputPrice);
        tier.setInputRangePrice(range);
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("高精度BigDecimal应该通过验证", priceInfo.validate());

        // 验证精度没有损失
        assertEquals("输入价格精度应该保持", inputPrice, tier.getInputRangePrice().getInput());
        assertEquals("输出价格精度应该保持", outputPrice, tier.getInputRangePrice().getOutput());
    }

    /**
     * 测试场景46: 货币精度 - 不同精度的可选价格字段
     */
    @Test
    public void testValidate_CurrencyPrecision_MixedPrecisionOptionalFields() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = createValidRangePrice(0, Integer.MAX_VALUE, "10.5", "20.75");

        // 不同精度的可选字段
        range.setImageInput(new BigDecimal("100.123456"));      // 6位小数
        range.setImageOutput(new BigDecimal("200.1"));          // 1位小数
        range.setCachedRead(new BigDecimal("2.99999999"));      // 8位小数
        range.setCachedCreation(new BigDecimal("5"));           // 整数

        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("混合精度的价格应该通过验证", priceInfo.validate());
    }

    /**
     * 测试场景47: 货币精度 - 科学计数法表示的价格
     */
    @Test
    public void testValidate_CurrencyPrecision_ScientificNotation() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);

        // 使用科学计数法
        range.setInput(new BigDecimal("1.23E-5"));    // 0.0000123
        range.setOutput(new BigDecimal("4.56E-4"));   // 0.000456

        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("科学计数法表示的价格应该通过验证", priceInfo.validate());
        assertTrue("科学计数法的输入价格应该大于0", range.getInput().compareTo(BigDecimal.ZERO) > 0);
        assertTrue("科学计数法的输出价格应该大于0", range.getOutput().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 测试场景48: 货币精度 - 非常大的价格值
     */
    @Test
    public void testValidate_CurrencyPrecision_VeryLargePrice() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "99999999999.99", "88888888888.88"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("非常大的价格应该通过验证", priceInfo.validate());
    }

    /**
     * 测试场景49: 货币精度 - 价格为1（边界整数）
     */
    @Test
    public void testValidate_CurrencyPrecision_PriceEqualsOne() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        tier.setInputRangePrice(createValidRangePrice(0, Integer.MAX_VALUE, "1", "1"));
        tiers.add(tier);

        priceInfo.setTiers(tiers);

        assertTrue("价格为1应该通过验证", priceInfo.validate());
    }

    /**
     * 测试场景50: 货币精度 - 价格精度边界（刚好大于0）
     */
    @Test
    public void testValidate_CurrencyPrecision_JustAboveZero() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);

        // 使用BigDecimal的最小正数（比Double.MIN_VALUE更精确）
        range.setInput(new BigDecimal("0.0000000000000000001"));  // 19位小数
        range.setOutput(new BigDecimal("0.0000000000000000002"));

        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("刚好大于0的极小价格应该通过验证", priceInfo.validate());
    }

    /**
     * 测试场景51: 货币精度 - 尾数为零的价格（应该被BigDecimal保留）
     */
    @Test
    public void testValidate_CurrencyPrecision_TrailingZeros() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);

        // 尾数为零的价格
        BigDecimal inputPrice = new BigDecimal("10.00000");
        BigDecimal outputPrice = new BigDecimal("20.50000");
        range.setInput(inputPrice);
        range.setOutput(outputPrice);

        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        assertTrue("尾数为零的价格应该通过验证", priceInfo.validate());

        // BigDecimal会保留尾数零的精度信息
        // 注意：虽然值相等，但scale可能不同
        assertTrue("输入价格值应该正确", range.getInput().compareTo(new BigDecimal("10")) == 0);
        assertTrue("输出价格值应该正确", range.getOutput().compareTo(new BigDecimal("20.5")) == 0);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建一个有效的RangePrice对象
     *
     * @param minToken token区间下限
     * @param maxToken token区间上限
     * @param inputPrice input价格
     * @param outputPrice output价格
     * @return RangePrice对象
     */
    private CompletionPriceInfo.RangePrice createValidRangePrice(int minToken, int maxToken, String inputPrice, String outputPrice) {
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(minToken);
        range.setMaxToken(maxToken);
        range.setInput(new BigDecimal(inputPrice));
        range.setOutput(new BigDecimal(outputPrice));
        return range;
    }
}
