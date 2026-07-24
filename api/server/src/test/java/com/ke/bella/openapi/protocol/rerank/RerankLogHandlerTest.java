package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RerankLogHandlerTest {
    private final RerankLogHandler handler = new RerankLogHandler();

    @Test
    void usesResponseUsage() {
        EndpointProcessData processData = processData();
        RerankResponse response = new RerankResponse();
        RerankResponse.Usage usage = new RerankResponse.Usage();
        usage.setTotalTokens(42);
        response.setUsage(usage);
        processData.setResponse(response);

        handler.process(processData);

        assertEquals(42, ((RerankResponse.Usage) processData.getUsage()).getTotalTokens());
        assertEquals(42, processData.getMetrics().get("token"));
    }

    @Test
    void usesPrecomputedMetricsWhenRequestOptimized() {
        EndpointProcessData processData = processData();
        processData.setRequestMetrics(RequestMetrics.builder().rerankTokens(33).build());
        processData.markRequestOptimized();
        processData.setResponse(new RerankResponse());

        handler.process(processData);

        assertEquals(33, ((RerankResponse.Usage) processData.getUsage()).getTotalTokens());
    }

    @Test
    void doesNotRecordRequestShapeMetrics() {
        EndpointProcessData processData = processData();
        RerankRequest request = new RerankRequest();
        request.setQuery("query");
        request.setDocuments(Arrays.asList("doc1", "doc2"));
        request.setTopN(1);
        processData.setRequest(request);
        processData.setResponse(new RerankResponse());

        handler.process(processData);

        assertFalse(processData.getMetrics().containsKey("document_count"));
        assertFalse(processData.getMetrics().containsKey("top_n"));
    }

    @Test
    void fourHundredErrorUsageIsZero() {
        EndpointProcessData processData = processData();
        RerankResponse response = new RerankResponse();
        response.setError(new OpenapiResponse.OpenapiError("invalid_request", "bad", 400));
        processData.setResponse(response);

        handler.process(processData);

        assertEquals(0, ((RerankResponse.Usage) processData.getUsage()).getTotalTokens());
    }

    @Test
    void errorWithNullHttpCodeDoesNotFailLogging() {
        EndpointProcessData processData = processData();
        RerankResponse response = new RerankResponse();
        response.setError(OpenapiResponse.OpenapiError.builder()
                .code("InvalidParameter")
                .message("bad request")
                .type("InvalidParameter")
                .build());
        processData.setResponse(response);

        handler.process(processData);

        assertEquals(0, ((RerankResponse.Usage) processData.getUsage()).getTotalTokens());
    }

    private EndpointProcessData processData() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/reranks");
        processData.setRequestTime(0);
        processData.setEncodingType("cl100k_base");
        return processData;
    }
}
