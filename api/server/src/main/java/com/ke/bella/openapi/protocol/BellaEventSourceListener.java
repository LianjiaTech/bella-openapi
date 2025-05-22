package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event source listener for handling SSE events with fallback support
 */
@Slf4j
public class BellaEventSourceListener extends EventSourceListener {
    private final Callbacks.StreamCompletionCallback callback;
    private final String model;
    private boolean hasStarted = false;

    public BellaEventSourceListener(Callbacks.StreamCompletionCallback callback, String model) {
        this.callback = callback;
        this.model = model;
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        log.debug("SSE connection opened for model {}", model);
        callback.onOpen();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if (!hasStarted) {
            hasStarted = true;
            log.info("First event received from model {}", model);
        }
        
        if ("[DONE]".equals(data)) {
            callback.done();
            callback.finish();
            return;
        }

        try {
            StreamCompletionResponse completionResponse = JacksonUtils.deserialize(data, StreamCompletionResponse.class);
            if (completionResponse == null) {
                log.warn("Failed to deserialize SSE event data: {}", data);
                return;
            }
            
            // Set model information if not present
            if (StringUtils.isEmpty(completionResponse.getModel())) {
                completionResponse.setModel(model);
            }
            
            // Set created time if not present
            if (completionResponse.getCreated() == 0) {
                completionResponse.setCreated(DateTimeUtils.getCurrentSeconds());
            }
            
            callback.callback(completionResponse);
        } catch (Exception e) {
            log.error("Error processing SSE event: {}", e.getMessage(), e);
            callback.finish(new ChannelException.InternalException("Error processing SSE event: " + e.getMessage()));
        }
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.debug("SSE connection closed for model {}", model);
        callback.finish();
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        log.error("SSE connection failure for model {}: {}", model, t != null ? t.getMessage() : "Unknown error", t);
        String errorMsg = t != null ? t.getMessage() : "Unknown SSE error";
        int statusCode = response != null ? response.code() : 500;
        callback.finish(new ChannelException.InternalException("SSE failure: " + errorMsg, statusCode));
    }
}
