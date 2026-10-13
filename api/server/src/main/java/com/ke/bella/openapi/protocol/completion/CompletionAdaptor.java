package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface CompletionAdaptor<T extends CompletionProperty> extends IProtocolAdaptor {

    CompletionResponse completion(CompletionRequest request, String url, T property);

    void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback);

    @Override
    default String endpoint() {
        return "/v1/chat/completions";
    }
}
