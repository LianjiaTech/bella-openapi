package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

/**
 * NoSendStreamCompletionCallback - Adapter pattern wrapper for StreamCompletionCallback
 *
 * Used in direct mode to prevent sending to SseEmitter (already handled by DirectSseListener)
 * All methods delegate to the wrapped callback, except send() which does nothing
 */
public class NoSendStreamCompletionCallback implements Callbacks.StreamCompletionCallback {

    private final Callbacks.StreamCompletionCallback delegate;

    public NoSendStreamCompletionCallback(Callbacks.StreamCompletionCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onOpen() {
        delegate.onOpen();
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        delegate.callback(msg);
    }

    @Override
    public void done() {
        delegate.done();
    }

    @Override
    public void finish() {
        delegate.finish();
    }

    @Override
    public void finish(ChannelException exception) {
        delegate.finish(exception);
    }

    @Override
    public void send(Object data) {
        // No-op: skip sending, already handled by DirectSseListener
    }

    @Override
    public boolean support() {
        return delegate.support();
    }
}
