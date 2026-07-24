package com.ke.bella.openapi.protocol;

import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BellaWebSocketListenerTest {
    @Test
    public void connectionFailureCompletesInitFutureWithoutCallback() {
        TestCallback callback = new TestCallback();
        BellaWebSocketListener listener = new BellaWebSocketListener(callback);
        CompletableFuture<Void> future = new CompletableFuture<>();
        listener.setConnectionInitFuture(future);

        listener.onFailure(null, new IOException("connect refused"), null);

        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, callback.failures);
    }

    @Test
    public void failureAfterConnectionInitDelegatesToCallback() {
        TestCallback callback = new TestCallback();
        BellaWebSocketListener listener = new BellaWebSocketListener(callback);
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        listener.setConnectionInitFuture(future);

        listener.onFailure(null, new IOException("connection reset"), null);

        assertEquals(1, callback.failures);
    }

    private static class TestCallback implements Callbacks.WebSocketCallback {
        int failures;

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
            failures++;
        }

        @Override
        public boolean started() {
            return false;
        }
    }
}
