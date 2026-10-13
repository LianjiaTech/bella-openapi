package com.ke.bella.openapi.protocol.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;

public class AnthropicVertexAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void completionConvertsRequestAndReturnsDelegatedResponse() {
        CapturingMessageAdaptor messageAdaptor = new CapturingMessageAdaptor();
        AnthropicVertexAdaptor adaptor = adaptor(messageAdaptor);
        CompletionRequest request = request(false);
        CompletionResponse expected = CompletionResponse.builder().id("chatcmpl-vertex").build();
        AnthropicVertexProperty property = property();
        messageAdaptor.completionResponse = expected;

        CompletionResponse actual = adaptor.completion(request, "https://vertex.example/model", property);

        assertSame(expected, actual);
        assertEquals("claude-sonnet", messageAdaptor.request.getModel());
        assertEquals(Integer.valueOf(2048), messageAdaptor.request.getMaxTokens());
        assertEquals("user", messageAdaptor.request.getMessages().get(0).getRole());
        assertEquals("hello", messageAdaptor.request.getMessages().get(0).getContent());
        assertEquals("https://vertex.example/model", messageAdaptor.url);
        assertSame(property, messageAdaptor.property);
    }

    @Test
    public void streamCompletionConvertsRequestAndReusesCallback() {
        CapturingMessageAdaptor messageAdaptor = new CapturingMessageAdaptor();
        AnthropicVertexAdaptor adaptor = adaptor(messageAdaptor);
        CompletionRequest request = request(true);
        NoopStreamCallback callback = new NoopStreamCallback();
        AnthropicVertexProperty property = property();

        adaptor.streamCompletion(request, "https://vertex.example/model", property, callback);

        assertTrue(messageAdaptor.request.getStream());
        assertEquals("claude-sonnet", messageAdaptor.request.getModel());
        assertSame(callback, messageAdaptor.callback);
        assertSame(property, messageAdaptor.property);
    }

    @Test
    public void queueCompletionEnqueuesOriginalCompletionRequest() {
        CapturingMessageAdaptor messageAdaptor = new CapturingMessageAdaptor();
        AnthropicVertexAdaptor adaptor = adaptor(messageAdaptor);
        CompletionRequest request = request(false);
        CompletionResponse expected = CompletionResponse.builder().id("chatcmpl-queue").build();
        AtomicReference<Object> queuedRequest = new AtomicReference<>();

        CompletionResponse actual = adaptor.completion(request, "ignored", property(), new Callbacks.HttpDelegator() {
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                queuedRequest.set(req);
                return clazz.cast(expected);
            }
        });

        assertSame(expected, actual);
        assertSame(request, queuedRequest.get());
        assertEquals("claude-sonnet", request.getModel());
        assertEquals(null, messageAdaptor.request);
    }

    @Test
    public void directPassthroughIsDisabledBecauseVertexRequiresRequestConversion() {
        AnthropicVertexAdaptor adaptor = adaptor(new CapturingMessageAdaptor());

        assertFalse(adaptor.supportsDirectPassthrough());
    }

    @Test
    public void queueStreamEnqueuesOriginalCompletionRequestAndParsesWorkerChunks() {
        CapturingMessageAdaptor messageAdaptor = new CapturingMessageAdaptor();
        AnthropicVertexAdaptor adaptor = adaptor(messageAdaptor);
        CompletionRequest request = request(true);
        CapturingStreamCallback callback = new CapturingStreamCallback();
        AtomicReference<Object> queuedRequest = new AtomicReference<>();
        AtomicReference<BellaEventSourceListener> queuedListener = new AtomicReference<>();

        adaptor.streamCompletion(request, "ignored", property(), callback, (req, listener) -> {
            queuedRequest.set(req);
            queuedListener.set(listener);
        });

        StreamCompletionResponse chunk = StreamCompletionResponse.builder().id("chatcmpl-queue").model("claude-sonnet").build();
        queuedListener.get().onEvent(null, null, "message", com.ke.bella.openapi.utils.JacksonUtils.serialize(chunk));
        queuedListener.get().onEvent(null, null, "message", "[DONE]");

        assertSame(request, queuedRequest.get());
        assertEquals("claude-sonnet", request.getModel());
        assertEquals(null, messageAdaptor.request);
        assertEquals(chunk.getId(), callback.response.getId());
        assertEquals(1, callback.doneCount);
    }

    @Test
    public void protocolRegistersSameCodeOnMessagesAndCompletionsEndpoints() {
        com.ke.bella.openapi.protocol.message.AnthropicVertexAdaptor messageAdaptor =
                new com.ke.bella.openapi.protocol.message.AnthropicVertexAdaptor();
        AnthropicVertexAdaptor completionAdaptor = new AnthropicVertexAdaptor();
        AdaptorManager manager = AdaptorManager.getInstance();

        manager.register(messageAdaptor.endpoint(), messageAdaptor);
        manager.register(completionAdaptor.endpoint(), completionAdaptor);

        String protocol = EntityConstants.Protocol.ANTHROPIC_VERTEX.getCode();
        assertEquals("AnthropicVertexAdaptor", protocol);
        assertTrue(completionAdaptor instanceof CompletionAdaptorDelegator);
        assertSame(messageAdaptor, manager.getProtocolAdaptor("/v1/messages", protocol));
        assertSame(completionAdaptor, manager.getProtocolAdaptor("/v1/chat/completions", protocol));
        assertEquals("AnthropicVertexCompletion", AnthropicVertexAdaptor.class.getAnnotation(Component.class).value());
    }

    private static AnthropicVertexAdaptor adaptor(CapturingMessageAdaptor messageAdaptor) {
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        ReflectionTestUtils.setField(adaptor, "delegator", messageAdaptor);
        return adaptor;
    }

    private static CompletionRequest request(boolean stream) {
        return CompletionRequest.builder()
                .model("claude-sonnet")
                .max_tokens(2048)
                .stream(stream)
                .messages(Collections.singletonList(Message.builder().role("user").content("hello").build()))
                .build();
    }

    private static AnthropicVertexProperty property() {
        return new AnthropicVertexProperty();
    }

    private static class CapturingMessageAdaptor extends com.ke.bella.openapi.protocol.message.AnthropicVertexAdaptor {
        private MessageRequest request;
        private String url;
        private AnthropicVertexProperty property;
        private Callbacks.StreamCompletionCallback callback;
        private CompletionResponse completionResponse;

        @Override
        public MessageResponse createMessages(MessageRequest request, String url, AnthropicVertexProperty property) {
            this.request = request;
            this.url = url;
            this.property = property;
            EndpointContext.getProcessData().setResponse(completionResponse);
            return MessageResponse.builder().id("msg-vertex").build();
        }

        @Override
        public void streamMessages(MessageRequest request, String url, AnthropicVertexProperty property,
                Callbacks.StreamCompletionCallback callback) {
            this.request = request;
            this.url = url;
            this.property = property;
            this.callback = callback;
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
        public void finish(BellaException exception) {
        }

        @Override
        public void send(Object data) {
        }
    }

    private static class CapturingStreamCallback extends NoopStreamCallback {
        private StreamCompletionResponse response;
        private int doneCount;

        @Override
        public void callback(StreamCompletionResponse msg) {
            response = msg;
        }

        @Override
        public void done() {
            doneCount++;
        }
    }
}
