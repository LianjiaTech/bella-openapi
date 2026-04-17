package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.db.repo.ChannelRepo;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

public class QueueAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private static final String DEFAULT_CHANNEL = "100000000-SELF-DEPLOYED";

    private final CompletionAdaptorDelegator<T> delegator;
    private final EndpointProcessData processData;
    private final QueueClient queueClient;
    private final ChannelRepo channelRepo;

    public QueueAdaptor(CompletionAdaptorDelegator<T> delegator, QueueClient queueClient, EndpointProcessData processData, ChannelRepo channelRepo) {
        this.delegator = delegator;
        this.queueClient = queueClient;
        this.processData = processData;
        this.channelRepo = channelRepo;
    }

    Callbacks.HttpDelegator httpDelegator() {
        return new Callbacks.HttpDelegator() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                Put put = Put.builder()
                        .data(JacksonUtils.toMap(req))
                        .endpoint(processData.getEndpoint())
                        .timeout(processData.getMaxWaitSec())
                        .build();

                T result = queueClient.blockingPut(put, processData.getApikey(), clazz, errorCallback);
                updateChannelInfo(JacksonUtils.toMap(result), processData, channelRepo);
                return result;
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private static void updateChannelInfo(Map rawResult, EndpointProcessData processData, ChannelRepo channelRepo) {
        if(rawResult == null) {
            return;
        }
        String returnedChannelCode = StringUtils.defaultIfBlank(
                Objects.toString(rawResult.get("channelCode"), null), DEFAULT_CHANNEL);
        if(returnedChannelCode.equals(processData.getChannelCode())) {
            return;
        }
        if(DEFAULT_CHANNEL.equals(returnedChannelCode)) {
            processData.setChannelCode(DEFAULT_CHANNEL);
            return;
        }
        ChannelDB channel = channelRepo.queryByUniqueKey(returnedChannelCode);
        if(channel != null) {
            processData.setChannelCode(channel.getChannelCode());
            processData.setPrivate(EntityConstants.PRIVATE.equals(channel.getVisibility()));
            processData.setForwardUrl(channel.getUrl());
            processData.setProtocol(channel.getProtocol());
            processData.setPriceInfo(channel.getPriceInfo());
            processData.setSupplier(channel.getSupplier());
        }
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
        private final ChannelRepo channelRepo;
        private boolean channelUpdated = false;

        QueueStreamListener(EndpointProcessData processData, ChannelRepo channelRepo) {
            this.processData = processData;
            this.channelRepo = channelRepo;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
            if(!channelUpdated) {
                Map rawMap = JacksonUtils.toMap(msg);
                if(rawMap != null) {
                    updateChannelInfo(rawMap, processData, channelRepo);
                }
                channelUpdated = true;
            }
            return msg;
        }
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, T property) {
        return delegator.completion(request, url, property, httpDelegator());
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        QueueStreamListener queueCallback = new QueueStreamListener(processData, channelRepo);
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
