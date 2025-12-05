package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;

import java.io.InputStream;

public interface CompletionAdaptor<T extends CompletionProperty> extends IProtocolAdaptor {

    CompletionResponse completion(CompletionRequest request, String url, T property);

    void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback);

    /**
     * Direct mode completion with InputStream passthrough (no deserialization)
     * Default implementation throws UnsupportedOperationException - override for direct mode support
     */
    default CompletionResponse completion(InputStream requestBody, String url, T property) {
        throw new UnsupportedOperationException("Direct mode with InputStream not supported by " + getClass().getSimpleName());
    }

    /**
     * Direct mode streaming completion with InputStream passthrough (no deserialization)
     * Default implementation throws UnsupportedOperationException - override for direct mode support
     */
    default void streamCompletion(InputStream requestBody, String url, T property, Callbacks.StreamCompletionCallback callback) {
        throw new UnsupportedOperationException("Direct mode streaming with InputStream not supported by " + getClass().getSimpleName());
    }

    @Override
    default String endpoint() {
        return "/v1/chat/completions";
    }
}
