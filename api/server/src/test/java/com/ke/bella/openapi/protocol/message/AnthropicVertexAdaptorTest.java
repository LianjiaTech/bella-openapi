package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AnthropicVertexProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;

import com.sun.net.httpserver.HttpServer;

import okhttp3.Request;
import okhttp3.ResponseBody;

public class AnthropicVertexAdaptorTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void propertyAndProtocolUseVertexDefaults() {
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        AnthropicVertexProperty property = new AnthropicVertexProperty();

        assertEquals("vertex-2023-10-16", property.getAnthropicVersion());
        assertSame(AnthropicVertexProperty.class, adaptor.getPropertyClass());
        assertEquals("/v1/messages", adaptor.endpoint());
        assertEquals(adaptor.getClass().getSimpleName(), EntityConstants.Protocol.ANTHROPIC_VERTEX.getCode());
    }

    @Test
    public void queueDelegatorsReceiveOriginalModelWithoutTransportVersion() {
        EndpointContext.setEndpointData("/v1/messages", "claude-sonnet", new MessageRequest());
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        AnthropicVertexProperty property = property();
        MessageRequest request = MessageRequest.builder().model("claude-sonnet").stream(false).build();
        AtomicReference<Object> captured = new AtomicReference<>();

        adaptor.streamMessages(request, "ignored", property, new NoopStreamCallback(), (req, listener) -> captured.set(req));

        assertSame(request, captured.get());
        assertEquals("claude-sonnet", request.getModel());
        assertTrue(request.getStream());
        assertEquals(Integer.valueOf(8192), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
    }

    @Test
    public void queueStreamEmitsOnlyNativeAnthropicEvents() {
        EndpointContext.setEndpointData("/v1/messages", "claude-sonnet", new MessageRequest());
        EndpointProcessData processData = EndpointContext.getProcessData();
        NativeSequenceCallback callback = new NativeSequenceCallback(processData);
        AtomicReference<com.ke.bella.openapi.protocol.BellaEventSourceListener> capturedListener = new AtomicReference<>();

        new AnthropicVertexAdaptor().streamMessages(
                MessageRequest.builder().model("claude-sonnet").build(),
                "ignored",
                property(),
                callback,
                (req, listener) -> capturedListener.set(listener));

        emitNativeSequence(capturedListener.get());

        assertTrue(processData.isNativeSend());
        assertEquals(list("message_start", "content_block_delta", "message_stop"), callback.events);
    }

    @Test
    public void directStreamEmitsOnlyNativeAnthropicEvents() throws Exception {
        HttpServer server = startSseServer();
        try {
            EndpointContext.setEndpointData("/v1/messages", "claude-sonnet", new MessageRequest());
            EndpointProcessData processData = EndpointContext.getProcessData();
            NativeSequenceCallback callback = new NativeSequenceCallback(processData);

            new AnthropicVertexAdaptor().streamMessages(
                    MessageRequest.builder().model("claude-sonnet").build(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/model",
                    property(),
                    callback);

            assertTrue(callback.finished.await(5, TimeUnit.SECONDS));
            assertTrue(processData.isNativeSend());
            assertEquals(list("message_start", "content_block_delta", "message_stop"), callback.events);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void syncQueueDelegatorReceivesOriginalModelWithoutTransportVersion() {
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        AnthropicVertexProperty property = property();
        MessageRequest request = MessageRequest.builder().model("claude-sonnet").stream(true).build();
        AtomicReference<Object> captured = new AtomicReference<>();

        adaptor.createMessages(request, "ignored", property, new Callbacks.HttpDelegator() {
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                captured.set(req);
                return clazz.cast(MessageResponse.builder().id("msg-test").build());
            }
        });

        assertSame(request, captured.get());
        assertEquals("claude-sonnet", request.getModel());
        assertFalse(request.getStream());
        assertEquals(Integer.valueOf(8192), request.getMaxTokens());
        assertNull(request.getAnthropic_version());
    }

    @Test
    public void transportRequestRemovesModelAndAddsVertexVersionWithoutMutatingSource() {
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        MessageRequest request = MessageRequest.builder()
                .model("claude-sonnet")
                .stream(false)
                .maxTokens(1024)
                .build();

        MessageRequest transportRequest = adaptor.prepareTransportRequest(request, property());
        String body = JacksonUtils.serialize(transportRequest);

        assertEquals("claude-sonnet", request.getModel());
        assertNull(request.getAnthropic_version());
        assertFalse(body.contains("\"model\""));
        assertTrue(body.contains("\"anthropic_version\":\"vertex-2023-10-16\""));
        assertTrue(body.contains("\"stream\":false"));
    }

    @Test
    public void requestUsesVertexUrlBearerAndFiltersAnthropicHeaders() throws Exception {
        EndpointContext.getProcessData().setSessionId("session-value");
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
        AnthropicVertexProperty property = property();
        property.setSessionId("X-Session-Id");
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-Custom", "custom-value");
        extraHeaders.put("X-API-KEY", "forbidden");
        extraHeaders.put("Anthropic-Version", "forbidden");
        property.setExtraHeaders(extraHeaders);

        Request request = adaptor.buildHttpRequest("{}".getBytes(StandardCharsets.UTF_8),
                adaptor.vertexUrl("https://vertex.example/model///", false), property);

        assertEquals("https://vertex.example/model:rawPredict", request.url().toString());
        assertEquals("https://vertex.example/model:streamRawPredict",
                adaptor.vertexUrl("https://vertex.example/model", true));
        assertEquals("Bearer google-token", request.header("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("custom-value", request.header("X-Custom"));
        assertEquals("session-value", request.header("X-Session-Id"));
        assertNull(request.header("x-api-key"));
        assertNull(request.header("anthropic-version"));
        assertEquals(2L, request.body().contentLength());
    }

    @Test
    public void vertexUrlRejectsConfiguredActionSuffix() {
        AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();

        BellaException.ChannelException exception = assertThrows(BellaException.ChannelException.class,
                () -> adaptor.vertexUrl("https://vertex.example/model:streamRawPredict", true));

        assertEquals(Integer.valueOf(400), exception.getHttpCode());
    }

    @Test
    public void syncQueueErrorPreservesGoogleMessageAndStatus() {
        for (int status : new int[] {400, 401, 403, 404, 429, 500}) {
            AnthropicVertexAdaptor adaptor = new AnthropicVertexAdaptor();
            MessageRequest request = MessageRequest.builder().model("claude-sonnet").build();
            MessageResponse error = MessageResponse.builder()
                    .error(com.ke.bella.openapi.protocol.OpenapiResponse.OpenapiError.builder()
                            .message("google error " + status)
                            .build())
                    .build();

            BellaException.ChannelException exception = assertThrows(BellaException.ChannelException.class,
                    () -> adaptor.createMessages(request, "ignored", property(), new ErrorHttpDelegator(error, status)));

            assertEquals(Integer.valueOf(status), exception.getHttpCode());
            assertEquals("google error " + status, exception.getMessage());
            assertEquals("claude-sonnet", request.getModel());
        }
    }

    @Test
    public void streamErrorPreservesGoogleMessageAndStatus() {
        AnthropicVertexAdaptor.AnthropicVertexStreamListener listener = new AnthropicVertexAdaptor.AnthropicVertexStreamListener(
                new NoopStreamCallback(), "claude-sonnet", true);
        okhttp3.Response response = new okhttp3.Response.Builder()
                .request(new Request.Builder().url("https://vertex.example").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body(ResponseBody.create(okhttp3.MediaType.parse("application/json"),
                        "{\"error\":{\"code\":500,\"message\":\"publisher unavailable\",\"status\":\"INTERNAL\"}}"))
                .build();

        BellaException exception = listener.convertToException(response);

        assertEquals(Integer.valueOf(500), exception.getHttpCode());
        assertEquals("publisher unavailable", exception.getMessage());
    }

    private static void emitNativeSequence(com.ke.bella.openapi.protocol.BellaEventSourceListener listener) {
        listener.onEvent(null, null, "message_start", messageStartJson());
        listener.onEvent(null, null, "content_block_delta", contentDeltaJson());
        listener.onEvent(null, null, "message_stop", JacksonUtils.serialize(StreamMessageResponse.messageStop()));
    }

    private static HttpServer startSseServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model:streamRawPredict", exchange -> {
            byte[] body = (sse("message_start", messageStartJson())
                    + sse("content_block_delta", contentDeltaJson())
                    + sse("message_stop", JacksonUtils.serialize(StreamMessageResponse.messageStop())))
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }

    private static String sse(String event, String data) {
        return "event: " + event + "\n" + "data: " + data + "\n\n";
    }

    private static String messageStartJson() {
        MessageResponse message = MessageResponse.builder()
                .id("msg-vertex")
                .type("message")
                .role("assistant")
                .model("vertex-claude")
                .content(Collections.emptyList())
                .usage(MessageResponse.Usage.builder().inputTokens(3).outputTokens(0).build())
                .build();
        return JacksonUtils.serialize(StreamMessageResponse.messageStart(message));
    }

    private static String contentDeltaJson() {
        return JacksonUtils.serialize(StreamMessageResponse.contentBlockDelta(
                0, new StreamMessageResponse.TextDelta("hello")));
    }

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static AnthropicVertexProperty property() {
        AnthropicVertexProperty property = new AnthropicVertexProperty();
        property.setDefaultMaxToken(8192);
        property.setAuth(new AuthorizationProperty(AuthorizationProperty.AuthType.BEARER, null, "google-token", null));
        return property;
    }

    private static class ErrorHttpDelegator implements Callbacks.HttpDelegator {
        private final MessageResponse error;
        private final int status;

        private ErrorHttpDelegator(MessageResponse error, int status) {
            this.error = error;
            this.status = status;
        }

        @Override
        public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
            okhttp3.Response response = new okhttp3.Response.Builder()
                    .request(new Request.Builder().url("https://vertex.example").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(status)
                    .message("error")
                    .build();
            errorCallback.callback(clazz.cast(error), response);
            return null;
        }
    }

    private static class NativeSequenceCallback implements Callbacks.StreamCompletionCallback {
        private final EndpointProcessData processData;
        private final List<String> events = new ArrayList<>();
        private final CountDownLatch finished = new CountDownLatch(1);

        private NativeSequenceCallback(EndpointProcessData processData) {
            this.processData = processData;
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void callback(StreamCompletionResponse msg) {
            if(!processData.isNativeSend()) {
                events.add("message");
            }
        }

        @Override
        public void done() {
            if(!processData.isNativeSend()) {
                events.add("message_stop");
            }
        }

        @Override
        public void finish() {
            finished.countDown();
        }

        @Override
        public void finish(BellaException exception) {
            events.add("error");
            finished.countDown();
        }

        @Override
        public void send(Object data) {
            events.add(((StreamMessageResponse) data).getType());
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
}
