package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;

public interface MessageDelegator<T extends CompletionProperty> extends MessageAdaptor<T> {

    MessageResponse createMessages(MessageRequest request, String url, T property, Callbacks.HttpDelegator httpDelegator);

    void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator streamDelegator);
}
