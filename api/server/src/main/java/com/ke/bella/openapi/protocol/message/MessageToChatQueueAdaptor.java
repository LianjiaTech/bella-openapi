package com.ke.bella.openapi.protocol.message;

import java.util.function.Supplier;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptorDelegator;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.QueueAdaptor;
import com.ke.bella.queue.QueueClient;

public class MessageToChatQueueAdaptor<T extends CompletionProperty> implements MessageAdaptor<T> {
    private final MessageDelegatorAdaptor<T> delegate;
    private final CompletionAdaptor<T> queueAdaptor;
    private final EndpointProcessData processData;

    @SuppressWarnings("unchecked")
    public MessageToChatQueueAdaptor(MessageDelegatorAdaptor<T> delegate, QueueClient queueClient, EndpointProcessData processData) {
        CompletionAdaptor<T> completionAdaptor = delegate.delegator();
        if(!(completionAdaptor instanceof CompletionAdaptorDelegator)) {
            throw new IllegalStateException(completionAdaptor.getClass().getSimpleName() + "不支持请求代理");
        }
        this.delegate = delegate;
        this.processData = processData;
        this.queueAdaptor = new QueueAdaptor<>((CompletionAdaptorDelegator<T>) completionAdaptor, queueClient, processData);
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, T property) {
        CompletionAdaptor<T> adaptor = delegate.decorateAdaptor(queueAdaptor, property, processData);
        return withChatEndpoint(() -> delegate.createMessagesWithCompletionAdaptor(request, url, property, adaptor));
    }

    @Override
    public void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        CompletionAdaptor<T> adaptor = delegate.decorateAdaptor(queueAdaptor, property, processData);
        withChatEndpoint(() -> {
            delegate.streamMessagesWithCompletionAdaptor(request, url, property, callback, adaptor);
            return null;
        });
    }

    @Override
    public String getDescription() {
        return "jobQueue协议Messages转ChatCompletion适配";
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegate.getPropertyClass();
    }

    private <R> R withChatEndpoint(Supplier<R> callable) {
        String endpoint = processData.getEndpoint();
        try {
            processData.setEndpoint(queueAdaptor.endpoint());
            return callable.get();
        } finally {
            processData.setEndpoint(endpoint);
        }
    }
}
