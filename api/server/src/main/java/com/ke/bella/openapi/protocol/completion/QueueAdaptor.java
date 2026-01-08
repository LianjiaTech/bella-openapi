package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;

public class QueueAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private final CompletionAdaptorDelegator<T> delegator;
    private final EndpointProcessData processData;
    private final QueueClient queueClient;

    public QueueAdaptor(CompletionAdaptorDelegator<T> delegator, QueueClient queueClient, EndpointProcessData processData) {
        this.delegator = delegator;
        this.queueClient = queueClient;
        this.processData = processData;
    }

    Callbacks.HttpDelegator httpDelegator() {
        return new Callbacks.HttpDelegator() {
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                Put put = Put.builder()
                        .data(JacksonUtils.toMap(req))
                        .endpoint(processData.getEndpoint())
                        .timeout(processData.getMaxWaitSec())
                        .build();

                return queueClient.blockingPut(put, processData.getApikey(), clazz, errorCallback);
            }
        };
    }

    Callbacks.StreamDelegator streamDelegator() {
        return (req, listener) -> {
            Put put = Put.builder()
                    .data(JacksonUtils.toMap(req))
                    .endpoint(processData.getEndpoint())
                    .timeout(processData.getMaxWaitSec())
                    .build();

            queueClient.streamingPut(put, processData.getApikey(), listener);
        };
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
