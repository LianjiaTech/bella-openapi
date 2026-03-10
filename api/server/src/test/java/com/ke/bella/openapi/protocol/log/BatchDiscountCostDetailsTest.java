package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesPriceInfo;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 批量折扣成本详细信息测试
 * 测试批量折扣应用到 CostDetails 的正确性
 */
public class BatchDiscountCostDetailsTest {

    /**
     * 测试场景1: 批量折扣应用到所有成本明细
     */
    @Test
    public void testBatchDiscountAppliedToAllDetails() {
        // 准备价格信息
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

        // 准备使用信息
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(5000);      // 5k tokens
        usage.setCompletion_tokens(3000);  // 3k tokens

        // 计算原始成本
        CostDetails originalDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 应用批量折扣（9折）
        BigDecimal discount = new BigDecimal("0.9");
        CostDetails discountedDetails = applyDiscount(originalDetails, discount);

        // 验证总成本应用了折扣
        BigDecimal expectedTotal = originalDetails.getTotalCost().multiply(discount);
        assertEquals("总成本应应用折扣", 0, expectedTotal.compareTo(discountedDetails.getTotalCost()));

        // 验证输入明细应用了折扣
        assertNotNull("输入明细不应为null", discountedDetails.getInputDetails());
        assertEquals("输入明细数量应相同", originalDetails.getInputDetails().size(),
                discountedDetails.getInputDetails().size());

        CostDetails.CostDetailItem originalInput = originalDetails.getInputDetails().get(0);
        CostDetails.CostDetailItem discountedInput = discountedDetails.getInputDetails().get(0);

        assertEquals("输入类型应相同", originalInput.getType(), discountedInput.getType());
        assertEquals("输入token数应相同", originalInput.getTokens(), discountedInput.getTokens());
        assertEquals("输入单价应相同", originalInput.getUnitPrice(), discountedInput.getUnitPrice());
        assertEquals("输入成本应应用折扣", 0,
                originalInput.getCost().multiply(discount).compareTo(discountedInput.getCost()));

        // 验证输出明细应用了折扣
        assertNotNull("输出明细不应为null", discountedDetails.getOutputDetails());
        CostDetails.CostDetailItem originalOutput = originalDetails.getOutputDetails().get(0);
        CostDetails.CostDetailItem discountedOutput = discountedDetails.getOutputDetails().get(0);

        assertEquals("输出类型应相同", originalOutput.getType(), discountedOutput.getType());
        assertEquals("输出token数应相同", originalOutput.getTokens(), discountedOutput.getTokens());
        assertEquals("输出单价应相同", originalOutput.getUnitPrice(), discountedOutput.getUnitPrice());
        assertEquals("输出成本应应用折扣", 0,
                originalOutput.getCost().multiply(discount).compareTo(discountedOutput.getCost()));

        // 验证折扣后明细总和等于总成本
        BigDecimal detailsSum = discountedInput.getCost().add(discountedOutput.getCost());
        assertEquals("折扣后明细总和应等于总成本", 0,
                discountedDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景2: 批量折扣应用到工具成本明细
     */
    @Test
    public void testBatchDiscountAppliedToToolDetails() {
        // 准备价格信息
        ResponsesPriceInfo.RangePrice rangePrice = new ResponsesPriceInfo.RangePrice();
        rangePrice.setMinToken(0);
        rangePrice.setMaxToken(Integer.MAX_VALUE);
        rangePrice.setInput(new BigDecimal("1.0"));
        rangePrice.setOutput(new BigDecimal("3.0"));

        ResponsesPriceInfo.Tier tier = new ResponsesPriceInfo.Tier();
        tier.setInputRangePrice(rangePrice);

        List<ResponsesPriceInfo.Tier> tiers = new ArrayList<>();
        tiers.add(tier);

        ResponsesPriceInfo priceInfo = new ResponsesPriceInfo();
        priceInfo.setTiers(tiers);

        Map<String, BigDecimal> toolPrices = new HashMap<>();
        toolPrices.put("web_search", new BigDecimal("0.5"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 准备使用信息
        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 2);
        usage.setTool_usage(toolUsage);

        // 计算原始成本
        CostDetails originalDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 应用批量折扣（8折）
        BigDecimal discount = new BigDecimal("0.8");
        CostDetails discountedDetails = applyDiscount(originalDetails, discount);

        // 验证工具明细应用了折扣
        assertNotNull("工具明细不应为null", discountedDetails.getToolDetails());
        assertEquals("工具明细数量应相同", originalDetails.getToolDetails().size(),
                discountedDetails.getToolDetails().size());

        CostDetails.ToolCostDetailItem originalTool = originalDetails.getToolDetails().get(0);
        CostDetails.ToolCostDetailItem discountedTool = discountedDetails.getToolDetails().get(0);

        assertEquals("工具名应相同", originalTool.getToolName(), discountedTool.getToolName());
        assertEquals("工具调用次数应相同", originalTool.getCallCount(), discountedTool.getCallCount());
        assertEquals("工具单价应相同", originalTool.getUnitPrice(), discountedTool.getUnitPrice());
        assertEquals("工具成本应应用折扣", 0,
                originalTool.getCost().multiply(discount).compareTo(discountedTool.getCost()));

        // 验证折扣后所有明细总和等于总成本
        BigDecimal tokenSum = discountedDetails.getInputDetails().get(0).getCost()
                .add(discountedDetails.getOutputDetails().get(0).getCost());
        BigDecimal toolSum = discountedTool.getCost();
        BigDecimal detailsSum = tokenSum.add(toolSum);

        assertEquals("折扣后明细总和应等于总成本", 0,
                discountedDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景3: 验证折扣数学正确性（先折扣再求和 = 先求和再折扣）
     */
    @Test
    public void testDiscountMathematicalEquivalence() {
        // 准备测试数据
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("10.123"));
        range.setOutput(new BigDecimal("20.456"));
        range.setCachedRead(new BigDecimal("2.789"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(8000);
        usage.setCompletion_tokens(4000);

        CompletionResponse.TokensDetail promptDetail = new CompletionResponse.TokensDetail();
        promptDetail.setCached_tokens(1000);
        usage.setPrompt_tokens_details(promptDetail);

        // 计算原始成本
        CostDetails originalDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);

        // 应用折扣
        BigDecimal discount = new BigDecimal("0.85");
        CostDetails discountedDetails = applyDiscount(originalDetails, discount);

        // 方法1：总成本 × 折扣
        BigDecimal method1 = originalDetails.getTotalCost().multiply(discount);

        // 方法2：每项明细折扣后求和
        BigDecimal method2 = BigDecimal.ZERO;
        if(discountedDetails.getInputDetails() != null) {
            for (CostDetails.CostDetailItem item : discountedDetails.getInputDetails()) {
                method2 = method2.add(item.getCost());
            }
        }
        if(discountedDetails.getOutputDetails() != null) {
            for (CostDetails.CostDetailItem item : discountedDetails.getOutputDetails()) {
                method2 = method2.add(item.getCost());
            }
        }

        // 验证两种方法结果一致
        assertEquals("先求和再折扣 应等于 先折扣再求和", 0, method1.compareTo(method2));
        assertEquals("折扣后总成本应等于明细总和", 0, discountedDetails.getTotalCost().compareTo(method2));
    }

    /**
     * 测试场景4: 不同折扣率的精度测试
     */
    @Test
    public void testDifferentDiscountRates() {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();

        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal("100"));
        range.setOutput(new BigDecimal("200"));
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(1000);
        usage.setCompletion_tokens(500);

        CostDetails originalDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);
        BigDecimal originalTotal = originalDetails.getTotalCost();

        // 测试不同折扣率
        BigDecimal[] discountRates = {
                new BigDecimal("0.5"),   // 5折
                new BigDecimal("0.75"),  // 7.5折
                new BigDecimal("0.9"),   // 9折
                new BigDecimal("0.95"),  // 9.5折
                new BigDecimal("1.0")    // 无折扣
        };

        for (BigDecimal discount : discountRates) {
            CostDetails discountedDetails = applyDiscount(originalDetails, discount);

            // 验证总成本
            BigDecimal expectedTotal = originalTotal.multiply(discount);
            assertEquals("折扣率" + discount + "的总成本应正确", 0,
                    expectedTotal.compareTo(discountedDetails.getTotalCost()));

            // 验证明细总和
            BigDecimal detailsSum = BigDecimal.ZERO;
            for (CostDetails.CostDetailItem item : discountedDetails.getInputDetails()) {
                detailsSum = detailsSum.add(item.getCost());
            }
            for (CostDetails.CostDetailItem item : discountedDetails.getOutputDetails()) {
                detailsSum = detailsSum.add(item.getCost());
            }

            assertEquals("折扣率" + discount + "的明细总和应等于总成本", 0,
                    discountedDetails.getTotalCost().compareTo(detailsSum));
        }
    }

    /**
     * 测试场景5: 折扣不改变 unitPrice 和 tokens/callCount
     */
    @Test
    public void testDiscountDoesNotChangeUnitPriceAndCounts() {
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
        usage.setPrompt_tokens(5000);
        usage.setCompletion_tokens(3000);

        CostDetails originalDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);
        CostDetails discountedDetails = applyDiscount(originalDetails, new BigDecimal("0.8"));

        // 验证 unitPrice 不变
        assertEquals("输入单价应保持不变",
                originalDetails.getInputDetails().get(0).getUnitPrice(),
                discountedDetails.getInputDetails().get(0).getUnitPrice());

        assertEquals("输出单价应保持不变",
                originalDetails.getOutputDetails().get(0).getUnitPrice(),
                discountedDetails.getOutputDetails().get(0).getUnitPrice());

        // 验证 tokens 不变
        assertEquals("输入token数应保持不变",
                originalDetails.getInputDetails().get(0).getTokens(),
                discountedDetails.getInputDetails().get(0).getTokens());

        assertEquals("输出token数应保持不变",
                originalDetails.getOutputDetails().get(0).getTokens(),
                discountedDetails.getOutputDetails().get(0).getTokens());

        // 验证只有 cost 改变
        assertNotEquals("输入成本应改变",
                originalDetails.getInputDetails().get(0).getCost(),
                discountedDetails.getInputDetails().get(0).getCost());

        assertNotEquals("输出成本应改变",
                originalDetails.getOutputDetails().get(0).getCost(),
                discountedDetails.getOutputDetails().get(0).getCost());
    }

    /**
     * 辅助方法：应用折扣（模拟 CostLogHandler.applyDiscount）
     */
    private CostDetails applyDiscount(CostDetails costDetails, BigDecimal discount) {
        if(costDetails == null) {
            return null;
        }

        return CostDetails.builder()
                .totalCost(costDetails.getTotalCost().multiply(discount))
                .inputDetails(applyDiscountToDetailItems(costDetails.getInputDetails(), discount))
                .outputDetails(applyDiscountToDetailItems(costDetails.getOutputDetails(), discount))
                .toolDetails(applyDiscountToToolItems(costDetails.getToolDetails(), discount))
                .build();
    }

    private List<CostDetails.CostDetailItem> applyDiscountToDetailItems(
            List<CostDetails.CostDetailItem> items, BigDecimal discountFactor) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        return items.stream()
                .map(item -> CostDetails.CostDetailItem.builder()
                        .type(item.getType())
                        .tokens(item.getTokens())
                        .unitPrice(item.getUnitPrice())
                        .cost(item.getCost().multiply(discountFactor))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<CostDetails.ToolCostDetailItem> applyDiscountToToolItems(
            List<CostDetails.ToolCostDetailItem> items, BigDecimal discount) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        return items.stream()
                .map(item -> CostDetails.ToolCostDetailItem.builder()
                        .toolName(item.getToolName())
                        .callCount(item.getCallCount())
                        .unitPrice(item.getUnitPrice())
                        .cost(item.getCost().multiply(discount))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}