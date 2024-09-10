package com.ke.bella.openapi.protocol.cost;

import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Arrays;

@Slf4j
public class CostCalculator  {

    public static BigDecimal calculate(String endpoint, String priceInfo, Object usage) {
        EndpointCostCalculator calculator = Arrays.stream(CostCalculators.values()).filter(t -> MatchUtils.matchUrl(t.endpoint, endpoint))
                .findAny()
                .map(CostCalculators::getCalculator)
                .orElseThrow(() -> new RuntimeException("no calculator implemented for " + endpoint));
        return calculator.calculate(priceInfo, usage);
    }

    public static boolean validate(String endpoint, String priceInfo) {
        EndpointCostCalculator calculator = Arrays.stream(CostCalculators.values()).filter(t -> MatchUtils.matchUrl(t.endpoint, endpoint))
                .findAny()
                .map(CostCalculators::getCalculator)
                .orElse(null);
        if(calculator == null) {
            LOGGER.warn("no calculator implemented for " + endpoint);
            return true;
        }
        return calculator.checkPriceInfo(priceInfo);
    }

    @AllArgsConstructor
    @Getter
    enum CostCalculators {
        COMPLETION("/v*/chat/completions", completion);
        final String endpoint;
        final EndpointCostCalculator calculator;
    }

    static EndpointCostCalculator completion = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            PriceInfo price = JacksonUtils.deserialize(priceInfo, PriceInfo.class);
            CompletionResponse.TokenUsage tokenUsage = (CompletionResponse.TokenUsage) usage;
            return price.getInput().multiply(BigDecimal.valueOf(tokenUsage.getPrompt_tokens() / 1000.0))
                    .add(price.getOutput().multiply(BigDecimal.valueOf(tokenUsage.getCompletion_tokens() / 1000.0)));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            PriceInfo price = JacksonUtils.deserialize(priceInfo, PriceInfo.class);
            return price != null && price.getInput() != null && price.getOutput() != null;
        }
    };

    @Data
    public static class PriceInfo {
        private BigDecimal input;
        private BigDecimal output;
        private String unit = "分/千token";
    }

    interface EndpointCostCalculator {
        BigDecimal calculate(String priceInfo, Object usage);
        boolean checkPriceInfo(String priceInfo);
    }
}
