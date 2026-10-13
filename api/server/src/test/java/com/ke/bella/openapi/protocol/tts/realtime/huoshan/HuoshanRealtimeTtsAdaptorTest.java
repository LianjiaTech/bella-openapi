package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.tts.realtime.HuoshanRealtimeTtsProperty;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsPayload;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskContext;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HuoshanRealtimeTtsAdaptorTest {
    @Test
    public void latestAccessKeyUsesXApiKeyHeader() throws Exception {
        HeaderCaptureServer server = HeaderCaptureServer.start();
        HuoshanRealtimeTtsProperty property = new HuoshanRealtimeTtsProperty();
        property.setResourceId("seed-tts-2.0");
        property.setAccessKey("volc-api-key");

        try {
            new HuoshanRealtimeTtsAdaptor().startSpeech(server.url(), property, null, new NoopCallback());
        } catch (RuntimeException ignored) {
            // The test server intentionally rejects the upgrade after capturing headers.
        }

        Map<String, String> headers = server.awaitHeaders();
        assertEquals("volc-api-key", headers.get("x-api-key"));
        assertEquals("seed-tts-2.0", headers.get("x-api-resource-id"));
        assertTrue(headers.containsKey("x-api-connect-id"));
        assertFalse(headers.containsKey("x-api-app-id"));
        assertFalse(headers.containsKey("x-api-access-key"));
        assertFalse(headers.containsKey("x-api-request-id"));
    }

    @Test
    public void legacyAuthUsesAppIdAndAccessKeyHeaders() throws Exception {
        HeaderCaptureServer server = HeaderCaptureServer.start();
        HuoshanRealtimeTtsProperty property = new HuoshanRealtimeTtsProperty();
        property.setResourceId("seed-tts-2.0");
        property.setAppId("legacy-app-id");
        property.setAuth(AuthorizationProperty.builder().secret("legacy-access-token").build());

        try {
            new HuoshanRealtimeTtsAdaptor().startSpeech(server.url(), property, null, new NoopCallback());
        } catch (RuntimeException ignored) {
            // The test server intentionally rejects the upgrade after capturing headers.
        }

        Map<String, String> headers = server.awaitHeaders();
        assertEquals("legacy-app-id", headers.get("x-api-app-id"));
        assertEquals("legacy-access-token", headers.get("x-api-access-key"));
        assertTrue(headers.containsKey("x-api-request-id"));
        assertFalse(headers.containsKey("x-api-key"));
        assertFalse(headers.containsKey("x-api-connect-id"));
    }

    @Test
    public void legacyAuthFallsBackToAuthApiKeyAsAccessToken() throws Exception {
        HeaderCaptureServer server = HeaderCaptureServer.start();
        HuoshanRealtimeTtsProperty property = new HuoshanRealtimeTtsProperty();
        property.setResourceId("seed-tts-2.0");
        property.setAppId("legacy-app-id");
        property.setAccessKey("latest-api-key");
        property.setAuth(AuthorizationProperty.builder().apiKey("legacy-access-token").build());

        try {
            new HuoshanRealtimeTtsAdaptor().startSpeech(server.url(), property, null, new NoopCallback());
        } catch (RuntimeException ignored) {
            // The test server intentionally rejects the upgrade after capturing headers.
        }

        Map<String, String> headers = server.awaitHeaders();
        assertEquals("legacy-app-id", headers.get("x-api-app-id"));
        assertEquals("legacy-access-token", headers.get("x-api-access-key"));
        assertFalse(headers.containsKey("x-api-key"));
    }

    @Test
    public void missingLatestAccessKeyFailsBeforeConnecting() throws Exception {
        HuoshanRealtimeTtsProperty property = new HuoshanRealtimeTtsProperty();
        property.setResourceId("seed-tts-2.0");

        try {
            new HuoshanRealtimeTtsAdaptor().startSpeech("ws://127.0.0.1:1/tts", property, null, new NoopCallback());
            fail("expected missing latest access key to fail");
        } catch (BellaException.ChannelException e) {
            assertEquals("Huoshan realtime TTS requires accessKey for X-Api-Key", e.getMessage());
        }
    }

    @Test
    public void missingLegacyAppIdOrAccessTokenFailsBeforeConnecting() throws Exception {
        HuoshanRealtimeTtsProperty property = new HuoshanRealtimeTtsProperty();
        property.setResourceId("seed-tts-2.0");
        property.setAuth(AuthorizationProperty.builder().secret("legacy-access-token").build());

        try {
            new HuoshanRealtimeTtsAdaptor().startSpeech("ws://127.0.0.1:1/tts", property, null, new NoopCallback());
            fail("expected incomplete legacy auth to fail");
        } catch (BellaException.ChannelException e) {
            assertEquals("Huoshan legacy realtime TTS requires appId and auth.secret/auth.apiKey", e.getMessage());
        }
    }

    @Test
    public void startSessionUsesTaskContextProviderSessionId() {
        HuoshanRealtimeTtsAdaptor adaptor = new HuoshanRealtimeTtsAdaptor();
        WebSocket webSocket = mock(WebSocket.class);
        RealtimeTtsTaskContext taskContext = new RealtimeTtsTaskContext();
        taskContext.setProviderSessionId("provider-session-b");
        taskContext.setFrozenPayload(RealtimeTtsPayload.builder()
                .voice("voice-a")
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .build());

        adaptor.startSession(webSocket, taskContext, new NoopCallback());

        ArgumentCaptor<ByteString> captor = ArgumentCaptor.forClass(ByteString.class);
        verify(webSocket).send(captor.capture());
        assertTrue(new String(captor.getValue().toByteArray(), StandardCharsets.ISO_8859_1).contains("provider-session-b"));
    }

    private static class HeaderCaptureServer {
        private final ServerSocket serverSocket;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Map<String, String> headers = new HashMap<>();

        private HeaderCaptureServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        static HeaderCaptureServer start() throws IOException {
            HeaderCaptureServer server = new HeaderCaptureServer(new ServerSocket(0));
            Thread thread = new Thread(server::acceptOnce);
            thread.setDaemon(true);
            thread.start();
            return server;
        }

        String url() {
            return "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/tts";
        }

        Map<String, String> awaitHeaders() throws InterruptedException {
            assertTrue("websocket request was not received", latch.await(5, TimeUnit.SECONDS));
            return headers;
        }

        private void acceptOnce() {
            try (ServerSocket ignored = serverSocket;
                    Socket socket = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))) {
                Map<String, String> captured = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int idx = line.indexOf(':');
                    if(idx > 0) {
                        captured.put(line.substring(0, idx).toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
                    }
                }
                headers = captured;
                socket.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
                // The client may close immediately after receiving the intentional 401.
            } finally {
                latch.countDown();
            }
        }
    }

    private static class NoopCallback implements Callbacks.WebSocketCallback {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        }

        @Override
        public boolean started() {
            return false;
        }
    }
}
