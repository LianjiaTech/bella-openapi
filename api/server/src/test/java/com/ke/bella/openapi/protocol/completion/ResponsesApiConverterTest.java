package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponsesApiConverterTest {

    @Test
    public void missingPromptCacheKeyIsNotGenerated() {
        ResponsesApiRequest converted = ResponsesApiConverter.convertChatCompletionToResponses(
                CompletionRequest.builder().build(), new ResponsesApiProperty());

        assertThat(converted.getPrompt_cache_key()).isNull();
    }

    @Test
    public void explicitPromptCacheKeyIsPreserved() {
        ResponsesApiRequest converted = ResponsesApiConverter.convertChatCompletionToResponses(
                CompletionRequest.builder().prompt_cache_key("user-key").build(), new ResponsesApiProperty());

        assertThat(converted.getPrompt_cache_key()).isEqualTo("user-key");
    }

    @Test
    public void functionCallArgumentsRemainStringWhenConvertedToChatCompletion() {
        ResponsesApiResponse.OutputItem functionCall = new ResponsesApiResponse.OutputItem();
        functionCall.setType("function_call");
        functionCall.setCall_id("call_123");
        functionCall.setName("get_weather");
        functionCall.setArguments("{\"city\":\"Beijing\"}");
        ResponsesApiResponse response = ResponsesApiResponse.builder()
                .status("completed")
                .output(Collections.singletonList(functionCall))
                .build();

        CompletionResponse converted = ResponsesApiConverter.convertResponsesToChatCompletion(response);

        assertThat(converted.getChoices().get(0).getMessage().getTool_calls().get(0).getFunction().getArguments())
                .isEqualTo("{\"city\":\"Beijing\"}");
    }

    @Test
    public void xToolsUsageIsNormalizedForBillingWithoutOverwritingStandardUsage() {
        String usageJson = "{\"input_tokens\":1000,\"output_tokens\":500,"
                + "\"tool_usage\":{\"web_search\":2},"
                + "\"x_tools\":{\"web_search\":{\"count\":3},\"code_interpreter\":{\"count\":1},"
                + "\"missing_count\":{},\"zero\":{\"count\":0}}}";

        ResponsesApiResponse.Usage usage = JacksonUtils.deserialize(usageJson, ResponsesApiResponse.Usage.class);

        assertThat(usage.getTool_usage()).containsEntry("web_search", 2)
                .containsEntry("code_interpreter", 1)
                .containsEntry("zero", 0)
                .doesNotContainKey("missing_count");
        assertThat(usage.getExtraBodyFields()).containsKey("x_tools");

        ResponsesApiResponse.Usage reversedUsage = JacksonUtils.deserialize("{\"x_tools\":{\"web_search\":{\"count\":3}},\"tool_usage\":{\"web_search\":2}}", ResponsesApiResponse.Usage.class);
        assertThat(reversedUsage.getTool_usage()).containsEntry("web_search", 2);

        ResponsesPriceInfo.RangePrice rangePrice = new ResponsesPriceInfo.RangePrice();
        rangePrice.setMinToken(0);
        rangePrice.setMaxToken(Integer.MAX_VALUE);
        rangePrice.setInput(new BigDecimal("1.0"));
        rangePrice.setOutput(new BigDecimal("3.0"));
        ResponsesPriceInfo.Tier tier = new ResponsesPriceInfo.Tier();
        tier.setInputRangePrice(rangePrice);
        ResponsesPriceInfo priceInfo = new ResponsesPriceInfo();
        priceInfo.setTiers(Collections.singletonList(tier));
        Map<String, BigDecimal> toolPrices = new HashMap<>();
        toolPrices.put("web_search", new BigDecimal("0.5"));
        toolPrices.put("code_interpreter", new BigDecimal("0.25"));
        toolPrices.put("zero", new BigDecimal("9.0"));
        toolPrices.put("missing_count", new BigDecimal("9.0"));
        priceInfo.setToolPrices(toolPrices);

        CostDetails details = CostCalculator.calculate("/v1/responses", JacksonUtils.serialize(priceInfo), usage);

        assertThat(details.getToolDetails()).containsKey("web_search");
        assertThat(details.getToolDetails().get("web_search").getCallCount()).isEqualTo(2);
        assertThat(details.getToolDetails().get("web_search").getUnitPrice()).isEqualByComparingTo("0.5");
        assertThat(details.getToolDetails().get("web_search").getCost()).isEqualByComparingTo("1.0");
        assertThat(details.getToolDetails()).containsKey("code_interpreter");
        assertThat(details.getToolDetails().get("code_interpreter").getCallCount()).isEqualTo(1);
        assertThat(details.getToolDetails().get("code_interpreter").getCost()).isEqualByComparingTo("0.25");
        assertThat(details.getToolDetails()).doesNotContainKey("zero").doesNotContainKey("missing_count");
    }
}
