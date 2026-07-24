package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KeAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void normalizeThinking_enableThinkingHasHighestPriorityAndClearsTopLevelFields() {
        CompletionRequest request = new CompletionRequest();
        request.setEnable_thinking(false);
        request.setThinking(mapOf("type", "enabled"));
        request.setReasoning_effort("high");

        KeAdaptor.normalizeThinkingOptions(request);

        assertThat(enableThinking(request)).isEqualTo(false);
        assertThat(request.getEnable_thinking()).isNull();
        assertThat(request.getThinking()).isNull();
        assertThat(request.getReasoning_effort()).isNull();
    }

    @Test
    public void normalizeThinking_thinkingOverridesReasoningEffort() {
        CompletionRequest request = new CompletionRequest();
        request.setThinking(mapOf("type", "disabled"));
        request.setReasoning_effort("high");

        KeAdaptor.normalizeThinkingOptions(request);

        assertThat(enableThinking(request)).isEqualTo(false);
    }

    @Test
    public void normalizeThinking_reasoningEffortEnablesAndMissingFieldsDefaultsFalse() {
        CompletionRequest reasoningRequest = new CompletionRequest();
        reasoningRequest.setReasoning_effort("medium");
        KeAdaptor.normalizeThinkingOptions(reasoningRequest);

        CompletionRequest defaultRequest = new CompletionRequest();
        KeAdaptor.normalizeThinkingOptions(defaultRequest);

        assertThat(enableThinking(reasoningRequest)).isEqualTo(true);
        assertThat(enableThinking(defaultRequest)).isEqualTo(false);
    }

    @Test
    public void normalizeThinking_preservesChatTemplateKwargsAndOtherExtraBody() {
        CompletionRequest request = new CompletionRequest();
        Map<String, Object> chatTemplateKwargs = new HashMap<>();
        chatTemplateKwargs.put("temperature_scale", 0.8);
        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("chat_template_kwargs", chatTemplateKwargs);
        extraBody.put("top_k", 20);
        request.setExtra_body(extraBody);

        KeAdaptor.normalizeThinkingOptions(request);

        assertThat(enableThinking(request)).isEqualTo(false);
        assertThat(chatTemplateKwargs(request)).containsEntry("temperature_scale", 0.8);
        assertThat(request.getExtra_body().get("top_k")).isEqualTo(20);
    }

    @Test
    public void messagesThinkingEnabled_convertsThroughKeCompletionExtraBody() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(MessageRequest.ThinkingConfig.enabled(1024));

        assertThat(enableThinking(captured)).isEqualTo(true);
        assertThat(captured.getReasoning_effort()).isNull();
    }

    @Test
    public void messagesThinkingDisabled_convertsThroughKeCompletionExtraBody() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(
                MessageRequest.ThinkingConfig.builder().type("disabled").build());

        assertThat(enableThinking(captured)).isEqualTo(false);
    }

    private CompletionRequest runMessageRequestThroughKeCompletion(MessageRequest.ThinkingConfig thinking) {
        TestOpenAIAdaptor openAIAdaptor = new TestOpenAIAdaptor();

        KeAdaptor completionAdaptor = new KeAdaptor();
        ReflectionTestUtils.setField(completionAdaptor, "openAIAdaptor", openAIAdaptor);

        com.ke.bella.openapi.protocol.message.KeAdaptor messageAdaptor = new com.ke.bella.openapi.protocol.message.KeAdaptor();
        ReflectionTestUtils.setField(messageAdaptor, "delegator", completionAdaptor);

        MessageRequest request = MessageRequest.builder()
                .model("ke-model")
                .messages(Arrays.asList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content("hello")
                        .build()))
                .thinking(thinking)
                .build();
        EndpointContext.setEndpointData("/v1/messages", request.getModel(), request);

        MessageResponse response = messageAdaptor.createMessages(request, "url", new OpenAIProperty());

        assertThat(response.getModel()).isEqualTo("ke-model");
        return openAIAdaptor.capturedRequest;
    }

    private class TestOpenAIAdaptor extends OpenAIAdaptor {
        private CompletionRequest capturedRequest;

        @Override
        public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.HttpDelegator delegator) {
            capturedRequest = request;
            return completionResponse();
        }
    }

    private CompletionResponse completionResponse() {
        return CompletionResponse.builder()
                .id("chatcmpl-test")
                .choices(Arrays.asList(CompletionResponse.Choice.builder()
                        .message(Message.builder()
                                .role("assistant")
                                .content("ok")
                                .build())
                        .finish_reason("stop")
                        .build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Boolean enableThinking(CompletionRequest request) {
        return (Boolean) chatTemplateKwargs(request).get("enable_thinking");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatTemplateKwargs(CompletionRequest request) {
        return (Map<String, Object>) request.getExtra_body().get("chat_template_kwargs");
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
