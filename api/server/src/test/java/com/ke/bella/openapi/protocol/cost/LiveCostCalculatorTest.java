package com.ke.bella.openapi.protocol.cost;

import com.ke.bella.openapi.protocol.live.LiveSessionUsage;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveCostCalculatorTest {
    @Test
    public void calculatesDurationTokensToolsAndDiscount() {
        String price = "{\"inputTextTokenPrice\":100,\"outputAudioTokenPrice\":200,"
                + "\"sessionMinutePrice\":6,\"toolCallPrice\":2,\"supplierDiscount\":0.5}";
        LiveSessionUsage usage = new LiveSessionUsage();
        usage.setInputTextTokens(1_000_000L);
        usage.setOutputAudioTokens(500_000L);
        usage.setSessionDurationSeconds(30L);
        usage.setToolCalls(2L);

        CostDetails details = CostCalculator.calculate("/v1/live/sessions", price, usage);

        assertEquals(0, new BigDecimal("103.5").compareTo(details.getTotalCost()));
        assertTrue(CostCalculator.validate("/v1/live/sessions", price));
    }

    @Test
    public void billsOnlyProviderConfirmedMediaDuration() {
        String price = "{\"sessionMinutePrice\":35,\"supplierDiscount\":1}";
        LiveSessionUsage noMediaUsage = new LiveSessionUsage();
        noMediaUsage.setSessionDurationSeconds(0L);
        LiveSessionUsage mediaUsage = new LiveSessionUsage();
        mediaUsage.setSessionDurationSeconds(2L);

        CostDetails noMediaDetails = CostCalculator.calculate("/v1/live/sessions", price, noMediaUsage);
        CostDetails mediaDetails = CostCalculator.calculate("/v1/live/sessions", price, mediaUsage);

        assertEquals(0, BigDecimal.ZERO.compareTo(noMediaDetails.getTotalCost()));
        assertEquals(0, new BigDecimal("1.1666666667").compareTo(mediaDetails.getTotalCost()));
    }
}
