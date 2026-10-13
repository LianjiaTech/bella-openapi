package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.QueueDelegator;
import com.ke.bella.queue.QueueClient;

public class QueueAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private final CompletionAdaptorDelegator<T> delegator;
    private final QueueDelegator queueDelegator;

    public QueueAdaptor(CompletionAdaptorDelegator<T> delegator, QueueClient queueClient, EndpointProcessData processData) {
        this.delegator = delegator;
        this.queueDelegator = new QueueDelegator(queueClient, processData);
    }

    Callbacks.HttpDelegator httpDelegator() {
        return queueDelegator.httpDelegator();
    }

    Callbacks.StreamDelegator streamDelegator() {
        return queueDelegator.streamDelegator();
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, T property) {
        return delegator.completion(request, url, property, httpDelegator());
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        delegator.streamCompletion(request, url, property, callback, streamDelegator());
    }

    @Override
    public String getDescription() {
        return "jobQueue协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegator.getPropertyClass();
    }

}
