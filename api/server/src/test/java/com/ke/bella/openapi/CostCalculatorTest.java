package com.ke.bella.openapi;

import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 费用计算器测试 - 重点测试区间定价
 */
class CostCalculatorTest {

    // ================== 固定定价测试 ==================

    @Test
    void testFixedPricing_Basic() {
        // 场景：基础固定定价（input + output）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));  // 0.01分/千token
        priceInfo.setOutput(BigDecimal.valueOf(0.03)); // 0.03分/千token

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);      // 10k input
        usage.setCompletion_tokens(5000);   // 5k output

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：(10000/1000)*0.01 + (5000/1000)*0.03 = 0.1 + 0.15 = 0.25分
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.25)), "固定定价计算错误");
    }

    @Test
    void testFixedPricing_WithCache() {
        // 场景：固定定价 + 缓存token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));      // 缓存读取更便宜
        priceInfo.setCachedCreation(BigDecimal.valueOf(0.0125)); // 缓存创建稍贵

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        // 设置缓存token详情
        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(3000);         // 3k缓存命中
        inputDetail.setCache_creation_tokens(2000); // 2k缓存创建
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期计算：
        // 普通输入：(10000 - 3000 - 2000)/1000 * 0.01 = 5 * 0.01 = 0.05
        // 缓存读取：3000/1000 * 0.001 = 0.003
        // 缓存创建：2000/1000 * 0.0125 = 0.025
        // 输出：5000/1000 * 0.03 = 0.15
        // 总计：0.05 + 0.003 + 0.025 + 0.15 = 0.228
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.228)), "固定定价+缓存计算错误");
    }

    @Test
    void testFixedPricing_WithImage() {
        // 场景：固定定价 + 图片token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));  // 图片输入更贵

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(4000); // 4k图片token
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通输入：(10000-4000)/1000 * 0.01 = 0.06
        // 图片输入：4000/1000 * 0.02 = 0.08
        // 输出：5000/1000 * 0.03 = 0.15
        // 总计：0.06 + 0.08 + 0.15 = 0.29
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.29)), "固定定价+图片计算错误");
    }

    // ================== 区间定价测试 ==================

    @Test
    void testTieredPricing_Basic() {
        // 场景：基础区间定价（无输出限制）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, 500000, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        // 测试匹配第一个区间
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(5000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);

        // 预期：50k/1000*0.01 + 5k/1000*0.03 = 0.5 + 0.15 = 0.65
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(0.65)), "区间1计算错误");

        // 测试匹配第二个区间
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(200000);
        usage2.setCompletion_tokens(10000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);

        // 预期：200k/1000*0.005 + 10k/1000*0.015 = 1.0 + 0.15 = 1.15
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(1.15)), "区间2计算错误");
    }

    @Test
    void testTieredPricing_Boundary() {
        // 场景：边界值测试（左开右闭）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, 500000, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        // 边界值：100000应匹配第一个区间
        CompletionResponse.TokenUsage usageAtBoundary = new CompletionResponse.TokenUsage();
        usageAtBoundary.setPrompt_tokens(100000);
        usageAtBoundary.setCompletion_tokens(1000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usageAtBoundary);

        // 预期：使用tier1价格 100k/1000*0.01 + 1k/1000*0.03 = 1.0 + 0.03 = 1.03
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(1.03)), "边界值应匹配第一个区间");

        // 100001应匹配第二个区间
        CompletionResponse.TokenUsage usageAfterBoundary = new CompletionResponse.TokenUsage();
        usageAfterBoundary.setPrompt_tokens(100001);
        usageAfterBoundary.setCompletion_tokens(1000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usageAfterBoundary);

        // 预期：使用tier2价格 100001/1000*0.005 + 1k/1000*0.015 = 0.500005 + 0.015 ≈ 0.515
        assertTrue(cost2.compareTo(BigDecimal.valueOf(0.514)) > 0
                && cost2.compareTo(BigDecimal.valueOf(0.516)) < 0, "100001应匹配第二个区间");
    }

    @Test
    void testTieredPricing_TwoDimensional() {
        // 场景：二维区间定价（输入+输出）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        // 低输入 + 短输出
        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 4000, 0.01, 0.06);
        // 低输入 + 长输出
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 4000, 16000, 0.01, 0.03);
        // 高输入
        CompletionPriceInfo.Tier tier3 = createTier(100000, 500000, null, null, 0.005, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3));

        // 测试：低输入+短输出
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(2000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);

        // 预期：匹配tier1，50k/1000*0.01 + 2k/1000*0.06 = 0.5 + 0.12 = 0.62
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(0.62)), "低输入+短输出计算错误");

        // 测试：低输入+长输出
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(50000);
        usage2.setCompletion_tokens(8000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);

        // 预期：匹配tier2，50k/1000*0.01 + 8k/1000*0.03 = 0.5 + 0.24 = 0.74
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(0.74)), "低输入+长输出计算错误");

        // 测试：高输入
        CompletionResponse.TokenUsage usage3 = new CompletionResponse.TokenUsage();
        usage3.setPrompt_tokens(200000);
        usage3.setCompletion_tokens(2000);

        BigDecimal cost3 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage3);

        // 预期：匹配tier3，200k/1000*0.005 + 2k/1000*0.015 = 1.0 + 0.03 = 1.03
        assertEquals(0, cost3.compareTo(BigDecimal.valueOf(1.03)), "高输入计算错误");
    }

    @Test
    void testTieredPricing_WithCache() {
        // 场景：区间定价 + 缓存token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier = createTier(0, 100000, null, null, 0.01, 0.03);
        tier.setCachedReadPrice(BigDecimal.valueOf(0.001));
        tier.setCachedCreationPrice(BigDecimal.valueOf(0.0125));
        priceInfo.setTiers(Arrays.asList(
                tier,
                createTier(100000, 500000, null, null, 0.005, 0.015)
        ));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(3000);
        inputDetail.setCache_creation_tokens(2000);
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：使用tier价格
        // 普通输入：(10000-3000-2000)/1000 * 0.01 = 0.05
        // 缓存读取：3000/1000 * 0.001 = 0.003
        // 缓存创建：2000/1000 * 0.0125 = 0.025
        // 输出：5000/1000 * 0.03 = 0.15
        // 总计：0.228
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.228)), "区间定价+缓存计算错误");
    }

    @Test
    void testTieredPricing_NoMatchingTier() {
        // 场景：无法匹配任何区间 → 抛出异常
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, 500000, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        // 超出最大范围
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(600000); // 超出500000
        usage.setCompletion_tokens(1000);

        String priceJson = JacksonUtils.serialize(priceInfo);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            CostCalculator.calculate("/v1/chat/completions", priceJson, usage);
        });

        assertTrue(exception.getMessage().contains("No matching price tier found"),
                "应该抛出无法匹配区间的异常");
    }

    @Test
    void testTieredPricing_OutputDimensionMismatch() {
        // 场景：输入匹配但输出不匹配
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        // 只定义短输出和长输出，没有覆盖所有输出范围
        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 4000, 0.01, 0.06);
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 4000, 8000, 0.01, 0.03);
        // 缺少 (8000, 16000] 的覆盖
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(50000);  // 输入匹配
        usage.setCompletion_tokens(10000); // 输出不在任何区间

        String priceJson = JacksonUtils.serialize(priceInfo);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            CostCalculator.calculate("/v1/chat/completions", priceJson, usage);
        });

        assertTrue(exception.getMessage().contains("No matching price tier found"),
                "输出维度不匹配应抛出异常");
    }

    @Test
    void testFixedPricing_ZeroTokens() {
        // 场景：零token消耗
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(0);
        usage.setCompletion_tokens(0);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        assertEquals(0, cost.compareTo(BigDecimal.ZERO), "零token应该零费用");
    }

    @Test
    void testFixedPricing_OnlyInput() {
        // 场景：只有输入，无输出
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(0);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：10k/1000*0.01 = 0.1
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.1)), "只有输入token计算错误");
    }

    @Test
    void testFixedPricing_OnlyOutput() {
        // 场景：只有输出，无输入
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(0);
        usage.setCompletion_tokens(5000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：5k/1000*0.03 = 0.15
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.15)), "只有输出token计算错误");
    }

    @Test
    void testFixedPricing_AllCacheHit() {
        // 场景：所有输入都命中缓存
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(10000); // 全部命中缓存
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 缓存读取：10k/1000 * 0.001 = 0.01
        // 输出：5k/1000 * 0.03 = 0.15
        // 总计：0.16
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.16)), "全缓存命中计算错误");
    }

    @Test
    void testFixedPricing_CacheCreationOnly() {
        // 场景：只有缓存创建，无缓存命中
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setCachedCreation(BigDecimal.valueOf(0.0125));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCache_creation_tokens(4000); // 只有缓存创建
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通输入：6k/1000 * 0.01 = 0.06
        // 缓存创建：4k/1000 * 0.0125 = 0.05
        // 输出：5k/1000 * 0.03 = 0.15
        // 总计：0.26
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.26)), "只有缓存创建计算错误");
    }

    @Test
    void testFixedPricing_ImageOnly() {
        // 场景：只有图片token，无文本输入
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);
        usage.setCompletion_tokens(3000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(5000); // 全部是图片token
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 图片输入：5k/1000 * 0.02 = 0.1
        // 输出：3k/1000 * 0.03 = 0.09
        // 总计：0.19
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.19)), "纯图片输入计算错误");
    }

    @Test
    void testFixedPricing_AllTokenTypes() {
        // 场景：混合所有类型token（文本+图片+缓存创建+缓存命中）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));
        priceInfo.setCachedCreation(BigDecimal.valueOf(0.0125));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(20000); // 总输入20k
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(5000);           // 5k图片
        inputDetail.setCached_tokens(3000);          // 3k缓存命中
        inputDetail.setCache_creation_tokens(2000);  // 2k缓存创建
        // 剩余10k普通文本
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通文本：10k/1000 * 0.01 = 0.1
        // 图片：5k/1000 * 0.02 = 0.1
        // 缓存命中：3k/1000 * 0.001 = 0.003
        // 缓存创建：2k/1000 * 0.0125 = 0.025
        // 输出：5k/1000 * 0.03 = 0.15
        // 总计：0.378
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.378)), "混合所有token类型计算错误");
    }

    @Test
    void testFixedPricing_OutputWithImage() {
        // 场景：输出也包含图片token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageOutput(BigDecimal.valueOf(0.04)); // 输出图片更贵

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(6000);

        CompletionResponse.TokensDetail outputDetail = new CompletionResponse.TokensDetail();
        outputDetail.setImage_tokens(2000); // 输出中有2k图片token
        usage.setCompletion_tokens_details(outputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 输入：10k/1000 * 0.01 = 0.1
        // 普通输出：4k/1000 * 0.03 = 0.12
        // 图片输出：2k/1000 * 0.04 = 0.08
        // 总计：0.3
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.3)), "输出包含图片token计算错误");
    }

    // ================== 区间定价边界测试 ==================

    @Test
    void testTieredPricing_FirstBoundaryAtZero() {
        // 场景：第一个区间包含0（特殊情况：0应该被包含）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, 500000, null, null, 0.005, 0.015)));

        // 测试0 token
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(0);
        usage.setCompletion_tokens(1000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：0输入+1k输出 = 0 + 1k/1000*0.03 = 0.03
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.03)), "0 token应匹配第一区间");
    }

    @Test
    void testTieredPricing_ExactBoundaryMatch() {
        // 场景：精确命中多个边界值
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 50000, null, null, 0.02, 0.06);
        CompletionPriceInfo.Tier tier2 = createTier(50000, 100000, null, null, 0.015, 0.045);
        CompletionPriceInfo.Tier tier3 = createTier(100000, 500000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3));

        // 测试50000边界（应匹配tier1）
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(1000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(1.06)), "50000应匹配tier1");

        // 测试100000边界（应匹配tier2）
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(100000);
        usage2.setCompletion_tokens(1000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(1.545)), "100000应匹配tier2");
    }

    @Test
    void testTieredPricing_TwoDimensional_OutputBoundary() {
        // 场景：二维区间的输出边界测试
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 4000, 0.01, 0.06);
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 4000, 16000, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        // 测试输出边界：4000（应匹配tier1）
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(4000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);
        // 预期：50k/1000*0.01 + 4k/1000*0.06 = 0.5 + 0.24 = 0.74
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(0.74)), "输出4000应匹配tier1");

        // 测试输出边界+1：4001（应匹配tier2）
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(50000);
        usage2.setCompletion_tokens(4001);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);
        // 预期：50k/1000*0.01 + 4001/1000*0.03 ≈ 0.5 + 0.12003 ≈ 0.62003
        assertTrue(cost2.compareTo(BigDecimal.valueOf(0.619)) > 0
                && cost2.compareTo(BigDecimal.valueOf(0.621)) < 0, "输出4001应匹配tier2");
    }

    @Test
    void testTieredPricing_WithAllTokenTypes() {
        // 场景：区间定价 + 所有token类型
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier = createTier(0, 100000, null, null, 0.01, 0.03);
        tier.setImageInputPrice(BigDecimal.valueOf(0.02));
        tier.setImageOutputPrice(BigDecimal.valueOf(0.04));
        tier.setCachedReadPrice(BigDecimal.valueOf(0.001));
        tier.setCachedCreationPrice(BigDecimal.valueOf(0.0125));

        priceInfo.setTiers(Arrays.asList(tier, createTier(100000, 500000, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(20000);
        usage.setCompletion_tokens(6000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(5000);
        inputDetail.setCached_tokens(3000);
        inputDetail.setCache_creation_tokens(2000);
        usage.setPrompt_tokens_details(inputDetail);

        CompletionResponse.TokensDetail outputDetail = new CompletionResponse.TokensDetail();
        outputDetail.setImage_tokens(2000);
        usage.setCompletion_tokens_details(outputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通输入：10k/1000 * 0.01 = 0.1
        // 图片输入：5k/1000 * 0.02 = 0.1
        // 缓存命中：3k/1000 * 0.001 = 0.003
        // 缓存创建：2k/1000 * 0.0125 = 0.025
        // 普通输出：4k/1000 * 0.03 = 0.12
        // 图片输出：2k/1000 * 0.04 = 0.08
        // 总计：0.428
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.428)), "区间定价+所有token类型计算错误");
    }

    @Test
    void testTieredPricing_MinimalTokens() {
        // 场景：极小token数（测试除以1000的精度）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, 500000, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1);  // 1个输入token
        usage.setCompletion_tokens(1); // 1个输出token

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：1/1000*0.01 + 1/1000*0.03 = 0.00001 + 0.00003 = 0.00004
        assertTrue(cost.compareTo(BigDecimal.valueOf(0.00003)) > 0
                && cost.compareTo(BigDecimal.valueOf(0.00005)) < 0, "极小token数计算精度");
    }

    @Test
    void testTieredPricing_LargeNumbers() {
        // 场景：超大token数
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, 10000000, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000000);  // 500万
        usage.setCompletion_tokens(1000000); // 100万

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：5000k/1000*0.005 + 1000k/1000*0.015 = 25 + 15 = 40
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(40.0)), "超大token数计算错误");
    }

    // ================== 异常和边界情况 ==================

    @Test
    void testTieredPricing_InputMatchMultipleOutputRanges() {
        // 场景：输入匹配单个区间，但有多个输出子区间
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        // 同一输入区间，不同输出区间不同价格
        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 2000, 0.01, 0.10);    // 短输出贵
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 2000, 8000, 0.01, 0.05);  // 中输出
        CompletionPriceInfo.Tier tier3 = createTier(0, 100000, 8000, 16000, 0.01, 0.03); // 长输出便宜
        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3));

        // 测试每个输出区间
        String priceJson = JacksonUtils.serialize(priceInfo);

        // 短输出：1000 tokens
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(1000);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(0.6)), "短输出价格");

        // 中输出：5000 tokens
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(50000);
        usage2.setCompletion_tokens(5000);
        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(0.75)), "中输出价格");

        // 长输出：10000 tokens
        CompletionResponse.TokenUsage usage3 = new CompletionResponse.TokenUsage();
        usage3.setPrompt_tokens(50000);
        usage3.setCompletion_tokens(10000);
        BigDecimal cost3 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage3);
        assertEquals(0, cost3.compareTo(BigDecimal.valueOf(0.8)), "长输出价格");
    }

    @Test
    void testTieredPricing_OnlyOutputDimension() {
        // 场景：只有输出维度的区间划分（输入维度只有一个大区间）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 1000000, 0, 4000, 0.01, 0.06);
        CompletionPriceInfo.Tier tier2 = createTier(0, 1000000, 4000, 16000, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(500000); // 大量输入
        usage.setCompletion_tokens(2000); // 少量输出

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：500k/1000*0.01 + 2k/1000*0.06 = 5 + 0.12 = 5.12
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(5.12)), "单输入区间+多输出区间");
    }

    @Test
    void testFixedPricing_VeryHighPrecision() {
        // 场景：高精度价格计算
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(new BigDecimal("0.00123456"));
        priceInfo.setOutput(new BigDecimal("0.00789012"));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(12345);
        usage.setCompletion_tokens(6789);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 验证精度保持
        assertTrue(cost.scale() >= 6, "应保持高精度");
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0, "费用应大于0");
    }

    @Test
    void testTieredPricing_LastTierUnbounded() {
        // 场景：最后一个输入区间max=null（无上限）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.005, 0.015); // max=null
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        // 测试超大输入值
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000000); // 1000万
        usage.setCompletion_tokens(5000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：10000k/1000*0.005 + 5k/1000*0.015 = 50 + 0.075 = 50.075
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(50.075)), "最后区间无上限应能匹配超大值");
    }

    @Test
    void testTieredPricing_LastOutputTierUnbounded() {
        // 场景：输出区间最后一个max=null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 4000, 0.01, 0.06);
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 4000, null, 0.01, 0.03); // 输出max=null
        CompletionPriceInfo.Tier tier3 = createTier(100000, null, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3));

        // 测试超大输出值
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(50000);
        usage.setCompletion_tokens(1000000); // 100万输出

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：50k/1000*0.01 + 1000k/1000*0.03 = 0.5 + 30 = 30.5
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(30.5)), "输出区间无上限应能匹配超大输出");
    }

    @Test
    void testTieredPricing_MultipleInputRanges_LastOutputUnbounded() {
        // 场景：每个输入区间的最后一个输出子区间max=null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        // 第一个输入区间
        CompletionPriceInfo.Tier tier1 = createTier(0, 50000, 0, 2000, 0.02, 0.08);
        CompletionPriceInfo.Tier tier2 = createTier(0, 50000, 2000, null, 0.02, 0.04); // 输出无上限
        // 第二个输入区间
        CompletionPriceInfo.Tier tier3 = createTier(50000, 100000, 0, 5000, 0.01, 0.05);
        CompletionPriceInfo.Tier tier4 = createTier(50000, 100000, 5000, null, 0.01, 0.025); // 输出无上限
        // 第三个输入区间
        CompletionPriceInfo.Tier tier5 = createTier(100000, null, null, null, 0.005, 0.015);

        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3, tier4, tier5));

        // 测试第一个输入区间，大输出
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(30000);
        usage1.setCompletion_tokens(50000); // 大输出

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);

        // 预期：30k/1000*0.02 + 50k/1000*0.04 = 0.6 + 2.0 = 2.6
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(2.6)), "第一输入区间+大输出");

        // 测试第二个输入区间，大输出
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(70000);
        usage2.setCompletion_tokens(30000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);

        // 预期：70k/1000*0.01 + 30k/1000*0.025 = 0.7 + 0.75 = 1.45
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(1.45)), "第二输入区间+大输出");
    }

    // ================== 补充：缓存相关边界测试 ==================

    @Test
    void testFixedPricing_CacheExceedsTotal() {
        // 场景：缓存token数之和超过总输入（异常情况，但应该能处理）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));
        priceInfo.setCachedCreation(BigDecimal.valueOf(0.0125));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(7000);          // 缓存命中
        inputDetail.setCache_creation_tokens(5000);  // 缓存创建
        // 总和12000 > 10000（异常情况）
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 应该能正常计算，普通输入为负数会被当作0处理
        // 缓存命中：7k/1000*0.001 = 0.007
        // 缓存创建：5k/1000*0.0125 = 0.0625
        // 输出：5k/1000*0.03 = 0.15
        assertTrue(cost.compareTo(BigDecimal.ZERO) >= 0, "即使缓存超出，费用不应为负");
    }

    @Test
    void testFixedPricing_OnlyCacheNoNormalInput() {
        // 场景：没有普通输入，全是缓存（缓存+创建=总输入）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));
        priceInfo.setCachedCreation(BigDecimal.valueOf(0.0125));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(6000);
        inputDetail.setCache_creation_tokens(4000);
        // 总和正好等于10000
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通输入：0
        // 缓存命中：6k/1000*0.001 = 0.006
        // 缓存创建：4k/1000*0.0125 = 0.05
        // 输出：5k/1000*0.03 = 0.15
        // 总计：0.206
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.206)), "全缓存无普通输入");
    }

    @Test
    void testTieredPricing_CacheWithoutPrice() {
        // 场景：有缓存token，但tier没有设置缓存价格（应该忽略缓存token）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier = createTier(0, 100000, null, null, 0.01, 0.03);
        // 不设置缓存价格
        priceInfo.setTiers(Arrays.asList(tier, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setCached_tokens(3000);
        inputDetail.setCache_creation_tokens(2000);
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：缓存token应该被当作普通token计算
        // 10k/1000*0.01 + 5k/1000*0.03 = 0.1 + 0.15 = 0.25
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.25)), "无缓存价格应按普通token计算");
    }

    // ================== 补充：图片相关边界测试 ==================

    @Test
    void testFixedPricing_ImageExceedsTotal() {
        // 场景：图片token超过总token（异常情况）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);
        usage.setCompletion_tokens(3000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(8000); // 超过总输入5000
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 应该能正常计算
        assertTrue(cost.compareTo(BigDecimal.ZERO) >= 0, "图片超出不应导致负费用");
    }

    @Test
    void testFixedPricing_ImageWithoutPrice() {
        // 场景：有图片token，但没有设置图片价格
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        // 不设置imageInput价格

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(4000);
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：图片token应该按普通input价格计算
        // 10k/1000*0.01 + 5k/1000*0.03 = 0.1 + 0.15 = 0.25
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.25)), "无图片价格应按普通token计算");
    }

    @Test
    void testFixedPricing_BothInputAndOutputImage() {
        // 场景：输入输出都有图片token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));
        priceInfo.setImageOutput(BigDecimal.valueOf(0.05));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(8000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(3000);
        usage.setPrompt_tokens_details(inputDetail);

        CompletionResponse.TokensDetail outputDetail = new CompletionResponse.TokensDetail();
        outputDetail.setImage_tokens(2000);
        usage.setCompletion_tokens_details(outputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：
        // 普通输入：7k/1000*0.01 = 0.07
        // 图片输入：3k/1000*0.02 = 0.06
        // 普通输出：6k/1000*0.03 = 0.18
        // 图片输出：2k/1000*0.05 = 0.1
        // 总计：0.41
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.41)), "输入输出都有图片");
    }

    @Test
    void testTieredPricing_ImageWithoutPrice() {
        // 场景：区间定价，有图片token但没设置图片价格
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier = createTier(0, 100000, null, null, 0.01, 0.03);
        // 不设置图片价格
        priceInfo.setTiers(Arrays.asList(tier, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(50000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(10000);
        usage.setPrompt_tokens_details(inputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：图片按普通价格
        // 50k/1000*0.01 + 5k/1000*0.03 = 0.5 + 0.15 = 0.65
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.65)), "区间定价无图片价格应按普通价格");
    }

    // ================== 补充：混合复杂场景 ==================

    @Test
    void testTieredPricing_AllTokenTypes_MultipleRanges() {
        // 场景：复杂区间+所有token类型
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        // 第一个输入区间：有输出子区间
        CompletionPriceInfo.Tier tier1 = createTier(0, 50000, 0, 3000, 0.02, 0.08);
        tier1.setImageInputPrice(BigDecimal.valueOf(0.04));
        tier1.setImageOutputPrice(BigDecimal.valueOf(0.12));
        tier1.setCachedReadPrice(BigDecimal.valueOf(0.002));
        tier1.setCachedCreationPrice(BigDecimal.valueOf(0.025));

        CompletionPriceInfo.Tier tier2 = createTier(0, 50000, 3000, null, 0.02, 0.04);
        tier2.setImageInputPrice(BigDecimal.valueOf(0.04));
        tier2.setImageOutputPrice(BigDecimal.valueOf(0.06));
        tier2.setCachedReadPrice(BigDecimal.valueOf(0.002));
        tier2.setCachedCreationPrice(BigDecimal.valueOf(0.025));

        // 第二个输入区间
        CompletionPriceInfo.Tier tier3 = createTier(50000, null, null, null, 0.01, 0.03);
        tier3.setImageInputPrice(BigDecimal.valueOf(0.02));
        tier3.setImageOutputPrice(BigDecimal.valueOf(0.05));
        tier3.setCachedReadPrice(BigDecimal.valueOf(0.001));
        tier3.setCachedCreationPrice(BigDecimal.valueOf(0.0125));

        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3));

        // 测试第一个输入区间+短输出+所有token类型
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(30000);
        usage.setCompletion_tokens(2000);

        CompletionResponse.TokensDetail inputDetail = new CompletionResponse.TokensDetail();
        inputDetail.setImage_tokens(5000);
        inputDetail.setCached_tokens(3000);
        inputDetail.setCache_creation_tokens(2000);
        usage.setPrompt_tokens_details(inputDetail);

        CompletionResponse.TokensDetail outputDetail = new CompletionResponse.TokensDetail();
        outputDetail.setImage_tokens(500);
        usage.setCompletion_tokens_details(outputDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：匹配tier1
        // 普通输入：20k/1000*0.02 = 0.4
        // 图片输入：5k/1000*0.04 = 0.2
        // 缓存命中：3k/1000*0.002 = 0.006
        // 缓存创建：2k/1000*0.025 = 0.05
        // 普通输出：1.5k/1000*0.08 = 0.12
        // 图片输出：0.5k/1000*0.12 = 0.06
        // 总计：0.836
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.836)), "复杂区间+所有token类型");
    }

    @Test
    void testTieredPricing_ZeroInputWithOutput() {
        // 场景：0输入token，只有输出
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(0);
        usage.setCompletion_tokens(5000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：0输入+5k输出 = 5k/1000*0.03 = 0.15
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.15)), "0输入应匹配第一区间");
    }

    @Test
    void testTieredPricing_InputBoundaryWithZeroOutput() {
        // 场景：输入在边界上，输出为0
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier2 = createTier(100000, null, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(100000); // 边界值
        usage.setCompletion_tokens(0);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：100k/1000*0.01 + 0 = 1.0
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(1.0)), "边界输入+0输出");
    }

    // ================== 补充：精度和舍入测试 ==================

    @Test
    void testFixedPricing_RoundingPrecision() {
        // 场景：测试除法精度和舍入
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(new BigDecimal("0.003333333"));
        priceInfo.setOutput(new BigDecimal("0.006666667"));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(3333);
        usage.setCompletion_tokens(6667);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 验证计算不会丢失精度
        assertTrue(cost.scale() >= 6, "应保持足够精度");
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0, "费用应大于0");
    }

    @Test
    void testTieredPricing_NonDivisibleBy1000() {
        // 场景：token数不能被1000整除
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(12345);
        usage.setCompletion_tokens(67890);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：12345/1000*0.01 + 67890/1000*0.03 = 0.12345 + 2.0367 = 2.16015
        BigDecimal expected = new BigDecimal("12345").divide(new BigDecimal("1000"), 10, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("0.01"))
                .add(new BigDecimal("67890").divide(new BigDecimal("1000"), 10, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("0.03")));

        assertTrue(cost.subtract(expected).abs().compareTo(new BigDecimal("0.00001")) < 0, "精度计算");
    }

    // ================== 补充：NULL和异常处理 ==================

    @Test
    void testCalculate_NullUsage() {
        // 场景：usage为null
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));

        String priceJson = JacksonUtils.serialize(priceInfo);

        assertThrows(Exception.class, () -> {
            CostCalculator.calculate("/v1/chat/completions", priceJson, null);
        }, "null usage应该抛出异常");
    }

    @Test
    void testCalculate_InvalidPriceJson() {
        // 场景：无效的价格JSON
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1000);
        usage.setCompletion_tokens(500);

        assertThrows(Exception.class, () -> {
            CostCalculator.calculate("/v1/chat/completions", "invalid json", usage);
        }, "无效JSON应该抛出异常");
    }

    @Test
    void testCalculate_EmptyPriceJson() {
        // 场景：空的价格JSON
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1000);
        usage.setCompletion_tokens(500);

        assertThrows(Exception.class, () -> {
            CostCalculator.calculate("/v1/chat/completions", "", usage);
        }, "空JSON应该抛出异常");
    }

    @Test
    void testFixedPricing_NullTokenDetails() {
        // 场景：TokenDetail为null（应该当作没有特殊token）
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.FIXED);
        priceInfo.setInput(BigDecimal.valueOf(0.01));
        priceInfo.setOutput(BigDecimal.valueOf(0.03));
        priceInfo.setImageInput(BigDecimal.valueOf(0.02));
        priceInfo.setCachedRead(BigDecimal.valueOf(0.001));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);
        usage.setPrompt_tokens_details(null); // null details
        usage.setCompletion_tokens_details(null);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：应该按普通token计算
        // 10k/1000*0.01 + 5k/1000*0.03 = 0.1 + 0.15 = 0.25
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.25)), "null details应按普通token计算");
    }

    @Test
    void testTieredPricing_EmptyTokenDetails() {
        // 场景：TokenDetail存在但所有字段为null/0
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10000);
        usage.setCompletion_tokens(5000);

        CompletionResponse.TokensDetail emptyDetail = new CompletionResponse.TokensDetail();
        // 所有字段都是null
        usage.setPrompt_tokens_details(emptyDetail);
        usage.setCompletion_tokens_details(emptyDetail);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：按普通token计算
        // 10k/1000*0.01 + 5k/1000*0.03 = 0.1 + 0.15 = 0.25
        assertEquals(0, cost.compareTo(BigDecimal.valueOf(0.25)), "空details应按普通token计算");
    }

    // ================== 补充：极端组合测试 ==================

    @Test
    void testTieredPricing_SingleTokenInput() {
        // 场景：只有1个输入token
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, null, null, 0.01, 0.03);
        priceInfo.setTiers(Arrays.asList(tier1, createTier(100000, null, null, null, 0.005, 0.015)));

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1);
        usage.setCompletion_tokens(0);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost = CostCalculator.calculate("/v1/chat/completions", priceJson, usage);

        // 预期：1/1000*0.01 = 0.00001
        assertTrue(cost.compareTo(new BigDecimal("0.00001")) >= 0
                && cost.compareTo(new BigDecimal("0.00002")) < 0, "单个token精度");
    }

    @Test
    void testTieredPricing_OutputBoundaryExactMatch_MultipleRanges() {
        // 场景：输出正好在两个区间的连接点
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        priceInfo.setMode(CompletionPriceInfo.PricingMode.TIERED);

        CompletionPriceInfo.Tier tier1 = createTier(0, 100000, 0, 2000, 0.01, 0.10);
        CompletionPriceInfo.Tier tier2 = createTier(0, 100000, 2000, 8000, 0.01, 0.05);
        CompletionPriceInfo.Tier tier3 = createTier(0, 100000, 8000, null, 0.01, 0.03);
        CompletionPriceInfo.Tier tier4 = createTier(100000, null, null, null, 0.005, 0.015);
        priceInfo.setTiers(Arrays.asList(tier1, tier2, tier3, tier4));

        // 测试2000边界（应匹配tier1）
        CompletionResponse.TokenUsage usage1 = new CompletionResponse.TokenUsage();
        usage1.setPrompt_tokens(50000);
        usage1.setCompletion_tokens(2000);

        String priceJson = JacksonUtils.serialize(priceInfo);
        BigDecimal cost1 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage1);
        assertEquals(0, cost1.compareTo(BigDecimal.valueOf(0.7)), "2000应匹配tier1");

        // 测试8000边界（应匹配tier2）
        CompletionResponse.TokenUsage usage2 = new CompletionResponse.TokenUsage();
        usage2.setPrompt_tokens(50000);
        usage2.setCompletion_tokens(8000);

        BigDecimal cost2 = CostCalculator.calculate("/v1/chat/completions", priceJson, usage2);
        assertEquals(0, cost2.compareTo(BigDecimal.valueOf(0.9)), "8000应匹配tier2");
    }

    // ================== 辅助方法 ==================

    private CompletionPriceInfo.Tier createTier(int minInput, Integer maxInput,
            Integer minOutput, Integer maxOutput,
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
}
