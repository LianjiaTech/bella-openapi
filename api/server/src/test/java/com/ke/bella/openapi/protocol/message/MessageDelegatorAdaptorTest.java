package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

public class MessageDelegatorAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void createMessages_queueNativeProxyPreparesAnthropicRequest() {
        FakeMessageDelegatorAdaptor adaptor = new FakeMessageDelegatorAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(true)
                .build();
        CompletionProperty property = nativeProxyProperty();
        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        Callbacks.HttpDelegator httpDelegator = new Callbacks.HttpDelegator() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R request(Object req, Class<R> clazz, Callbacks.ChannelErrorCallback<R> errorCallback) {
                capturedRequest.set(req);
                return (R) MessageResponse.builder().id("msg-test").build();
            }
        };

        adaptor.createMessages(request, "url", property, httpDelegator);

        assertSame(request, capturedRequest.get());
        assertEquals("glm-5.1-deploy", request.getModel());
        assertEquals(false, request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
        assertFalse(adaptor.completionAdaptor.completionCalled);
    }

    @Test
    public void streamMessages_queueNativeProxyPreparesAnthropicRequest() {
        EndpointContext.setEndpointData("/v1/messages", "GLM-5.1", new MessageRequest());

        FakeMessageDelegatorAdaptor adaptor = new FakeMessageDelegatorAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(false)
                .build();
        CompletionProperty property = nativeProxyProperty();
        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        Callbacks.StreamDelegator streamDelegator = (req, listener) -> capturedRequest.set(req);

        adaptor.streamMessages(request, "url", property, new NoopStreamCallback(), streamDelegator);

        assertSame(request, capturedRequest.get());
        assertEquals("glm-5.1-deploy", request.getModel());
        assertTrue(request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
        assertFalse(adaptor.completionAdaptor.streamCompletionCalled);
    }

    private CompletionProperty nativeProxyProperty() {
        CompletionProperty property = new CompletionProperty();
        property.setMessageEndpointUrl("https://example.test/v1/messages");
        property.setDeployName("glm-5.1-deploy");
        property.setDefaultMaxToken(2000);
        property.setAnthropicVersion("2023-06-01");
        return property;
    }

    private static class FakeMessageDelegatorAdaptor implements MessageDelegatorAdaptor<CompletionProperty> {
        private final FakeCompletionAdaptor completionAdaptor = new FakeCompletionAdaptor();
        private final AnthropicAdaptor anthropicAdaptor = new AnthropicAdaptor();

        @Override
        public CompletionAdaptor<CompletionProperty> delegator() {
            return completionAdaptor;
        }

        @Override
        public AnthropicAdaptor anthropicAdaptor() {
            return anthropicAdaptor;
        }

        @Override
        public boolean isNativeSupport() {
            return false;
        }

        @Override
        public String getDescription() {
            return "fake";
        }
    }

    private static class FakeCompletionAdaptor implements CompletionAdaptor<CompletionProperty> {
        private boolean completionCalled;
        private boolean streamCompletionCalled;

        @Override
        public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
            completionCalled = true;
            return CompletionResponse.builder().build();
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback) {
            streamCompletionCalled = true;
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
