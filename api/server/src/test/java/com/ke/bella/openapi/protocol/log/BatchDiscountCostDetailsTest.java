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

    private static final String PROMPT_TOKENS_KEY = "prompt_tokens";
    private static final String CACHED_TOKENS_KEY = "cached_tokens";
    private static final String COMPLETION_TOKENS_KEY = "completion_tokens";
    private static final String INPUT_TOKENS_KEY = "input_tokens";
    private static final String OUTPUT_TOKENS_KEY = "output_tokens";
    private static final String WEB_SEARCH_TOOL_KEY = "web_search";

    private String buildCompletionPriceJson(String input, String output) {
        return buildCompletionPriceJsonWithCache(input, output, null);
    }

    private String buildCompletionPriceJsonWithCache(String input, String output, String cachedRead) {
        CompletionPriceInfo priceInfo = new CompletionPriceInfo();
        List<CompletionPriceInfo.Tier> tiers = new ArrayList<>();
        CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
        CompletionPriceInfo.RangePrice range = new CompletionPriceInfo.RangePrice();
        range.setMinToken(0);
        range.setMaxToken(Integer.MAX_VALUE);
        range.setInput(new BigDecimal(input));
        range.setOutput(new BigDecimal(output));
        if(cachedRead != null) {
            range.setCachedRead(new BigDecimal(cachedRead));
        }
        tier.setInputRangePrice(range);
        tiers.add(tier);
        priceInfo.setTiers(tiers);
        return JacksonUtils.serialize(priceInfo);
    }

    private CompletionResponse.TokenUsage buildTokenUsage(int promptTokens, int completionTokens) {
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(promptTokens);
        usage.setCompletion_tokens(completionTokens);
        return usage;
    }

    /**
     * 测试场景1: 批量折扣应用到所有成本明细
     */
    @Test
    public void testBatchDiscountAppliedToAllDetails() {
        String priceInfoJson = buildCompletionPriceJson("10", "20");

        // 准备使用信息
        CompletionResponse.TokenUsage usage = buildTokenUsage(5000, 3000);

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

        CostDetails.CostDetailItem originalInput = originalDetails.getInputDetails().get(PROMPT_TOKENS_KEY);
        CostDetails.CostDetailItem discountedInput = discountedDetails.getInputDetails().get(PROMPT_TOKENS_KEY);

        assertNotNull("原始输入明细不应为null", originalInput);
        assertNotNull("折扣后输入明细不应为null", discountedInput);
        assertEquals("输入token数应相同", originalInput.getTokens(), discountedInput.getTokens());
        assertEquals("输入单价应相同", originalInput.getUnitPrice(), discountedInput.getUnitPrice());
        assertEquals("输入成本应应用折扣", 0,
                originalInput.getCost().multiply(discount).compareTo(discountedInput.getCost()));

        // 验证输出明细应用了折扣
        assertNotNull("输出明细不应为null", discountedDetails.getOutputDetails());
        CostDetails.CostDetailItem originalOutput = originalDetails.getOutputDetails().get(COMPLETION_TOKENS_KEY);
        CostDetails.CostDetailItem discountedOutput = discountedDetails.getOutputDetails().get(COMPLETION_TOKENS_KEY);

        assertNotNull("原始输出明细不应为null", originalOutput);
        assertNotNull("折扣后输出明细不应为null", discountedOutput);
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
        toolPrices.put(WEB_SEARCH_TOOL_KEY, new BigDecimal("0.5"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        // 准备使用信息
        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put(WEB_SEARCH_TOOL_KEY, 2);
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

        CostDetails.ToolCostDetailItem originalTool = originalDetails.getToolDetails().get(WEB_SEARCH_TOOL_KEY);
        CostDetails.ToolCostDetailItem discountedTool = discountedDetails.getToolDetails().get(WEB_SEARCH_TOOL_KEY);

        assertNotNull("原始工具明细不应为null", originalTool);
        assertNotNull("折扣后工具明细不应为null", discountedTool);
        assertEquals("工具调用次数应相同", originalTool.getCallCount(), discountedTool.getCallCount());
        assertEquals("工具单价应相同", originalTool.getUnitPrice(), discountedTool.getUnitPrice());
        assertEquals("工具成本应应用折扣", 0,
                originalTool.getCost().multiply(discount).compareTo(discountedTool.getCost()));

        // 验证折扣后所有明细总和等于总成本
        BigDecimal tokenSum = discountedDetails.getInputDetails().get(INPUT_TOKENS_KEY).getCost()
                .add(discountedDetails.getOutputDetails().get(OUTPUT_TOKENS_KEY).getCost());
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
        // 准备测试数据（含 cachedRead，需单独构建）
        String priceInfoJson = buildCompletionPriceJsonWithCache("10.123", "20.456", "2.789");

        CompletionResponse.TokenUsage usage = buildTokenUsage(8000, 4000);

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
            for (CostDetails.CostDetailItem item : discountedDetails.getInputDetails().values()) {
                method2 = method2.add(item.getCost());
            }
        }
        if(discountedDetails.getOutputDetails() != null) {
            for (CostDetails.CostDetailItem item : discountedDetails.getOutputDetails().values()) {
                method2 = method2.add(item.getCost());
            }
        }

        // 验证两种方法结果一致
        assertEquals("先求和再折扣 应等于 先折扣再求和", 0, method1.compareTo(method2));
        assertEquals("折扣后总成本应等于明细总和", 0, discountedDetails.getTotalCost().compareTo(method2));

        // 验证 cached_tokens 明细折扣正确
        CostDetails.CostDetailItem originalCached = originalDetails.getInputDetails().get(CACHED_TOKENS_KEY);
        CostDetails.CostDetailItem discountedCached = discountedDetails.getInputDetails().get(CACHED_TOKENS_KEY);
        assertNotNull("原始cached_tokens明细不应为null", originalCached);
        assertNotNull("折扣后cached_tokens明细不应为null", discountedCached);
        assertEquals("cached_tokens token数应不变", originalCached.getTokens(), discountedCached.getTokens());
        assertEquals("cached_tokens单价应不变", originalCached.getUnitPrice(), discountedCached.getUnitPrice());
        assertEquals("cached_tokens成本应应用折扣", 0,
                originalCached.getCost().multiply(discount).compareTo(discountedCached.getCost()));
    }

    /**
     * 测试场景4: 不同折扣率的精度测试
     */
    @Test
    public void testDifferentDiscountRates() {
        String priceInfoJson = buildCompletionPriceJson("100", "200");
        CompletionResponse.TokenUsage usage = buildTokenUsage(1000, 500);

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
            for (CostDetails.CostDetailItem item : discountedDetails.getInputDetails().values()) {
                detailsSum = detailsSum.add(item.getCost());
            }
            for (CostDetails.CostDetailItem item : discountedDetails.getOutputDetails().values()) {
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
        String priceInfoJson = buildCompletionPriceJson("10", "20");
        CompletionResponse.TokenUsage usage = buildTokenUsage(5000, 3000);

        CostDetails originalDetails = CostCalculator.calculate("/v1/chat/completions", priceInfoJson, usage);
        CostDetails discountedDetails = applyDiscount(originalDetails, new BigDecimal("0.8"));

        CostDetails.CostDetailItem originalInput = originalDetails.getInputDetails().get(PROMPT_TOKENS_KEY);
        CostDetails.CostDetailItem discountedInput = discountedDetails.getInputDetails().get(PROMPT_TOKENS_KEY);
        CostDetails.CostDetailItem originalOutput = originalDetails.getOutputDetails().get(COMPLETION_TOKENS_KEY);
        CostDetails.CostDetailItem discountedOutput = discountedDetails.getOutputDetails().get(COMPLETION_TOKENS_KEY);

        // 验证 unitPrice 不变
        assertEquals("输入单价应保持不变",
                originalInput.getUnitPrice(),
                discountedInput.getUnitPrice());

        assertEquals("输出单价应保持不变",
                originalOutput.getUnitPrice(),
                discountedOutput.getUnitPrice());

        // 验证 tokens 不变
        assertEquals("输入token数应保持不变",
                originalInput.getTokens(),
                discountedInput.getTokens());

        assertEquals("输出token数应保持不变",
                originalOutput.getTokens(),
                discountedOutput.getTokens());

        // 验证只有 cost 改变
        assertNotEquals("输入成本应改变",
                originalInput.getCost(),
                discountedInput.getCost());

        assertNotEquals("输出成本应改变",
                originalOutput.getCost(),
                discountedOutput.getCost());
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

    private Map<String, CostDetails.CostDetailItem> applyDiscountToDetailItems(
            Map<String, CostDetails.CostDetailItem> items, BigDecimal discountFactor) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        Map<String, CostDetails.CostDetailItem> result = new HashMap<>();
        items.forEach((key, item) -> result.put(key, CostDetails.CostDetailItem.builder()
                .tokens(item.getTokens())
                .unitPrice(item.getUnitPrice())
                .cost(item.getCost().multiply(discountFactor))
                .build()));
        return result;
    }

    private Map<String, CostDetails.ToolCostDetailItem> applyDiscountToToolItems(
            Map<String, CostDetails.ToolCostDetailItem> items, BigDecimal discount) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        Map<String, CostDetails.ToolCostDetailItem> result = new HashMap<>();
        items.forEach((key, item) -> result.put(key, CostDetails.ToolCostDetailItem.builder()
                .callCount(item.getCallCount())
                .unitPrice(item.getUnitPrice())
                .cost(item.getCost().multiply(discount))
                .build()));
        return result;
    }
}
