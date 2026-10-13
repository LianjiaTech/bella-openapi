package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;

public interface CompletionAdaptorDelegator<T extends CompletionProperty> extends CompletionAdaptor<T> {
    default boolean supportsDirectPassthrough() {
        return true;
    }

    CompletionResponse completion(CompletionRequest request, String url, T property, Callbacks.HttpDelegator delegator);

    void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator);
}
