package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.QueueDelegator;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.queue.QueueClient;

public class QueueMessageAdaptor<T extends CompletionProperty> implements MessageAdaptor<T> {
    private final MessageDelegator<T> delegate;
    private final QueueDelegator queueDelegator;
    private final EndpointProcessData processData;

    public QueueMessageAdaptor(MessageDelegator<T> delegate, QueueClient queueClient, EndpointProcessData processData) {
        this.delegate = delegate;
        this.processData = processData;
        this.queueDelegator = new QueueDelegator(queueClient, processData);
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, T property) {
        MessageResponse response = delegate.createMessages(request, url, property, queueDelegator.httpDelegator());
        processData.setResponse(TransferToCompletionsUtils.convertResponse(response));
        return response;
    }

    @Override
    public void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        delegate.streamMessages(request, url, property, callback, queueDelegator.streamDelegator());
    }

    @Override
    public String getDescription() {
        return "jobQueue协议Messages适配";
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegate.getPropertyClass();
    }
}
