package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Direct SSE Listener for transparent passthrough
 * Sends events immediately to client, processes delegate callback asynchronously
 */
@Slf4j
public class DirectSseListener extends BellaEventSourceListener {
    private final SseEmitter sseEmitter;
    private final Callbacks.StreamCompletionCallback delegateCallback;

    public DirectSseListener(SseEmitter sseEmitter, Callbacks.StreamCompletionCallback delegateCallback) {
        this.sseEmitter = sseEmitter;
        this.delegateCallback = delegateCallback;
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        super.onOpen(eventSource, response);

        // Async notify delegate callback
        if (delegateCallback != null) {
            TaskExecutor.submit(() -> {
                try {
                    delegateCallback.onOpen();
                } catch (Exception e) {
                    log.error("Error in async onOpen processing", e);
                }
            });
        }
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        // Immediately send to client (transparent passthrough)
        sendDirectly(data);

        // Async process delegate callback (logging, metrics, etc.)
        // NoSendStreamCompletionCallback already wraps it, so send() is skipped
        if (delegateCallback != null) {
            TaskExecutor.submit(() -> {
                try {
                    StreamCompletionResponse response = parseResponse(data);
                    if (response != null) {
                        delegateCallback.callback(response);
                    }
                } catch (Exception e) {
                    log.error("Error in async event processing", e);
                }
            });
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        super.onClosed(eventSource);

        // Send [DONE] immediately
        sendDirectly("[DONE]");

        // Complete SSE
        sseEmitter.complete();

        // Async finish processing
        if (delegateCallback != null) {
            TaskExecutor.submit(() -> {
                try {
                    delegateCallback.done();
                    delegateCallback.finish();
                } catch (Exception e) {
                    log.error("Error in async done processing", e);
                }
            });
        }
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        super.onFailure(eventSource, t, response);

        log.error("Direct SSE error", t);

        // Complete with error
        sseEmitter.completeWithError(t);

        // Async error processing
        if (delegateCallback != null) {
            TaskExecutor.submit(() -> {
                try {
                    if (t instanceof com.ke.bella.openapi.common.exception.ChannelException) {
                        delegateCallback.finish((com.ke.bella.openapi.common.exception.ChannelException) t);
                    } else {
                        delegateCallback.finish(
                            com.ke.bella.openapi.common.exception.ChannelException.fromException(t));
                    }
                } catch (Exception e) {
                    log.error("Error in async failure processing", e);
                }
            });
        }
    }

    /**
     * Send data directly to client without any processing
     */
    private void sendDirectly(String data) {
        try {
            SseHelper.sendEvent(sseEmitter, data);
        } catch (Exception e) {
            log.error("Error sending SSE event", e);
        }
    }

    /**
     * Parse response from data string
     */
    private StreamCompletionResponse parseResponse(String data) {
        try {
            return com.ke.bella.openapi.utils.JacksonUtils.deserialize(data, StreamCompletionResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse response data: {}", data, e);
            return null;
        }
    }
}
