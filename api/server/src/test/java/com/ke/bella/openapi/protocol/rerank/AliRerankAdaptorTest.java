package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AliRerankAdaptorTest {
    @Test
    void deserializeCompatibleResponseWithUsage() {
        RerankResponse response = JacksonUtils.deserialize("{"
                + "\"id\":\"rerank-1\","
                + "\"object\":\"list\","
                + "\"model\":\"qwen3-rerank\","
                + "\"request_id\":\"req-1\","
                + "\"results\":[{\"index\":1,\"relevance_score\":0.91,\"document\":\"hotel b\"}],"
                + "\"usage\":{\"total_tokens\":30}"
                + "}", RerankResponse.class);

        assertEquals("req-1", response.getRequestId());
        assertEquals("rerank-1", response.getId());
        assertEquals(1, response.getResults().size());
        assertEquals(1, response.getResults().get(0).getIndex());
        assertEquals(0.91, response.getResults().get(0).getRelevanceScore());
        assertEquals(30, response.getUsage().getTotalTokens());
    }

    @Test
    void deserializeErrorResponse() {
        RerankResponse response = JacksonUtils.deserialize("{"
                + "\"code\":\"InvalidParameter\","
                + "\"message\":\"bad request\","
                + "\"request_id\":\"fb53c4ec-1c12-4fc4-a580-cdb7c3261fc1\""
                + "}", RerankResponse.class);

        assertEquals("fb53c4ec-1c12-4fc4-a580-cdb7c3261fc1", response.getRequestId());
        assertEquals("InvalidParameter", response.getCode());
        assertEquals("bad request", response.getMessage());
    }
}
