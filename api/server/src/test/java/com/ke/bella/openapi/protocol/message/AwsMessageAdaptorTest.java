package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AwsMessageProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

public class AwsMessageAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void createMessages_queueDelegatorReceivesPreparedRequest() {
        AwsMessageAdaptor adaptor = new AwsMessageAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(true)
                .build();
        AwsMessageProperty property = new AwsMessageProperty();
        property.setDefaultMaxToken(2000);
        property.setAnthropicVersion("bedrock-2023-05-31");

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
        assertEquals("GLM-5.1", request.getModel());
        assertEquals(true, request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertEquals("bedrock-2023-05-31", request.getAnthropic_version());
    }

    @Test
    public void streamMessages_queueDelegatorReceivesPreparedRequest() {
        EndpointContext.setEndpointData("/v1/messages", "GLM-5.1", new MessageRequest());

        AwsMessageAdaptor adaptor = new AwsMessageAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("GLM-5.1")
                .stream(true)
                .build();
        AwsMessageProperty property = new AwsMessageProperty();
        property.setDefaultMaxToken(2000);
        property.setAnthropicVersion("bedrock-2023-05-31");

        AtomicReference<Object> capturedRequest = new AtomicReference<>();
        Callbacks.StreamDelegator streamDelegator = (req, listener) -> capturedRequest.set(req);

        adaptor.streamMessages(request, "url", property, new CapturingStreamCallback(), streamDelegator);

        assertSame(request, capturedRequest.get());
        assertEquals("GLM-5.1", request.getModel());
        assertEquals(true, request.getStream());
        assertEquals(Integer.valueOf(2000), request.getMaxTokens());
        assertEquals("bedrock-2023-05-31", request.getAnthropic_version());
    }

    @Test
    public void queueSseListener_onFailureWithoutThrowableOrResponse_finishesWithFallbackException() throws Exception {
        CapturingStreamCallback callback = new CapturingStreamCallback();
        Object listener = newAwsQueueSseListener(callback, "GLM-5.1", false);
        CompletableFuture<Object> future = new CompletableFuture<>();
        Field connectionInitFuture = listener.getClass().getSuperclass().getDeclaredField("connectionInitFuture");
        connectionInitFuture.setAccessible(true);
        connectionInitFuture.set(listener, future);

        Method onFailure = listener.getClass().getDeclaredMethod(
                "onFailure", okhttp3.sse.EventSource.class, Throwable.class, okhttp3.Response.class);
        onFailure.setAccessible(true);
        onFailure.invoke(listener, null, null, null);

        assertTrue(future.isCompletedExceptionally());
        try {
            future.get();
            fail("future should be completed exceptionally");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof BellaException);
            assertEquals(Integer.valueOf(500), ((BellaException) e.getCause()).getHttpCode());
            assertEquals("SSE request failed without response", e.getCause().getMessage());
        }
    }

    private Object newAwsQueueSseListener(Callbacks.StreamCompletionCallback callback, String model, boolean nativeSend) throws Exception {
        Class<?> listenerClass = Class.forName(
                "com.ke.bella.openapi.protocol.message.AwsMessageAdaptor$AwsQueueSseListener");
        Constructor<?> constructor = listenerClass.getDeclaredConstructor(
                Callbacks.StreamCompletionCallback.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(callback, model, nativeSend);
    }

    private static class CapturingStreamCallback implements Callbacks.StreamCompletionCallback {
        private BellaException exception;

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
            this.exception = exception;
        }

        @Override
        public void send(Object data) {
        }
    }
}
