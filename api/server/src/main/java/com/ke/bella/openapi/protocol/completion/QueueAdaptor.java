package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

public class QueueAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private static final String DEFAULT_CHANNEL = "100000000-SELF-DEPLOYED";

    private final CompletionAdaptorDelegator<T> delegator;
    private final EndpointProcessData processData;
    private final QueueClient queueClient;
    private final Function<String, ChannelDB> channelLookup;

    public QueueAdaptor(CompletionAdaptorDelegator<T> delegator, QueueClient queueClient, EndpointProcessData processData, Function<String, ChannelDB> channelLookup) {
        this.delegator = delegator;
        this.queueClient = queueClient;
        this.processData = processData;
        this.channelLookup = channelLookup;
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

                T result = queueClient.blockingPut(put, processData.getApikey(), clazz, errorCallback);
                if(result instanceof OpenapiResponse) {
                    updateChannelInfo(((OpenapiResponse) result).getChannelCode(), processData, channelLookup);
                }
                return result;
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

    private static class QueueStreamListener extends Callbacks.StreamCompletionCallbackNode {
        private final EndpointProcessData processData;
        private final Function<String, ChannelDB> channelLookup;
        private boolean channelUpdated = false;

        QueueStreamListener(EndpointProcessData processData, Function<String, ChannelDB> channelLookup) {
            this.processData = processData;
            this.channelLookup = channelLookup;
        }

        @Override
        public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
            if(!channelUpdated) {
                updateChannelInfo(msg.getChannelCode(), processData, channelLookup);
                channelUpdated = true;
            }
            return msg;
        }
    }

    private static void updateChannelInfo(String channelCode, EndpointProcessData processData, Function<String, ChannelDB> channelLookup) {
        String returnedChannelCode = StringUtils.defaultIfBlank(channelCode, DEFAULT_CHANNEL);
        if(returnedChannelCode.equals(processData.getChannelCode())) {
            return;
        }
        if(DEFAULT_CHANNEL.equals(returnedChannelCode)) {
            processData.setChannelCode(DEFAULT_CHANNEL);
            return;
        }
        ChannelDB channel = channelLookup.apply(returnedChannelCode);
        if(channel != null) {
            processData.setChannelCode(channel.getChannelCode());
            processData.setPrivate(EntityConstants.PRIVATE.equals(channel.getVisibility()));
            processData.setForwardUrl(channel.getUrl());
            processData.setProtocol(channel.getProtocol());
            processData.setPriceInfo(channel.getPriceInfo());
            processData.setSupplier(channel.getSupplier());
        }
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, T property) {
        return delegator.completion(request, url, property, httpDelegator());
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        QueueStreamListener queueCallback = new QueueStreamListener(processData, channelLookup);
        queueCallback.addLast(callback);
        delegator.streamCompletion(request, url, property, queueCallback, streamDelegator());
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
