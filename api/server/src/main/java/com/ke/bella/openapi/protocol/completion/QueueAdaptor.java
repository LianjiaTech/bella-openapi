package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;

public class QueueAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private final CompletionAdaptorDelegator<T> delegator;
    private final JobQueueClient jobQueueClient;
    private final EndpointProcessData processData;
    private final Integer defaultTimeout;

    public QueueAdaptor(CompletionAdaptorDelegator<T> delegator, JobQueueClient jobQueueClient, EndpointProcessData processData, Integer defaultTimeout) {
        this.delegator = delegator;
        this.jobQueueClient = jobQueueClient;
        this.processData = processData;
        this.defaultTimeout = defaultTimeout;
    }

    Callbacks.HttpDelegator httpDelegator() {
       return new Callbacks.HttpDelegator() {
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                return jobQueueClient.blockingPut(jobQueueClient.buildTaskPutRequest(req, getTimeout(), processData.getEndpoint(), processData.getModel()), processData.getApikey(), clazz, errorCallback);
            }
        };
    }

    Callbacks.StreamDelegator streamDelegator() {
        return (req, listener) -> jobQueueClient.streamPut(jobQueueClient.buildTaskPutRequest(req, getTimeout(), processData.getEndpoint(), processData.getModel()), processData.getApikey(), listener);
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

    private int getTimeout() {
        return processData.getMaxWaitSec() != null ? processData.getMaxWaitSec() : defaultTimeout;
    }
}
