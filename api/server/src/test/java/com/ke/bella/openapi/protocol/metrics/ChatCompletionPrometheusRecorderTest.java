package com.ke.bella.openapi.protocol.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.message.MessageResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ChatCompletionPrometheusRecorderTest {
    private SimpleMeterRegistry registry;
    private ChatCompletionPrometheusRecorder recorder;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new ChatCompletionPrometheusRecorder(registry);
    }

    @Test
    public void ignoresNullMockAndNonTextGenerationRequests() {
        recorder.finish(null);

        EndpointProcessData mock = processData("mock", "/v1/chat/completions");
        mock.setMock(true);
        recorder.finish(mock);

        recorder.finish(processData("images", "/v1/images/generations"));

        assertEquals(0, registry.getMeters().size());
    }

    @Test
    public void recordsRouteFailureWithStatusAndLowCardinalityLabels() {
        EndpointProcessData processData = processData("route-failure", "/v1/messages");
        processData.setModel(null);
        processData.setSupplier(null);
        processData.setFailureStage(EndpointProcessData.FAILURE_STAGE_ROUTE);
        processData.setResponse(errorResponse(503));

        recorder.finish(processData);

        assertCounter(1, "bella_channel_route_failure",
                "api_endpoint", "/v1/messages",
                "model", "unknown");
        assertCounter(1, "bella_channel_requests",
                "api_endpoint", "/v1/messages",
                "model", "unknown",
                "supplier", "none",
                "channel_code", "channel-a",
                "deploy_name", "deploy-a",
                "forward_host", "host-a",
                "forward_path", "/provider/chat",
                "protocol", "openai",
                "stream", "false",
                "status_class", "route_failure",
                "http_status_code", "503");
        assertNull(registry.find("bella_system_ttlt_milliseconds").summary());
    }

    @Test
    public void classifiesSuccessfulAndErrorResponses() {
        EndpointProcessData success = processData("success", "/v1/chat/completions");
        success.setResponse(new CompletionResponse());
        recorder.finish(success);

        EndpointProcessData clientError = processData("client-error", "/v1/messages");
        clientError.setResponse(errorResponse(400));
        recorder.finish(clientError);

        EndpointProcessData serverError = processData("server-error", "/v1/responses");
        serverError.setResponse(errorResponse(502));
        recorder.finish(serverError);

        assertRequestStatus("/v1/chat/completions", "success", "200");
        assertRequestStatus("/v1/messages", "error_4xx", "400");
        assertRequestStatus("/v1/responses", "error_5xx", "502");
    }

    @Test
    public void recordsCompletionTimingTpsAndAllTokenTypes() {
        CompletionResponse.TokensDetail promptDetails = new CompletionResponse.TokensDetail();
        promptDetails.setCached_tokens(25);
        promptDetails.setCache_creation_tokens(5);
        CompletionResponse.TokensDetail completionDetails = new CompletionResponse.TokensDetail();
        completionDetails.setReasoning_tokens(40);
        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(1200)
                .completion_tokens(600)
                .prompt_tokens_details(promptDetails)
                .completion_tokens_details(completionDetails)
                .build();
        CompletionResponse response = new CompletionResponse();
        response.setUsage(usage);

        EndpointProcessData processData = processData("completion", "/v1/chat/completions");
        processData.setRequest(CompletionRequest.builder().stream(true).build());
        processData.setRequestMillis(1000);
        processData.setForwardMillis(1200);
        processData.setFirstPackageTime(1500);
        processData.setResponseMillis(3500);
        processData.setResponse(response);
        processData.setUsage(usage);

        recorder.finish(processData);

        assertSummary(1, 2500, "bella_system_ttlt_milliseconds",
                "input_token_bucket", "1k_8k", "output_token_bucket", "512_2k");
        assertSummary(1, 2300, "bella_channel_ttlt_milliseconds",
                "input_token_bucket", "1k_8k", "output_token_bucket", "512_2k");
        assertSummary(1, 500, "bella_system_ttft_milliseconds", "input_token_bucket", "1k_8k");
        assertSummary(1, 300, "bella_channel_ttft_milliseconds", "input_token_bucket", "1k_8k");
        assertSummary(1, 300, "bella_chat_completion_tps", "output_token_bucket", "512_2k", "stream", "true");
        assertTokenCounter(1200, "/v1/chat/completions", "input");
        assertTokenCounter(600, "/v1/chat/completions", "output");
        assertTokenCounter(30, "/v1/chat/completions", "cache");
        assertTokenCounter(40, "/v1/chat/completions", "reasoning");
        assertTokenCounter(1800, "/v1/chat/completions", "total");
    }

    @Test
    public void extractsMessageAndResponsesApiUsageFromResponses() {
        MessageResponse.Usage messageUsage = MessageResponse.Usage.builder()
                .inputTokens(100)
                .outputTokens(50)
                .cacheCreationInputTokens(20)
                .cacheReadInputTokens(10)
                .build();
        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setUsage(messageUsage);
        EndpointProcessData message = processData("message", "/v1/messages");
        message.setResponse(messageResponse);
        message.setUsage(CompletionResponse.TokenUsage.builder().prompt_tokens(999).build());
        recorder.finish(message);

        ResponsesApiResponse.Usage responsesUsage = ResponsesApiResponse.Usage.builder()
                .input_tokens(90)
                .output_tokens(40)
                .input_tokens_details(ResponsesApiResponse.InputTokensDetail.builder().cached_tokens(8).build())
                .output_tokens_details(ResponsesApiResponse.OutputTokensDetail.builder().reasoning_tokens(6).build())
                .build();
        ResponsesApiResponse responsesApiResponse = new ResponsesApiResponse();
        responsesApiResponse.setUsage(responsesUsage);
        EndpointProcessData responses = processData("responses", "/v1/responses");
        responses.setResponse(responsesApiResponse);
        recorder.finish(responses);

        assertTokenCounter(130, "/v1/messages", "input");
        assertTokenCounter(50, "/v1/messages", "output");
        assertTokenCounter(30, "/v1/messages", "cache");
        assertTokenCounter(180, "/v1/messages", "total");
        assertTokenCounter(90, "/v1/responses", "input");
        assertTokenCounter(40, "/v1/responses", "output");
        assertTokenCounter(8, "/v1/responses", "cache");
        assertTokenCounter(6, "/v1/responses", "reasoning");
        assertTokenCounter(130, "/v1/responses", "total");
    }

    @Test
    public void toleratesMissingAndNegativeUsageValues() {
        EndpointProcessData completion = processData("negative-completion", "/v1/chat/completions");
        completion.setUsage(CompletionResponse.TokenUsage.builder()
                .prompt_tokens(-10)
                .completion_tokens(-20)
                .cache_read_tokens(-3)
                .cache_creation_tokens(-4)
                .build());
        recorder.finish(completion);

        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setUsage(MessageResponse.Usage.builder()
                .inputTokens(-10)
                .outputTokens(-20)
                .cacheCreationInputTokens(-3)
                .cacheReadInputTokens(-4)
                .build());
        EndpointProcessData message = processData("negative-message", "/v1/messages");
        message.setResponse(messageResponse);
        recorder.finish(message);

        ResponsesApiResponse responsesApiResponse = new ResponsesApiResponse();
        responsesApiResponse.setUsage(ResponsesApiResponse.Usage.builder()
                .input_tokens(null)
                .output_tokens(-20)
                .input_tokens_details(ResponsesApiResponse.InputTokensDetail.builder().cached_tokens(-3).build())
                .output_tokens_details(ResponsesApiResponse.OutputTokensDetail.builder().reasoning_tokens(null).build())
                .build());
        EndpointProcessData responses = processData("negative-responses", "/v1/responses");
        responses.setResponse(responsesApiResponse);
        recorder.finish(responses);

        assertEquals(3, requestCounterCount());
        assertNull(registry.find("bella_channel_tokens").counter());
    }

    @Test
    public void recordsEachRequestIdOnlyOnce() {
        recorder.finish(processData("same-id", "/v1/chat/completions"));
        recorder.finish(processData("same-id", "/v1/chat/completions"));

        assertEquals(1, requestCounterCount());
    }

    @Test
    public void usesRequestObjectIdentityWhenRequestIdIsMissing() {
        EndpointProcessData first = processData(null, "/v1/chat/completions");
        EndpointProcessData second = processData(null, "/v1/chat/completions");

        recorder.finish(first);
        recorder.finish(first);
        recorder.finish(second);

        assertEquals(2, requestCounterCount());
    }

    private EndpointProcessData processData(String requestId, String endpoint) {
        return EndpointProcessData.builder()
                .requestId(requestId)
                .endpoint(endpoint)
                .model("model-a")
                .supplier("supplier-a")
                .channelCode("channel-a")
                .deployName("deploy-a")
                .forwardHost("host-a")
                .forwardPath("/provider/chat")
                .protocol("openai")
                .responseMillis(100)
                .build();
    }

    private OpenapiResponse errorResponse(int httpStatus) {
        return OpenapiResponse.errorResponse(OpenapiResponse.OpenapiError.builder()
                .httpCode(httpStatus)
                .code(String.valueOf(httpStatus))
                .message("failed")
                .build());
    }

    private void assertRequestStatus(String endpoint, String statusClass, String httpStatus) {
        assertCounter(1, "bella_channel_requests",
                "api_endpoint", endpoint,
                "status_class", statusClass,
                "http_status_code", httpStatus);
    }

    private void assertTokenCounter(double expected, String endpoint, String type) {
        assertCounter(expected, "bella_channel_tokens", "api_endpoint", endpoint, "type", type);
    }

    private void assertCounter(double expected, String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        assertNotNull(counter);
        assertEquals(expected, counter.count(), 0.0001);
    }

    private void assertSummary(long expectedCount, double expectedTotal, String name, String... tags) {
        DistributionSummary summary = registry.find(name).tags(tags).summary();
        assertNotNull(summary);
        assertEquals(expectedCount, summary.count());
        assertEquals(expectedTotal, summary.totalAmount(), 0.0001);
    }

    private long requestCounterCount() {
        return registry.find("bella_channel_requests").counters().stream()
                .mapToLong(counter -> (long) counter.count())
                .sum();
    }
}
