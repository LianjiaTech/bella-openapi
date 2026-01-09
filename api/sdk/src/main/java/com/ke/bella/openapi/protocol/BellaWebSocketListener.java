package com.ke.bella.openapi.protocol;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class BellaWebSocketListener extends WebSocketListener {
    @Setter
    protected CompletableFuture<?> connectionInitFuture;

    private final Callbacks.WebSocketCallback callback;

    public BellaWebSocketListener(Callbacks.WebSocketCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        connectionInitFuture.complete(null);
        callback.onOpen(webSocket, response);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        callback.onMessage(webSocket, bytes);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        callback.onMessage(webSocket, text);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        callback.onClosed(webSocket, code, reason);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        callback.onClosing(webSocket, code, reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        if(t != null) {
            log.warn(t.getMessage(), t);
        } else {
            log.warn(response.message());
        }
        callback.onFailure(webSocket, t, response);
    }

}
