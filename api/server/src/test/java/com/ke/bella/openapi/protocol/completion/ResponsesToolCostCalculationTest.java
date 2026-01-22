package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Responses API 工具计费测试
 * 测试 Doubao 等厂商的工具调用计费场景
 */
public class ResponsesToolCostCalculationTest {

    /**
     * 测试场景：Doubao web_search 工具计费
     *
     * Usage:
     * {
     *   "input_tokens": 4601,
     *   "output_tokens": 1244,
     *   "total_tokens": 5845,
     *   "input_tokens_details": {"cached_tokens": 0},
     *   "output_tokens_details": {"reasoning_tokens": 573},
     *   "tool_usage": {"web_search": 1}
     * }
     *
     * Price:
     * - input: 1.0 分/千token
     * - output: 3.0 分/千token
     * - web_search: 0.5 分/次
     *
     * 期望成本（单位：分）:
     * - Token成本: (4601/1000) * 1.0 + (1244/1000) * 3.0 = 4.601 + 3.732 = 8.333 分
     * - 工具成本: 1 * 0.5 = 0.5 分
     * - 总成本: 8.333 + 0.5 = 8.833 分
     */
    @Test
    public void testDoubaoWebSearchToolCost() {
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
        System.out.println("Price Info JSON: " + priceInfoJson);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(4601);
        usage.setOutput_tokens(1244);
        usage.setTotal_tokens(5845);

        ResponsesApiResponse.InputTokensDetail inputDetail = new ResponsesApiResponse.InputTokensDetail();
        inputDetail.setCached_tokens(0);
        usage.setInput_tokens_details(inputDetail);

        ResponsesApiResponse.OutputTokensDetail outputDetail = new ResponsesApiResponse.OutputTokensDetail();
        outputDetail.setReasoning_tokens(573);
        usage.setOutput_tokens_details(outputDetail);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 1);
        usage.setTool_usage(toolUsage);

        BigDecimal cost = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        assertNotNull("Cost should not be null", cost);

        BigDecimal expectedTokenCost = new BigDecimal("4.601").add(new BigDecimal("3.732"));
        System.out.println("Expected Token Cost: " + expectedTokenCost);

        BigDecimal expectedToolCost = new BigDecimal("0.5");
        System.out.println("Expected Tool Cost: " + expectedToolCost);

        BigDecimal expectedTotalCost = expectedTokenCost.add(expectedToolCost);
        System.out.println("Expected Total Cost: " + expectedTotalCost);
        System.out.println("Actual Cost: " + cost);

        assertEquals("Cost calculation should be accurate",
                expectedTotalCost.doubleValue(), cost.doubleValue(), 0.001);
    }

    /**
     * 测试场景2：code_interpreter 工具计费
     */
    @Test
    public void testCodeInterpreterToolCost() {
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
        toolPrices.put("code_interpreter", new BigDecimal("1.0"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("code_interpreter", 2);
        usage.setTool_usage(toolUsage);

        BigDecimal cost = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        assertNotNull("Cost should not be null", cost);

        BigDecimal expectedTokenCost = new BigDecimal("1.0").add(new BigDecimal("1.5"));
        BigDecimal expectedToolCost = new BigDecimal("1.0").multiply(new BigDecimal("2"));
        BigDecimal expectedTotalCost = expectedTokenCost.add(expectedToolCost);

        System.out.println("Code Interpreter Test - Expected: " + expectedTotalCost + ", Actual: " + cost);

        assertEquals("Cost with code_interpreter should be accurate",
                expectedTotalCost.doubleValue(), cost.doubleValue(), 0.001);
    }

    /**
     * 测试场景3：多次工具调用计费
     */
    @Test
    public void testMultipleToolCalls() {
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

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(2000);
        usage.setOutput_tokens(800);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 1);
        usage.setTool_usage(toolUsage);

        BigDecimal cost = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        assertNotNull("Cost should not be null", cost);

        BigDecimal expectedTokenCost = new BigDecimal("2.0").add(new BigDecimal("2.4"));
        BigDecimal expectedToolCost = new BigDecimal("0.5");
        BigDecimal expectedTotalCost = expectedTokenCost.add(expectedToolCost);

        System.out.println("Multiple Tool Calls Test - Expected: " + expectedTotalCost + ", Actual: " + cost);

        assertEquals("Cost with multiple tool calls should be accurate",
                expectedTotalCost.doubleValue(), cost.doubleValue(), 0.001);
    }

    /**
     * 测试场景4：无工具调用时的计费
     */
    @Test
    public void testCostWithoutToolUsage() {
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

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(3000);
        usage.setOutput_tokens(1000);

        BigDecimal cost = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        assertNotNull("Cost should not be null", cost);

        BigDecimal expectedCost = new BigDecimal("3.0").add(new BigDecimal("3.0"));

        System.out.println("No Tool Test - Expected: " + expectedCost + ", Actual: " + cost);

        assertEquals("Cost without tools should be accurate",
                expectedCost.doubleValue(), cost.doubleValue(), 0.001);
    }
}
