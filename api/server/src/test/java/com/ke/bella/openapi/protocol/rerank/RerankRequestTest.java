package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RerankRequestTest {
    @Test
    void deserializeTextQueryAndTextDocumentsWithUser() {
        RerankRequest request = JacksonUtils.deserialize("{"
                + "\"model\":\"qwen3-rerank\","
                + "\"user\":\"u-1\","
                + "\"query\":\"family hotel\","
                + "\"documents\":[\"hotel a\",\"hotel b\"],"
                + "\"top_n\":1,"
                + "\"return_documents\":true,"
                + "\"instruct\":\"rank by family friendliness\","
                + "\"custom_flag\":\"x\""
                + "}", RerankRequest.class);

        assertNotNull(request);
        assertEquals("u-1", request.getUser());
        assertEquals("qwen3-rerank", request.getModel());
        assertEquals("family hotel", request.getQuery());
        assertEquals(2, request.getDocuments().size());
        assertEquals(1, request.getTopN());
        assertEquals("rank by family friendliness", request.getInstruct());
        assertEquals(Boolean.TRUE, request.getExtraBody().get("return_documents"));
        assertEquals("x", request.getExtraBody().get("custom_flag"));
    }
}
