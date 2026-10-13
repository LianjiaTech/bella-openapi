package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RerankCostDetailsTest {
    @Test
    void calculateByTotalTokens() {
        RerankPriceInfo priceInfo = new RerankPriceInfo();
        priceInfo.setInput(new BigDecimal("2.5"));
        RerankResponse.Usage usage = new RerankResponse.Usage();
        usage.setTotalTokens(2000);

        CostDetails details = CostCalculator.calculate("/v1/reranks", JacksonUtils.serialize(priceInfo), usage);

        assertEquals(0, new BigDecimal("5.0").compareTo(details.getTotalCost()));
        assertNotNull(details.getInputDetails());
        assertEquals(Integer.valueOf(2000), details.getInputDetails().get("total_tokens").getTokens());
    }

    @Test
    void validatePriceInfo() {
        RerankPriceInfo priceInfo = new RerankPriceInfo();
        priceInfo.setInput(new BigDecimal("1"));

        assertTrue(CostCalculator.validate("/v1/reranks", JacksonUtils.serialize(priceInfo)));
    }
}
