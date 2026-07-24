package com.ke.bella.openapi.protocol;

import java.util.concurrent.CompletableFuture;

import lombok.Setter;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

@Setter
public abstract class BellaEventSourceListener extends EventSourceListener {
    protected CompletableFuture<?> connectionInitFuture;

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        this.connectionInitFuture.complete(null);
    }
}
