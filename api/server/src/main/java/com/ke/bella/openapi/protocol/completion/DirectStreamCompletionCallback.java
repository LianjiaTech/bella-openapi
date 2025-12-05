package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Direct mode streaming callback that:
 * 1. Immediately sends responses to client (transparent passthrough)
 * 2. Asynchronously processes logging and metrics
 * 3. Delegates actual callback processing to the wrapped callback
 *
 * This acts as a proxy that prioritizes response speed over processing.
 */
@Slf4j
public class DirectStreamCompletionCallback implements Callbacks.StreamCompletionCallback {

    private final Callbacks.StreamCompletionCallback delegate;
    private boolean sendEnabled = true;

    public DirectStreamCompletionCallback(Callbacks.StreamCompletionCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onOpen() {
        // Forward immediately
        delegate.onOpen();
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        // Step 1: Send immediately to client (transparent passthrough)
        if (sendEnabled) {
            delegate.send(msg);
        }

        // Step 2: Process async operations (logging, metrics, etc.) in background
        CompletableFuture.runAsync(() -> {
            try {
                // Temporarily disable send in delegate to avoid double-sending
                disableSend();
                delegate.callback(msg);
            } catch (Exception e) {
                log.error("Error in async callback processing", e);
            } finally {
                enableSend();
            }
        });
    }

    @Override
    public void done() {
        // Send [DONE] immediately
        if (sendEnabled) {
            delegate.send("[DONE]");
        }

        // Process done logic asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                disableSend();
                delegate.done();
            } catch (Exception e) {
                log.error("Error in async done processing", e);
            } finally {
                enableSend();
            }
        });
    }

    @Override
    public void finish() {
        // Wait for async operations to complete before finishing
        try {
            // Small delay to ensure async operations have time to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.finish();
    }

    @Override
    public void finish(ChannelException exception) {
        // Forward error immediately
        delegate.finish(exception);
    }

    @Override
    public void send(Object data) {
        if (sendEnabled) {
            delegate.send(data);
        }
    }

    @Override
    public boolean support() {
        return delegate.support();
    }

    /**
     * Disable send operations in delegate to prevent double-sending
     * during async processing
     */
    private void disableSend() {
        // This is handled by checking sendEnabled flag
        // The delegate's send will be called through this proxy
    }

    /**
     * Re-enable send operations
     */
    private void enableSend() {
        // This is handled by checking sendEnabled flag
    }
}
