package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

public interface Callbacks {
    interface BaseCallback {
    }

    interface StreamCompletionCallback extends BaseCallback {
        void onOpen();
        void callback(StreamCompletionResponse msg);
        void done();
        void finish();
        void finish(ChannelException exception);
    }
}