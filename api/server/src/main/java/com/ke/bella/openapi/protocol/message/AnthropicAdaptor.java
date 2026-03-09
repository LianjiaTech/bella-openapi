package com.ke.bella.openapi.protocol.message;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AnthropicProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;

@Slf4j
@Component("AnthropicMessage")
public class AnthropicAdaptor implements MessageAdaptor<AnthropicProperty> {

    private final Callbacks.ChannelErrorCallback<MessageResponse> errorCallback = (errorResponse, res) -> {
        if(errorResponse != null && errorResponse.getError() != null) {
            throw new BellaException.ChannelException(res.code(), errorResponse.getError().getMessage());
        }
        throw new BellaException.ChannelException(res.code(), res.message());
    };

    @Override
    public String getDescription() {
        return "Anthropic协议适配器";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AnthropicProperty.class;
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, AnthropicProperty property) {
        request.setModel(property.getDeployName());
        request.setStream(false);

        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion());

        byte[] requestBytes = JacksonUtils.toByte(request);
        clearLargeData(request);

        Request httpRequest = buildHttpRequest(requestBytes, url, property);

        MessageResponse response = HttpUtils.httpRequest(httpRequest, MessageResponse.class, errorCallback);

        EndpointContext.getProcessData().setResponse(TransferToCompletionsUtils.convertResponse(response));

        return response;
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AnthropicProperty property,
            Callbacks.StreamCompletionCallback callback) {
        String originalModel = request.getModel();
        request.setModel(property.getDeployName());
        request.setStream(true);

        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion() != null ? property.getAnthropicVersion() : "2023-06-01");

        byte[] requestBytes = JacksonUtils.toByte(request);
        clearLargeData(request);

        Request httpRequest = buildHttpRequest(requestBytes, url, property);

        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setNativeSend(endpoint().equals(processData.getEndpoint()));

        AnthropicStreamListener listener = new AnthropicStreamListener(
                callback,
                originalModel,
                processData.isNativeSend());

        HttpUtils.streamRequest(httpRequest, listener);
    }

    private Request buildHttpRequest(byte[] requestBytes, String url, AnthropicProperty property) {
        String anthropicVersion = property.getAnthropicVersion() != null ? property.getAnthropicVersion() : "2023-06-01";

        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", property.getAuth().getApiKey())
                .addHeader("anthropic-version", anthropicVersion)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBytes));

        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }

    static class AnthropicStreamListener extends BellaEventSourceListener {
        private final Callbacks.StreamCompletionCallback callback;
        private final String originalModel;
        private final boolean nativeSend;

        private boolean isFirst = true;
        private String id = UUID.randomUUID().toString();
        private MessageResponse.Usage usage;
        private final AtomicInteger toolNum = new AtomicInteger(0);

        public AnthropicStreamListener(Callbacks.StreamCompletionCallback callback,
                String originalModel,
                boolean nativeSend) {
            this.callback = callback;
            this.originalModel = originalModel;
            this.nativeSend = nativeSend;
        }

        @Override
        public void onOpen(EventSource eventSource, Response response) {
            callback.onOpen();
            super.onOpen(eventSource, response);
        }

        @Override
        public void onEvent(EventSource eventSource, String eventId, String type, String msg) {
            if(isFirst) {
                isFirst = false;
            }

            StreamMessageResponse response = JacksonUtils.deserialize(msg, StreamMessageResponse.class);

            if(response == null) {
                log.warn("deserialized response is null, skipping event: {}", msg);
                return;
            }

            if("message_start".equals(response.getType()) && response.getMessage() != null) {
                id = response.getMessage().getId();
                usage = response.getMessage().getUsage();
                if(nativeSend) {
                    callback.send(response);
                }
                return;
            }

            if("message_stop".equals(response.getType())) {
                if(nativeSend) {
                    callback.send(response);
                }
                callback.done();
                return;
            }

            if(nativeSend) {
                callback.send(response);
            } else {
                StreamCompletionResponse openaiResponse = TransferToCompletionsUtils.convertStreamResponse(
                        response, originalModel, id, toolNum, usage);
                if(openaiResponse != null) {
                    callback.callback(openaiResponse);
                }
            }
        }

        @Override
        public void onClosed(EventSource eventSource) {
            callback.finish();
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            BellaException exception;
            try {
                if(t == null) {
                    exception = convertToException(response);
                } else {
                    exception = BellaException.fromException(t);
                }
            } catch (Exception e) {
                exception = BellaException.fromException(e);
            }

            if(connectionInitFuture.isDone()) {
                callback.finish(exception);
            } else {
                connectionInitFuture.completeExceptionally(exception);
            }
        }

        private BellaException convertToException(Response response) {
            try {
                String msg = response.body().string();
                return new BellaException.ChannelException(response.code(), msg);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                return new BellaException.ChannelException(response.code(), response.message());
            }
        }
    }
}
