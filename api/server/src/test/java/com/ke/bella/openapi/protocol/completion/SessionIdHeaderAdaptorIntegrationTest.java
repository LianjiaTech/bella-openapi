package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.SessionIdService;
import okhttp3.Request;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdHeaderAdaptorIntegrationTest {
    private static final String SESSION_HEADER = "X-Prompt-Cache-Id";

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void openAIAdaptorSharedBuilderInjectsConfiguredHeader() {
        setHeaderContext(SESSION_HEADER, "explicit-id");
        OpenAIProperty property = openAIProperty();

        Request outbound = ReflectionTestUtils.invokeMethod(new OpenAIAdaptor(), "buildRequest",
                CompletionRequest.builder().build(), "http://localhost/chat", property);

        assertSingleSessionHeader(outbound, "explicit-id");
    }

    @Test
    public void responsesApiAdaptorSharedBuilderInjectsConfiguredHeader() {
        setHeaderContext("session-id", "codex-id");
        ResponsesApiProperty property = responsesApiProperty();

        Request outbound = ReflectionTestUtils.invokeMethod(new ResponsesApiAdaptor(), "buildResponsesApiRequest",
                new ResponsesApiRequest(), "http://localhost/responses", property);

        assertSingleSessionHeader(outbound, "codex-id");
    }

    @Test
    public void explicitPromptCacheKeyFallbackIsForwarded() {
        ResponsesApiRequest request = ResponsesApiRequest.builder().prompt_cache_key("body-cache-id").build();
        SessionIdService.resolveForResponses(request, 64, 1);
        ResponsesApiProperty property = responsesApiProperty();

        Request outbound = ReflectionTestUtils.invokeMethod(new OpenAIResponsesAdaptor(), "buildRequest",
                request, "http://localhost/responses", property);

        assertSingleSessionHeader(outbound, "body-cache-id");
    }

    @Test
    public void openAIResponsesAdaptorSharedBuilderInjectsConfiguredHeader() {
        setHeaderContext("x-claude-code-session-id", "claude-id");
        ResponsesApiProperty property = responsesApiProperty();

        Request outbound = ReflectionTestUtils.invokeMethod(new OpenAIResponsesAdaptor(), "buildRequest",
                new ResponsesApiRequest(), "http://localhost/responses", property);

        assertSingleSessionHeader(outbound, "claude-id");
    }

    private void setHeaderContext(String header, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(header, value);
        EndpointContext.setRequest(request);
    }

    private OpenAIProperty openAIProperty() {
        OpenAIProperty property = new OpenAIProperty();
        property.setDeployName("test-model");
        property.setSessionId(SESSION_HEADER);
        property.setExtraHeaders(Collections.singletonMap(SESSION_HEADER, "configured-id"));
        return property;
    }

    private ResponsesApiProperty responsesApiProperty() {
        ResponsesApiProperty property = new ResponsesApiProperty();
        property.setDeployName("test-model");
        property.setSessionId(SESSION_HEADER);
        property.setExtraHeaders(Collections.singletonMap(SESSION_HEADER, "configured-id"));
        return property;
    }

    private void assertSingleSessionHeader(Request outbound, String expected) {
        assertThat(outbound.headers(SESSION_HEADER)).containsExactly(expected);
    }
}
