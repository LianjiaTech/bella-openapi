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
 * 成本详细信息测试
 * 测试 CostDetails 的详细成本分解功能
 */
public class CompletionCostDetailsTest {

    /**
     * 测试场景1: 基本的输入输出成本详情
     */
    @Test
    public void testBasicCostDetails() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));    // 10分/千token
        range.setOutput(new BigDecimal("20"));   // 20分/千token
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);      // 5k prompt tokens
        usage.setCompletion_tokens(3000);  // 3k completion tokens

        CostDetails costDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 验证总成本
        BigDecimal expectedTotal = new BigDecimal("5").multiply(new BigDecimal("10"))
                .add(new BigDecimal("3").multiply(new BigDecimal("20")));
        assertEquals("总成本应该正确", 0, expectedTotal.compareTo(costDetails.getTotalCost()));

        // 验证输入明细
        assertNotNull("输入明细不应为null", costDetails.getInputDetails());
        assertEquals("应该有1个输入明细项", 1, costDetails.getInputDetails().size());

        CostDetails.CostDetailItem inputItem = costDetails.getInputDetails().get(0);
        assertEquals("输入类型应为 prompt_tokens", "prompt_tokens", inputItem.getType());
        assertEquals("输入token数应为5000", Integer.valueOf(5000), inputItem.getTokens());
        assertEquals("输入单价应为10", new BigDecimal("10"), inputItem.getUnitPrice());
        assertEquals("输入成本应为50", 0, new BigDecimal("50").compareTo(inputItem.getCost()));

        // 验证输出明细
        assertNotNull("输出明细不应为null", costDetails.getOutputDetails());
        assertEquals("应该有1个输出明细项", 1, costDetails.getOutputDetails().size());

        CostDetails.CostDetailItem outputItem = costDetails.getOutputDetails().get(0);
        assertEquals("输出类型应为 completion_tokens", "completion_tokens", outputItem.getType());
        assertEquals("输出token数应为3000", Integer.valueOf(3000), outputItem.getTokens());
        assertEquals("输出单价应为20", new BigDecimal("20"), outputItem.getUnitPrice());
        assertEquals("输出成本应为60", 0, new BigDecimal("60").compareTo(outputItem.getCost()));

        // 验证工具明细
        assertNull("工具明细应为null（未使用工具）", costDetails.getToolDetails());

        // 验证总和一致性
        BigDecimal detailsSum = inputItem.getCost().add(outputItem.getCost());
        assertEquals("明细总和应等于总成本", 0, costDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景2: 包含缓存和图片token的详细成本
     */
    @Test
    public void testCostDetailsWithCacheAndImage() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        range.setCachedRead(new BigDecimal("2"));        // 2分/千token
        range.setImageInput(new BigDecimal("100"));      // 100分/千token
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);       // 总输入: 5000
        usage.setCompletion_tokens(3000);   // 总输出: 3000

        // 设置输入token详情
        CompletionResponse.TokensDetail promptDetail = new CompletionResponse.TokensDetail();
        promptDetail.setCached_tokens(500);    // 500个缓存token
        promptDetail.setImage_tokens(1000);    // 1000个图片token
        usage.setPrompt_tokens_details(promptDetail);

        CostDetails costDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 验证输入明细数量
        assertNotNull("输入明细不应为null", costDetails.getInputDetails());
        assertEquals("应该有3个输入明细项", 3, costDetails.getInputDetails().size());

        // 查找各类型的明细
        CostDetails.CostDetailItem cachedItem = findDetailByType(costDetails.getInputDetails(), "cached_tokens");
        CostDetails.CostDetailItem imageItem = findDetailByType(costDetails.getInputDetails(), "image_tokens");
        CostDetails.CostDetailItem promptItem = findDetailByType(costDetails.getInputDetails(), "prompt_tokens");

        // 验证缓存token明细
        assertNotNull("应该有缓存token明细", cachedItem);
        assertEquals("缓存token数应为500", Integer.valueOf(500), cachedItem.getTokens());
        assertEquals("缓存单价应为2", new BigDecimal("2"), cachedItem.getUnitPrice());
        assertEquals("缓存成本应为1", 0, new BigDecimal("1").compareTo(cachedItem.getCost()));

        // 验证图片token明细
        assertNotNull("应该有图片token明细", imageItem);
        assertEquals("图片token数应为1000", Integer.valueOf(1000), imageItem.getTokens());
        assertEquals("图片单价应为100", new BigDecimal("100"), imageItem.getUnitPrice());
        assertEquals("图片成本应为100", 0, new BigDecimal("100").compareTo(imageItem.getCost()));

        // 验证普通prompt token明细（应该扣除缓存和图片token）
        assertNotNull("应该有普通prompt明细", promptItem);
        assertEquals("普通prompt token数应为3500 (5000-500-1000)", Integer.valueOf(3500), promptItem.getTokens());
        assertEquals("普通prompt单价应为10", new BigDecimal("10"), promptItem.getUnitPrice());
        assertEquals("普通prompt成本应为35", 0, new BigDecimal("35").compareTo(promptItem.getCost()));

        // 验证输出明细
        assertNotNull("输出明细不应为null", costDetails.getOutputDetails());
        assertEquals("应该有1个输出明细项", 1, costDetails.getOutputDetails().size());

        // 验证总和一致性
        BigDecimal inputSum = cachedItem.getCost().add(imageItem.getCost()).add(promptItem.getCost());
        BigDecimal outputSum = costDetails.getOutputDetails().get(0).getCost();
        BigDecimal detailsSum = inputSum.add(outputSum);
        assertEquals("明细总和应等于总成本", 0, costDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景3: 零成本明细不应出现在列表中
     */
    @Test
    public void testZeroCostDetailsNotIncluded() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10"));
        range.setOutput(new BigDecimal("20"));
        range.setCachedRead(new BigDecimal("2"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);
        usage.setCompletion_tokens(0);  // 零输出token

        // 设置输入token详情（没有缓存token）
        CompletionResponse.TokensDetail promptDetail = new CompletionResponse.TokensDetail();
        promptDetail.setCached_tokens(0);  // 零缓存token
        usage.setPrompt_tokens_details(promptDetail);

        CostDetails costDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 验证输入明细：只有prompt_tokens，没有cached_tokens（因为为0）
        assertNotNull("输入明细不应为null", costDetails.getInputDetails());
        assertEquals("应该只有1个输入明细项（prompt_tokens）", 1, costDetails.getInputDetails().size());
        assertEquals("唯一的输入明细应为prompt_tokens", "prompt_tokens",
                costDetails.getInputDetails().get(0).getType());

        // 验证输出明细：为null（因为completion_tokens为0）
        assertNull("输出明细应为null（零输出token）", costDetails.getOutputDetails());
    }

    /**
     * 测试场景4: 所有明细项的成本总和等于总成本
     */
    @Test
    public void testTotalCostEqualsDetailsSum() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10.123"));
        range.setOutput(new BigDecimal("20.456"));
        range.setCachedRead(new BigDecimal("2.789"));
        range.setImageInput(new BigDecimal("100.111"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(8000);
        usage.setCompletion_tokens(4000);

        CompletionResponse.TokensDetail promptDetail = new CompletionResponse.TokensDetail();
        promptDetail.setCached_tokens(1000);
        promptDetail.setImage_tokens(2000);
        usage.setPrompt_tokens_details(promptDetail);

        CostDetails costDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 计算所有明细项的总和
        BigDecimal detailsSum = BigDecimal.ZERO;

        if(costDetails.getInputDetails() != null) {
            for (CostDetails.CostDetailItem item : costDetails.getInputDetails()) {
                detailsSum = detailsSum.add(item.getCost());
            }
        }

        if(costDetails.getOutputDetails() != null) {
            for (CostDetails.CostDetailItem item : costDetails.getOutputDetails()) {
                detailsSum = detailsSum.add(item.getCost());
            }
        }

        // 验证总和一致性（允许小的精度误差）
        BigDecimal diff = costDetails.getTotalCost().subtract(detailsSum).abs();
        assertTrue("明细总和应等于总成本（误差小于0.001），实际差值: " + diff,
                diff.compareTo(new BigDecimal("0.001")) < 0);
    }

    /**
     * 测试场景5: 空的明细列表应返回null而非空列表
     */
    @Test
    public void testEmptyDetailsListShouldBeNull() {
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
        usage.setPrompt_tokens(1000);
        usage.setCompletion_tokens(0);  // 零输出

        CostDetails costDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 输入明细应存在
        assertNotNull("输入明细应存在", costDetails.getInputDetails());
        assertFalse("输入明细不应为空列表", costDetails.getInputDetails().isEmpty());

        // 输出明细应为null（而非空列表）
        assertNull("输出明细应为null而非空列表", costDetails.getOutputDetails());

        // 工具明细应为null
        assertNull("工具明细应为null", costDetails.getToolDetails());
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