package com.ke.bella.openapi.simulation;

import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

public interface FunctionCallListener {

    default void onMessage(StreamCompletionResponse msg) {
    }

    default void onFinish() {
    }

    default void onError(String msg) {
    }

}
