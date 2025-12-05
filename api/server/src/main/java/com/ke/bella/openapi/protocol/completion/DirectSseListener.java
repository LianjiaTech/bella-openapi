package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

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

        // Immediately notify client
        if (delegateCallback != null) {
            delegateCallback.onOpen();
        }
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        // Step 1: Immediately send to client (transparent passthrough)
        sendDirectly(data);

        // Step 2: Async process delegate callback (logging, metrics, etc.)
        if (delegateCallback != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    // Process the event through delegate (for logging, metrics, safety checks, etc.)
                    // But skip the send operation since we already sent
                    processAsyncWithoutSend(data);
                } catch (Exception e) {
                    log.error("Error in async delegate processing", e);
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
            CompletableFuture.runAsync(() -> {
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
            CompletableFuture.runAsync(() -> {
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
     * Process delegate callback asynchronously without sending
     * This is for logging, metrics, safety checks, etc.
     */
    private void processAsyncWithoutSend(String data) {
        // Parse the data if needed for delegate processing
        // The delegate's send() will be skipped since we wrap it with NoOpSendCallback

        // Create a wrapper callback that skips send operations
        Callbacks.StreamCompletionCallback noOpSendCallback = new Callbacks.StreamCompletionCallback() {
            @Override
            public void onOpen() {
                // Already opened
            }

            @Override
            public void callback(StreamCompletionResponse msg) {
                // Delegate to actual callback but skip send
                if (delegateCallback instanceof NoOpSendWrapper) {
                    ((NoOpSendWrapper) delegateCallback).callbackWithoutSend(msg);
                }
            }

            @Override
            public void done() {
                // Already sent [DONE]
            }

            @Override
            public void finish() {
                // Delegate finish
                delegateCallback.finish();
            }

            @Override
            public void finish(com.ke.bella.openapi.common.exception.ChannelException exception) {
                // Delegate finish with error
                delegateCallback.finish(exception);
            }

            @Override
            public void send(Object data) {
                // Skip send - already sent directly
            }
        };

        // TODO: Parse data and call delegate callback for processing
        // This allows logging, metrics, safety checks, etc. to run asynchronously
    }

    /**
     * Interface for callbacks that support skipping send operations
     */
    public interface NoOpSendWrapper {
        void callbackWithoutSend(StreamCompletionResponse msg);
    }
}
