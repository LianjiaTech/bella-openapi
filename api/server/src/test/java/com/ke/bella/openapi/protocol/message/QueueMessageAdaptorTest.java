package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;

public class QueueMessageAdaptorTest {

    @Test
    public void createMessages_setsCompletionResponseForLogging() {
        EndpointProcessData processData = new EndpointProcessData();
        MessageResponse response = MessageResponse.builder()
                .id("msg-test")
                .model("glm-5.1")
                .role("assistant")
                .usage(MessageResponse.Usage.builder()
                        .inputTokens(13)
                        .outputTokens(18)
                        .cacheCreationInputTokens(2)
                        .cacheReadInputTokens(3)
                        .build())
                .build();

        FakeMessageDelegator delegate = new FakeMessageDelegator(response);
        QueueMessageAdaptor<CompletionProperty> adaptor = new QueueMessageAdaptor<>(delegate, null, processData);

        MessageResponse actual = adaptor.createMessages(new MessageRequest(), "url", new CompletionProperty());

        assertEquals(response, actual);
        assertTrue(delegate.httpDelegatorProvided);
        assertTrue(processData.getResponse() instanceof CompletionResponse);
        CompletionResponse completionResponse = (CompletionResponse) processData.getResponse();
        assertEquals(18, completionResponse.getUsage().getPrompt_tokens());
        assertEquals(18, completionResponse.getUsage().getCompletion_tokens());
        assertEquals(36, completionResponse.getUsage().getTotal_tokens());
        assertEquals(2, completionResponse.getUsage().getCache_creation_tokens());
        assertEquals(3, completionResponse.getUsage().getCache_read_tokens());
    }

    private static class FakeMessageDelegator implements MessageDelegator<CompletionProperty> {
        private final MessageResponse response;
        private boolean httpDelegatorProvided;

        private FakeMessageDelegator(MessageResponse response) {
            this.response = response;
        }

        @Override
        public MessageResponse createMessages(MessageRequest request, String url, CompletionProperty property) {
            return response;
        }

        @Override
        public MessageResponse createMessages(MessageRequest request, String url, CompletionProperty property,
                Callbacks.HttpDelegator httpDelegator) {
            httpDelegatorProvided = httpDelegator != null;
            return response;
        }

        @Override
        public void streamMessages(MessageRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback) {
        }

        @Override
        public void streamMessages(MessageRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator streamDelegator) {
        }

        @Override
        public String getDescription() {
            return "fake";
        }

        @Override
        public Class<?> getPropertyClass() {
            return CompletionProperty.class;
        }
    }
}
