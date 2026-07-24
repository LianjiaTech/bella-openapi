package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;
import org.apache.commons.lang3.StringUtils;

public class QueueDelegator {
    private final QueueClient queueClient;
    private final EndpointProcessData processData;

    public QueueDelegator(QueueClient queueClient, EndpointProcessData processData) {
        this.queueClient = queueClient;
        this.processData = processData;
    }

    public Callbacks.HttpDelegator httpDelegator() {
        return new Callbacks.HttpDelegator() {
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                return queueClient.blockingPut(buildPut(req), processData.getApikey(), clazz, errorCallback);
            }
        };
    }

    public Callbacks.StreamDelegator streamDelegator() {
        return (req, listener) -> queueClient.streamingPut(buildPut(req), processData.getApikey(), listener);
    }

    public Put buildPut(Object request) {
        Put.PutBuilder builder = Put.builder()
                .data(JacksonUtils.toMap(request))
                .endpoint(processData.getEndpoint())
                .timeout(processData.getMaxWaitSec());
        if(StringUtils.isNotBlank(processData.getQueueName())) {
            builder.queue(processData.getQueueName());
        }
        return builder.build();
    }
}
