package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeRerankAdaptorTest {
    private final KeRerankAdaptor adaptor = new KeRerankAdaptor();

    @Test
    void convertRequestUsesIntroduction() {
        RerankRequest request = new RerankRequest();
        request.setQuery("学校师资力量和升学表现怎么样");
        request.setDocuments(Arrays.asList("doc1", "doc2"));
        request.setInstruct("Given a web search query, retrieve relevant passages that answer the query");
        request.setTopN(1);

        KeRerankRequest keRequest = adaptor.requestConvert(request);

        assertEquals(request.getQuery(), keRequest.getQuery());
        assertEquals(request.getDocuments(), keRequest.getDocuments());
        assertEquals(request.getInstruct(), keRequest.getIntroduction());
        assertEquals(request.getTopN(), keRequest.getTopN());
        Map<String, Object> requestBody = JacksonUtils.deserialize(JacksonUtils.serialize(keRequest), Map.class);
        assertEquals(1, requestBody.get("top_n"));
    }

    @Test
    void deserializeResponseKeepsKeDocumentAndScores() {
        RerankResponse response = JacksonUtils.deserialize("{"
                + "\"id\":\"rerank-782415e8edf64c6689e5aeb26c6bea1f\","
                + "\"model\":\"qwen3-reranker-4b-20251028\","
                + "\"usage\":{\"total_tokens\":86},"
                + "\"results\":["
                + "{\"index\":2,\"document\":{\"text\":\"doc3\",\"multi_modal\":null},\"relevance_score\":0.40384194254875183},"
                + "{\"index\":1,\"document\":{\"text\":\"doc2\",\"multi_modal\":null},\"relevance_score\":0.28238528966903687}"
                + "]"
                + "}", RerankResponse.class);

        assertEquals("rerank-782415e8edf64c6689e5aeb26c6bea1f", response.getId());
        assertEquals("qwen3-reranker-4b-20251028", response.getModel());
        assertEquals(86, response.getUsage().getTotalTokens());
        assertEquals(2, response.getResults().size());
        assertEquals(2, response.getResults().get(0).getIndex());
        assertEquals(0.40384194254875183, response.getResults().get(0).getRelevanceScore());
        Map<String, Object> document = (Map<String, Object>) response.getResults().get(0).getDocument();
        assertEquals("doc3", document.get("text"));
    }
}
