package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class HuoshanAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void propertyDefaultsToDisabledAndDescribesConversion() {
        HuoshanProperty property = new HuoshanProperty();

        assertThat(property.isConvertDeveloperToSystem()).isFalse();
        assertThat(property.description())
                .containsEntry("convertDeveloperToSystem", "是否将developer role转换为system role");
    }

    @Test
    public void completionConvertsAllDeveloperRolesAndPreservesOtherMessageData() {
        TestOpenAIAdaptor openAIAdaptor = new TestOpenAIAdaptor();
        HuoshanAdaptor adaptor = completionAdaptor(openAIAdaptor);
        Message firstDeveloper = Message.builder().role("developer").content("first").name("dev-name").build();
        Message user = Message.builder().role("user").content("question").build();
        Message secondDeveloper = Message.builder().role("developer").content(Collections.singletonMap("key", "value")).build();
        Message assistant = Message.builder().role("assistant").content("answer").build();
        Message tool = Message.builder().role("tool").content("result").tool_call_id("call-1").build();
        Message system = Message.builder().role("system").content("system prompt").build();
        CompletionRequest request = CompletionRequest.builder()
                .messages(Arrays.asList(firstDeveloper, user, secondDeveloper, assistant, tool, system))
                .build();

        adaptor.completion(request, "url", enabledProperty());

        assertThat(openAIAdaptor.capturedCompletionRequest).isSameAs(request);
        assertThat(request.getMessages()).containsExactly(firstDeveloper, user, secondDeveloper, assistant, tool, system);
        assertThat(request.getMessages()).extracting(Message::getRole)
                .containsExactly("system", "user", "system", "assistant", "tool", "system");
        assertThat(firstDeveloper.getContent()).isEqualTo("first");
        assertThat(firstDeveloper.getName()).isEqualTo("dev-name");
        assertThat(secondDeveloper.getContent()).isEqualTo(Collections.singletonMap("key", "value"));
        assertThat(tool.getTool_call_id()).isEqualTo("call-1");
    }

    @Test
    public void completionKeepsDeveloperRoleWhenConversionDisabledOrMissing() {
        TestOpenAIAdaptor openAIAdaptor = new TestOpenAIAdaptor();
        HuoshanAdaptor adaptor = completionAdaptor(openAIAdaptor);
        HuoshanProperty disabledProperty = new HuoshanProperty();
        disabledProperty.setConvertDeveloperToSystem(false);
        CompletionRequest disabledRequest = requestWithRole("developer");
        CompletionRequest defaultRequest = requestWithRole("developer");

        adaptor.completion(disabledRequest, "url", disabledProperty);
        adaptor.completion(defaultRequest, "url", new HuoshanProperty());

        assertThat(disabledRequest.getMessages()).extracting(Message::getRole).containsExactly("developer");
        assertThat(defaultRequest.getMessages()).extracting(Message::getRole).containsExactly("developer");
    }

    @Test
    public void streamCompletionUsesSameDeveloperRoleConversion() {
        TestOpenAIAdaptor openAIAdaptor = new TestOpenAIAdaptor();
        HuoshanAdaptor adaptor = completionAdaptor(openAIAdaptor);
        CompletionRequest request = requestWithRole("developer");

        adaptor.streamCompletion(request, "url", enabledProperty(), new NoopStreamCallback());

        assertThat(openAIAdaptor.capturedStreamRequest).isSameAs(request);
        assertThat(request.getMessages()).extracting(Message::getRole).containsExactly("system");
    }

    @Test
    public void messageDelegationUsesSamePropertyAndConversionForCompletionAndStream() {
        TestOpenAIAdaptor openAIAdaptor = new TestOpenAIAdaptor();
        HuoshanAdaptor completionAdaptor = completionAdaptor(openAIAdaptor);
        com.ke.bella.openapi.protocol.message.HuoshanAdaptor messageAdaptor =
                new com.ke.bella.openapi.protocol.message.HuoshanAdaptor();
        ReflectionTestUtils.setField(messageAdaptor, "delegator", completionAdaptor);
        HuoshanProperty property = enabledProperty();

        MessageRequest completionRequest = messageRequest("developer", false);
        EndpointContext.setEndpointData("/v1/messages", completionRequest.getModel(), completionRequest);
        MessageResponse response = messageAdaptor.createMessages(completionRequest, "url", property);

        assertThat(response.getModel()).isEqualTo("huoshan-model");
        assertThat(openAIAdaptor.capturedCompletionRequest.getMessages()).extracting(Message::getRole)
                .containsExactly("system");

        MessageRequest streamRequest = messageRequest("developer", true);
        EndpointContext.clearAll();
        EndpointContext.setEndpointData("/v1/messages", streamRequest.getModel(), streamRequest);
        messageAdaptor.streamMessages(streamRequest, "url", property, new NoopStreamCallback());

        assertThat(openAIAdaptor.capturedStreamRequest.getMessages()).extracting(Message::getRole)
                .containsExactly("system");
    }

    private HuoshanAdaptor completionAdaptor(TestOpenAIAdaptor openAIAdaptor) {
        HuoshanAdaptor adaptor = new HuoshanAdaptor();
        ReflectionTestUtils.setField(adaptor, "openAIAdaptor", openAIAdaptor);
        return adaptor;
    }

    private HuoshanProperty enabledProperty() {
        HuoshanProperty property = new HuoshanProperty();
        property.setConvertDeveloperToSystem(true);
        return property;
    }

    private CompletionRequest requestWithRole(String role) {
        return CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder().role(role).content("content").build()))
                .build();
    }

    private MessageRequest messageRequest(String role, boolean stream) {
        return MessageRequest.builder()
                .model("huoshan-model")
                .messages(Collections.singletonList(MessageRequest.InputMessage.builder()
                        .role(role)
                        .content("content")
                        .build()))
                .stream(stream)
                .build();
    }

    private CompletionResponse completionResponse() {
        return CompletionResponse.builder()
                .id("chatcmpl-test")
                .choices(Collections.singletonList(CompletionResponse.Choice.builder()
                        .message(Message.builder().role("assistant").content("ok").build())
                        .finish_reason("stop")
                        .build()))
                .build();
    }

    private class TestOpenAIAdaptor extends OpenAIAdaptor {
        private CompletionRequest capturedCompletionRequest;
        private CompletionRequest capturedStreamRequest;

        @Override
        public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
            capturedCompletionRequest = request;
            return completionResponse();
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property,
                Callbacks.StreamCompletionCallback callback) {
            capturedStreamRequest = request;
        }
    }

    private static class NoopStreamCallback implements Callbacks.StreamCompletionCallback {
        @Override
        public void onOpen() {
        }

        @Override
        public void callback(StreamCompletionResponse msg) {
        }

        @Override
        public void done() {
        }

        @Override
        public void finish() {
        }

        @Override
        public void finish(com.ke.bella.openapi.common.exception.BellaException exception) {
        }

        @Override
        public void send(Object data) {
        }
    }
}
