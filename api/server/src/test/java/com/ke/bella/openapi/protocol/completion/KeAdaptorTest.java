package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.protocol.message.TransferFromCompletionsUtils;
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

        KeAdaptor.normalizeThinkingOptions(request, false);

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

        KeAdaptor.normalizeThinkingOptions(request, false);

        assertThat(enableThinking(request)).isEqualTo(false);
    }

    @Test
    public void normalizeThinking_reasoningEffortEnablesNoneDisablesAndMissingFieldsDefaultsFalse() {
        CompletionRequest reasoningRequest = new CompletionRequest();
        reasoningRequest.setReasoning_effort("medium");
        KeAdaptor.normalizeThinkingOptions(reasoningRequest, false);

        CompletionRequest noneRequest = new CompletionRequest();
        noneRequest.setReasoning_effort("none");
        KeAdaptor.normalizeThinkingOptions(noneRequest, false);

        CompletionRequest defaultRequest = new CompletionRequest();
        KeAdaptor.normalizeThinkingOptions(defaultRequest, false);

        assertThat(enableThinking(reasoningRequest)).isEqualTo(true);
        assertThat(enableThinking(noneRequest)).isEqualTo(false);
        assertThat(enableThinking(defaultRequest)).isEqualTo(false);
    }

    @Test
    public void normalizeThinking_usesChannelDefaultWhenThinkingParametersAreMissing() {
        CompletionRequest request = new CompletionRequest();

        KeAdaptor.normalizeThinkingOptions(request, true);

        assertThat(enableThinking(request)).isTrue();
    }

    @Test
    public void normalizeThinking_explicitParametersOverrideChannelDefault() {
        CompletionRequest enableThinkingRequest = new CompletionRequest();
        enableThinkingRequest.setEnable_thinking(false);
        KeAdaptor.normalizeThinkingOptions(enableThinkingRequest, true);

        CompletionRequest thinkingRequest = new CompletionRequest();
        thinkingRequest.setThinking(mapOf("type", "disabled"));
        KeAdaptor.normalizeThinkingOptions(thinkingRequest, true);

        CompletionRequest reasoningEffortRequest = new CompletionRequest();
        reasoningEffortRequest.setReasoning_effort("none");
        KeAdaptor.normalizeThinkingOptions(reasoningEffortRequest, true);

        assertThat(enableThinking(enableThinkingRequest)).isFalse();
        assertThat(enableThinking(thinkingRequest)).isFalse();
        assertThat(enableThinking(reasoningEffortRequest)).isFalse();
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

        KeAdaptor.normalizeThinkingOptions(request, false);

        assertThat(enableThinking(request)).isEqualTo(false);
        assertThat(chatTemplateKwargs(request)).containsEntry("temperature_scale", 0.8);
        assertThat(request.getExtra_body().get("top_k")).isEqualTo(20);
    }

    @Test
    public void messagesMissingThinking_usesChannelDefault() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(null, null, false, false, true);

        assertThat(enableThinking(captured)).isTrue();
    }

    @Test
    public void streamingMessagesMissingThinking_usesChannelDefault() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(null, null, false, true, true);

        assertThat(enableThinking(captured)).isTrue();
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

    @Test
    public void messagesOutputConfigEffort_convertsForEnabledKeChannel() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(null, "high", true, false);

        assertThat(captured.getReasoning_effort()).isNull();
        assertThat(enableThinking(captured)).isTrue();
    }

    @Test
    public void messagesOutputConfigEffort_isIgnoredWhenChannelSupportIsDisabled() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(null, "high", false, false);

        assertThat(captured.getReasoning_effort()).isNull();
        assertThat(enableThinking(captured)).isFalse();
    }

    @Test
    public void messagesThinking_takesPriorityOverOutputConfigEffort() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(
                MessageRequest.ThinkingConfig.builder().type("disabled").build(), "high", true, false);

        assertThat(captured.getReasoning_effort()).isNull();
        assertThat(enableThinking(captured)).isFalse();
    }

    @Test
    public void streamingMessagesOutputConfigEffort_usesSameConversion() {
        CompletionRequest captured = runMessageRequestThroughKeCompletion(null, "max", true, true);

        assertThat(captured.getReasoning_effort()).isNull();
        assertThat(enableThinking(captured)).isTrue();
    }

    @Test
    public void converter_mapsAllSupportedEffortValuesWhenEnabled() {
        for(String effort : Arrays.asList("low", "medium", "high", "max")) {
            MessageRequest request = MessageRequest.builder()
                    .outputConfig(MessageRequest.OutputConfig.builder().effort(effort).build())
                    .build();

            assertThat(TransferFromCompletionsUtils.convertRequest(request, false, true).getReasoning_effort())
                    .isEqualTo(effort);
        }
    }

    @Test
    public void converter_keepsOutputConfigEffortForOtherAdaptorsDisabled() {
        MessageRequest request = MessageRequest.builder()
                .outputConfig(MessageRequest.OutputConfig.builder().effort("low").build())
                .build();

        assertThat(TransferFromCompletionsUtils.convertRequest(request, false, false).getReasoning_effort()).isNull();
    }

    private CompletionRequest runMessageRequestThroughKeCompletion(MessageRequest.ThinkingConfig thinking) {
        return runMessageRequestThroughKeCompletion(thinking, null, false, false);
    }

    private CompletionRequest runMessageRequestThroughKeCompletion(MessageRequest.ThinkingConfig thinking,
            String effort, boolean supportOutputConfigEffort, boolean stream) {
        return runMessageRequestThroughKeCompletion(thinking, effort, supportOutputConfigEffort, stream, false);
    }

    private CompletionRequest runMessageRequestThroughKeCompletion(MessageRequest.ThinkingConfig thinking,
            String effort, boolean supportOutputConfigEffort, boolean stream, boolean defaultThinkingEnabled) {
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
                .outputConfig(effort == null ? null : MessageRequest.OutputConfig.builder().effort(effort).build())
                .stream(stream)
                .build();
        EndpointContext.setEndpointData("/v1/messages", request.getModel(), request);

        OpenAIProperty property = new OpenAIProperty();
        property.setSupportOutputConfigEffort(supportOutputConfigEffort);
        property.setDefaultThinkingEnabled(defaultThinkingEnabled);
        if(stream) {
            messageAdaptor.streamMessages(request, "url", property, new Callbacks.StreamCompletionCallback() {
                @Override public void onOpen() { }
                @Override public void callback(com.ke.bella.openapi.protocol.completion.StreamCompletionResponse msg) { }
                @Override public void done() { }
                @Override public void finish() { }
                @Override public void finish(com.ke.bella.openapi.common.exception.BellaException exception) { }
                @Override public void send(Object data) { }
            });
            return openAIAdaptor.capturedRequest;
        }

        MessageResponse response = messageAdaptor.createMessages(request, "url", property);

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

        @Override
        public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property,
                Callbacks.StreamCompletionCallback callback) {
            capturedRequest = request;
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property,
                Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator delegator) {
            capturedRequest = request;
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
