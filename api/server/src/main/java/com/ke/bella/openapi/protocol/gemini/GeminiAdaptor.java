package com.ke.bella.openapi.protocol.gemini;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;

import javax.servlet.http.HttpServletResponse;

public interface GeminiAdaptor<T extends CompletionProperty> extends IProtocolAdaptor {

    void completion(GeminiRequest request, String url, T property, HttpServletResponse response);

    void streamCompletion(GeminiRequest request, String url, T property, HttpServletResponse response);

    @Override
    default String endpoint() {
        return "/v1beta/models";
    }
}
