package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Responses API 成本详细信息测试
 * 测试 Responses API 的详细成本分解，包括工具调用成本
 */
public class ResponsesCostDetailsTest {

    /**
     * 测试场景1: 包含工具调用的成本明细
     */
    @Test
    public void testResponsesWithToolCostDetails() {
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
        toolPrices.put("code_interpreter", new BigDecimal("1.0"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 2);         // 调用2次
        toolUsage.put("code_interpreter", 1);   // 调用1次
        usage.setTool_usage(toolUsage);

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 验证总成本 = token成本 + 工具成本
        // token成本: 1000/1000 * 1.0 + 500/1000 * 3.0 = 1.0 + 1.5 = 2.5
        // 工具成本: 2 * 0.5 + 1 * 1.0 = 1.0 + 1.0 = 2.0
        // 总成本: 2.5 + 2.0 = 4.5
        BigDecimal expectedTotal = new BigDecimal("4.5");
        assertEquals("总成本应为4.5", 0, expectedTotal.compareTo(costDetails.getTotalCost()));

        // 验证输入明细
        assertNotNull("输入明细不应为null", costDetails.getInputDetails());
        assertEquals("应该有1个输入明细项", 1, costDetails.getInputDetails().size());

        CostDetails.CostDetailItem inputItem = costDetails.getInputDetails().get("input_tokens");
        assertNotNull("应该有 input_tokens 明细", inputItem);
        assertEquals("输入token数应为1000", Integer.valueOf(1000), inputItem.getTokens());
        assertEquals("输入成本应为1.0", 0, new BigDecimal("1.0").compareTo(inputItem.getCost()));

        // 验证输出明细
        assertNotNull("输出明细不应为null", costDetails.getOutputDetails());
        assertEquals("应该有1个输出明细项", 1, costDetails.getOutputDetails().size());

        CostDetails.CostDetailItem outputItem = costDetails.getOutputDetails().get("output_tokens");
        assertNotNull("应该有 output_tokens 明细", outputItem);
        assertEquals("输出token数应为500", Integer.valueOf(500), outputItem.getTokens());
        assertEquals("输出成本应为1.5", 0, new BigDecimal("1.5").compareTo(outputItem.getCost()));

        // 验证工具明细
        assertNotNull("工具明细不应为null", costDetails.getToolDetails());
        assertEquals("应该有2个工具明细项", 2, costDetails.getToolDetails().size());

        // 查找各工具明细
        CostDetails.ToolCostDetailItem webSearchItem = costDetails.getToolDetails().get("web_search");
        CostDetails.ToolCostDetailItem codeInterpreterItem = costDetails.getToolDetails().get("code_interpreter");

        // 验证 web_search 明细
        assertNotNull("应该有web_search明细", webSearchItem);
        assertEquals("web_search调用次数应为2", Integer.valueOf(2), webSearchItem.getCallCount());
        assertEquals("web_search单价应为0.5", new BigDecimal("0.5"), webSearchItem.getUnitPrice());
        assertEquals("web_search成本应为1.0", 0, new BigDecimal("1.0").compareTo(webSearchItem.getCost()));

        // 验证 code_interpreter 明细
        assertNotNull("应该有code_interpreter明细", codeInterpreterItem);
        assertEquals("code_interpreter调用次数应为1", Integer.valueOf(1), codeInterpreterItem.getCallCount());
        assertEquals("code_interpreter单价应为1.0", new BigDecimal("1.0"), codeInterpreterItem.getUnitPrice());
        assertEquals("code_interpreter成本应为1.0", 0, new BigDecimal("1.0").compareTo(codeInterpreterItem.getCost()));

        // 验证总和一致性
        BigDecimal tokenCostSum = inputItem.getCost().add(outputItem.getCost());
        BigDecimal toolCostSum = webSearchItem.getCost().add(codeInterpreterItem.getCost());
        BigDecimal detailsSum = tokenCostSum.add(toolCostSum);
        assertEquals("明细总和应等于总成本", 0, costDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景2: 包含缓存和推理token的成本明细
     */
    @Test
    public void testResponsesWithCachedAndReasoningTokens() {
        ResponsesPriceInfo.RangePrice rangePrice = new ResponsesPriceInfo.RangePrice();
        rangePrice.setMinToken(0);
        rangePrice.setMaxToken(Integer.MAX_VALUE);
        rangePrice.setInput(new BigDecimal("1.0"));
        rangePrice.setOutput(new BigDecimal("3.0"));
        rangePrice.setCachedInput(new BigDecimal("0.1"));          // 缓存输入: 0.1分/千token
        rangePrice.setReasoningOutput(new BigDecimal("6.0"));      // 推理输出: 6.0分/千token

        ResponsesPriceInfo.Tier tier = new ResponsesPriceInfo.Tier();
        tier.setInputRangePrice(rangePrice);

        List<ResponsesPriceInfo.Tier> tiers = new ArrayList<>();
        tiers.add(tier);

        ResponsesPriceInfo priceInfo = new ResponsesPriceInfo();
        priceInfo.setTiers(tiers);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(10000);    // 总输入: 10k
        usage.setOutput_tokens(5000);    // 总输出: 5k

        // 设置输入token详情
        ResponsesApiResponse.InputTokensDetail inputDetail = new ResponsesApiResponse.InputTokensDetail();
        inputDetail.setCached_tokens(3000);  // 3k缓存token
        usage.setInput_tokens_details(inputDetail);

        // 设置输出token详情
        ResponsesApiResponse.OutputTokensDetail outputDetail = new ResponsesApiResponse.OutputTokensDetail();
        outputDetail.setReasoning_tokens(2000);  // 2k推理token
        usage.setOutput_tokens_details(outputDetail);

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 验证输入明细（应该有2项：cached_tokens 和 input_tokens）
        assertNotNull("输入明细不应为null", costDetails.getInputDetails());
        assertEquals("应该有2个输入明细项", 2, costDetails.getInputDetails().size());

        CostDetails.CostDetailItem cachedItem = costDetails.getInputDetails().get("cached_tokens");
        CostDetails.CostDetailItem inputItem = costDetails.getInputDetails().get("input_tokens");

        // 验证缓存token明细
        assertNotNull("应该有cached_tokens明细", cachedItem);
        assertEquals("缓存token数应为3000", Integer.valueOf(3000), cachedItem.getTokens());
        assertEquals("缓存单价应为0.1", new BigDecimal("0.1"), cachedItem.getUnitPrice());
        assertEquals("缓存成本应为0.3", 0, new BigDecimal("0.3").compareTo(cachedItem.getCost()));

        // 验证普通输入token明细（应该扣除缓存token）
        assertNotNull("应该有input_tokens明细", inputItem);
        assertEquals("普通输入token数应为7000 (10000-3000)", Integer.valueOf(7000), inputItem.getTokens());
        assertEquals("普通输入单价应为1.0", new BigDecimal("1.0"), inputItem.getUnitPrice());
        assertEquals("普通输入成本应为7.0", 0, new BigDecimal("7.0").compareTo(inputItem.getCost()));

        // 验证输出明细（应该有2项：reasoning_tokens 和 output_tokens）
        assertNotNull("输出明细不应为null", costDetails.getOutputDetails());
        assertEquals("应该有2个输出明细项", 2, costDetails.getOutputDetails().size());

        CostDetails.CostDetailItem reasoningItem = costDetails.getOutputDetails().get("reasoning_tokens");
        CostDetails.CostDetailItem outputItem = costDetails.getOutputDetails().get("output_tokens");

        // 验证推理token明细
        assertNotNull("应该有reasoning_tokens明细", reasoningItem);
        assertEquals("推理token数应为2000", Integer.valueOf(2000), reasoningItem.getTokens());
        assertEquals("推理单价应为6.0", new BigDecimal("6.0"), reasoningItem.getUnitPrice());
        assertEquals("推理成本应为12.0", 0, new BigDecimal("12.0").compareTo(reasoningItem.getCost()));

        // 验证普通输出token明细（应该扣除推理token）
        assertNotNull("应该有output_tokens明细", outputItem);
        assertEquals("普通输出token数应为3000 (5000-2000)", Integer.valueOf(3000), outputItem.getTokens());
        assertEquals("普通输出单价应为3.0", new BigDecimal("3.0"), outputItem.getUnitPrice());
        assertEquals("普通输出成本应为9.0", 0, new BigDecimal("9.0").compareTo(outputItem.getCost()));

        // 验证总和一致性
        BigDecimal detailsSum = cachedItem.getCost()
                .add(inputItem.getCost())
                .add(reasoningItem.getCost())
                .add(outputItem.getCost());
        assertEquals("明细总和应等于总成本", 0, costDetails.getTotalCost().compareTo(detailsSum));
    }

    /**
     * 测试场景3: 没有工具调用时，工具明细应为null
     */
    @Test
    public void testResponsesWithoutToolsShouldHaveNullToolDetails() {
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
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);
        // 没有设置 tool_usage

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 验证工具明细为null
        assertNull("没有工具调用时，工具明细应为null", costDetails.getToolDetails());

        // 验证总成本只包含token成本
        BigDecimal expectedTotal = new BigDecimal("1.0").add(new BigDecimal("1.5"));
        assertEquals("总成本应只包含token成本", 0, expectedTotal.compareTo(costDetails.getTotalCost()));
    }

    /**
     * 测试场景4: 工具调用次数为0时不应出现在明细中
     */
    @Test
    public void testZeroToolCallsShouldNotAppearInDetails() {
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
        toolPrices.put("code_interpreter", new BigDecimal("1.0"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(1000);
        usage.setOutput_tokens(500);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 2);
        toolUsage.put("code_interpreter", 0);  // 调用0次
        usage.setTool_usage(toolUsage);

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 验证工具明细
        assertNotNull("工具明细不应为null", costDetails.getToolDetails());
        assertEquals("应该只有1个工具明细项（code_interpreter调用0次不应出现）",
                1, costDetails.getToolDetails().size());

        CostDetails.ToolCostDetailItem item = costDetails.getToolDetails().get("web_search");
        assertNotNull("应该有 web_search 工具明细", item);
    }

    /**
     * 测试场景5: 所有明细总和等于总成本（包含工具）
     */
    @Test
    public void testTotalCostEqualsDetailsSumWithTools() {
        ResponsesPriceInfo.RangePrice rangePrice = new ResponsesPriceInfo.RangePrice();
        rangePrice.setMinToken(0);
        rangePrice.setMaxToken(Integer.MAX_VALUE);
        rangePrice.setInput(new BigDecimal("1.234"));
        rangePrice.setOutput(new BigDecimal("3.456"));
        rangePrice.setCachedInput(new BigDecimal("0.123"));

        ResponsesPriceInfo.Tier tier = new ResponsesPriceInfo.Tier();
        tier.setInputRangePrice(rangePrice);

        List<ResponsesPriceInfo.Tier> tiers = new ArrayList<>();
        tiers.add(tier);

        ResponsesPriceInfo priceInfo = new ResponsesPriceInfo();
        priceInfo.setTiers(tiers);

        Map<String, BigDecimal> toolPrices = new HashMap<>();
        toolPrices.put("web_search", new BigDecimal("0.567"));
        priceInfo.setToolPrices(toolPrices);

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(8000);
        usage.setOutput_tokens(4000);

        ResponsesApiResponse.InputTokensDetail inputDetail = new ResponsesApiResponse.InputTokensDetail();
        inputDetail.setCached_tokens(2000);
        usage.setInput_tokens_details(inputDetail);

        Map<String, Integer> toolUsage = new HashMap<>();
        toolUsage.put("web_search", 3);
        usage.setTool_usage(toolUsage);

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        // 计算所有明细项的总和
        BigDecimal detailsSum = BigDecimal.ZERO;

        if(costDetails.getInputDetails() != null) {
            for (CostDetails.CostDetailItem item : costDetails.getInputDetails().values()) {
                detailsSum = detailsSum.add(item.getCost());
            }
        }

        if(costDetails.getOutputDetails() != null) {
            for (CostDetails.CostDetailItem item : costDetails.getOutputDetails().values()) {
                detailsSum = detailsSum.add(item.getCost());
            }
        }

        if(costDetails.getToolDetails() != null) {
            for (CostDetails.ToolCostDetailItem item : costDetails.getToolDetails().values()) {
                detailsSum = detailsSum.add(item.getCost());
            }
        }

        // 验证总和一致性（允许小的精度误差）
        BigDecimal diff = costDetails.getTotalCost().subtract(detailsSum).abs();
        assertTrue("明细总和应等于总成本（误差小于0.001），实际差值: " + diff,
                diff.compareTo(new BigDecimal("0.001")) < 0);
    }

    @Test
    public void testResponsesShouldSupportCompletionStylePriceFields() {
        String priceInfoJson = "{\"batchDiscount\":1,\"tiers\":[{\"inputRangePrice\":{\"minToken\":0,\"maxToken\":272000,\"input\":1.75,\"output\":10.5,\"cachedRead\":0.175}},{\"inputRangePrice\":{\"minToken\":272000,\"maxToken\":2147483647,\"input\":3.5,\"output\":15.75,\"cachedRead\":0.35}}],\"toolPrices\":{\"web_search\":17.5},\"unit\":\"分/千token\"}";

        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(10000);
        usage.setOutput_tokens(5000);

        ResponsesApiResponse.InputTokensDetail inputDetail = new ResponsesApiResponse.InputTokensDetail();
        inputDetail.setCached_tokens(3000);
        usage.setInput_tokens_details(inputDetail);

        CostDetails costDetails = CostCalculator.calculate("/v1/responses", priceInfoJson, usage);

        assertNotNull("兼容 completion 风格 cachedRead 后应有 cached_tokens 明细", costDetails.getInputDetails());
        CostDetails.CostDetailItem cachedItem = costDetails.getInputDetails().get("cached_tokens");
        assertNotNull("cached_tokens 明细不应为 null", cachedItem);
        assertEquals("缓存单价应取 cachedRead", new BigDecimal("0.175"), cachedItem.getUnitPrice());
        assertEquals("缓存成本应正确计算", 0, new BigDecimal("0.525").compareTo(cachedItem.getCost()));

        CostDetails.CostDetailItem inputItem = costDetails.getInputDetails().get("input_tokens");
        assertNotNull("普通输入 token 明细不应为 null", inputItem);
        assertEquals("普通输入 token 应扣除缓存 token", Integer.valueOf(7000), inputItem.getTokens());
    }

}
