package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashAsrLogHandlerTest {
    private final FlashAsrLogHandler handler = new FlashAsrLogHandler();

    @Test
    void successfulResponseUsageIsOne() {
        EndpointProcessData processData = processData();
        processData.setResponse(FlashAsrResponse.builder()
                .flashResult(FlashAsrResponse.FlashResult.builder()
                        .duration(12345)
                        .build())
                .build());

        handler.process(processData);

        assertTrue(processData.getUsage() instanceof Map);
        Map<?, ?> usage = (Map<?, ?>) processData.getUsage();
        assertEquals(1, usage.get("count"));
        assertEquals(12.345, ((Number) usage.get("duration")).doubleValue(), 0.0001);
    }

    @Test
    void errorResponseUsageIsZero() {
        EndpointProcessData processData = processData();
        OpenapiResponse response = new OpenapiResponse();
        response.setError(new OpenapiResponse.OpenapiError("invalid_request", "bad request", 400));
        processData.setResponse(response);

        handler.process(processData);

        assertTrue(processData.getUsage() instanceof Map);
        Map<?, ?> usage = (Map<?, ?>) processData.getUsage();
        assertEquals(0, usage.get("count"));
        assertEquals(0.0, ((Number) usage.get("duration")).doubleValue(), 0.0001);
    }

    @Test
    void nullResponseUsageIsZero() {
        EndpointProcessData processData = processData();

        handler.process(processData);

        assertTrue(processData.getUsage() instanceof Map);
        Map<?, ?> usage = (Map<?, ?>) processData.getUsage();
        assertEquals(0, usage.get("count"));
        assertEquals(0.0, ((Number) usage.get("duration")).doubleValue(), 0.0001);
    }

    private EndpointProcessData processData() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/flash");
        processData.setRequestTime(System.currentTimeMillis() / 1000);
        return processData;
    }
}
