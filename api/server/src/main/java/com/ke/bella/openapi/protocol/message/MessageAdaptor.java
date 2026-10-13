package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;

public interface MessageAdaptor<T extends CompletionProperty> extends IProtocolAdaptor {

    MessageResponse createMessages(MessageRequest request, String url, T property);

    void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback);

    @Override
    default String endpoint() {
        return "/v1/messages";
    }
}
