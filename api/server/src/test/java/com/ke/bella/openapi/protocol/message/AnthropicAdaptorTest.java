package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AnthropicProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;

public class AnthropicAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void streamMessages_queueDelegatorReceivesPreparedRequest() {
        EndpointContext.setEndpointData("/v1/messages", "GLM-5.1", new MessageRequest());

        AnthropicAdaptor adaptor = new AnthropicAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(false)
                .build();
        AnthropicProperty property = new AnthropicProperty();
        property.setDeployName("glm-5.1-deploy");
        property.setDefaultMaxToken(2000);
        property.setAnthropicVersion("2023-06-01");

        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        Callbacks.StreamDelegator streamDelegator = (req, listener) -> capturedRequest.set(req);

        adaptor.streamMessages(request, "url", property, new NoopStreamCallback(), streamDelegator);

        assertSame(request, capturedRequest.get());
        assertEquals("glm-5.1-deploy", request.getModel());
        assertTrue(request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
        assertSerializedBodyHasNoAnthropicVersion(request);
    }

    @Test
    public void createMessages_queueDelegatorReceivesPreparedRequest() {
        AnthropicAdaptor adaptor = new AnthropicAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(true)
                .build();
        AnthropicProperty property = new AnthropicProperty();
        property.setDeployName("glm-5.1-deploy");
        property.setDefaultMaxToken(2000);
        property.setAnthropicVersion("2023-06-01");

        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        Callbacks.HttpDelegator httpDelegator = new Callbacks.HttpDelegator() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                capturedRequest.set(req);
                return (T) MessageResponse.builder().id("msg-test").build();
            }
        };

        adaptor.createMessages(request, "url", property, httpDelegator);

        assertSame(request, capturedRequest.get());
        assertEquals("glm-5.1-deploy", request.getModel());
        assertEquals(false, request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
        assertSerializedBodyHasNoAnthropicVersion(request);
    }

    @Test
    public void streamListener_convertsAnthropicObjectEvent() {
        CapturingStreamCallback callback = new CapturingStreamCallback();
        AnthropicAdaptor.AnthropicStreamListener listener = new AnthropicAdaptor.AnthropicStreamListener(callback, "claude-test", false);

        listener.onEvent(null, null, "message_start", serialize(StreamMessageResponse.messageStart(message())));
        listener.onEvent(null, null, "content_block_delta",
                serialize(StreamMessageResponse.contentBlockDelta(0, new StreamMessageResponse.TextDelta("hello"))));

        assertEquals(0, callback.doneCount);
        assertEquals(1, callback.responses.size());
        StreamCompletionResponse response = callback.responses.get(0);
        assertEquals("msg-test", response.getId());
        assertEquals("claude-test", response.getModel());
        assertEquals("hello", response.content());
    }

    @Test
    public void streamListener_doneFlagCompletesBeforeDeserialization() {
        CapturingStreamCallback callback = new CapturingStreamCallback();
        AnthropicAdaptor.AnthropicStreamListener listener = new AnthropicAdaptor.AnthropicStreamListener(callback, "claude-test", false);

        listener.onEvent(null, null, null, "[DONE]");

        assertEquals(1, callback.doneCount);
        assertEquals(0, callback.responses.size());
        assertEquals(0, callback.sent.size());
    }

    @Test
    public void streamListener_messageStopAndDoneFlagCompleteOnce() {
        CapturingStreamCallback callback = new CapturingStreamCallback();
        AnthropicAdaptor.AnthropicStreamListener listener = new AnthropicAdaptor.AnthropicStreamListener(callback, "claude-test", true);

        listener.onEvent(null, null, "message_stop", serialize(StreamMessageResponse.messageStop()));
        listener.onEvent(null, null, null, "[DONE]");
        listener.onEvent(null, null, null, "[DONE]");

        assertEquals(1, callback.doneCount);
        assertEquals(1, callback.sent.size());
        assertEquals("message_stop", ((StreamMessageResponse) callback.sent.get(0)).getType());
    }

    @Test
    public void streamListener_doneFlagSuppressesLaterMessageStop() {
        CapturingStreamCallback callback = new CapturingStreamCallback();
        AnthropicAdaptor.AnthropicStreamListener listener = new AnthropicAdaptor.AnthropicStreamListener(callback, "claude-test", true);

        listener.onEvent(null, null, null, "[DONE]");
        listener.onEvent(null, null, "message_stop", serialize(StreamMessageResponse.messageStop()));

        assertEquals(1, callback.doneCount);
        assertEquals(0, callback.sent.size());
    }

    private void assertSerializedBodyHasNoAnthropicVersion(MessageRequest request) {
        String body = new String(JacksonUtils.toByte(request), StandardCharsets.UTF_8);
        assertFalse(body.contains("anthropic_version"));
    }

    private static MessageResponse message() {
        MessageResponse.Usage usage = MessageResponse.Usage.builder()
                .inputTokens(7)
                .outputTokens(0)
                .build();
        return MessageResponse.builder()
                .id("msg-test")
                .type("message")
                .role("assistant")
                .model("upstream-claude")
                .content(Collections.emptyList())
                .usage(usage)
                .build();
    }

    private static String serialize(Object data) {
        String json = JacksonUtils.serialize(data);
        assertNotNull(json);
        return json;
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

    private static class CapturingStreamCallback implements Callbacks.StreamCompletionCallback {
        private final List<StreamCompletionResponse> responses = new ArrayList<>();
        private final List<Object> sent = new ArrayList<>();
        private int doneCount;

        @Override
        public void onOpen() {
        }

        @Override
        public void callback(StreamCompletionResponse msg) {
            responses.add(msg);
        }

        @Override
        public void done() {
            doneCount++;
        }

        @Override
        public void finish() {
        }

        @Override
        public void finish(BellaException exception) {
        }

        @Override
        public void send(Object data) {
            sent.add(data);
        }
    }
}
