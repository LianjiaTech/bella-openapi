package com.ke.bella.openapi.protocol.message;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.SessionIdService;
import com.ke.bella.openapi.protocol.completion.AnthropicVertexProperty;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Component
public class AnthropicVertexAdaptor implements MessageDelegator<AnthropicVertexProperty> {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final String RAW_PREDICT = ":rawPredict";
    private static final String STREAM_RAW_PREDICT = ":streamRawPredict";

    private final Callbacks.ChannelErrorCallback<MessageResponse> errorCallback = (errorResponse, response) -> {
        if(errorResponse != null && errorResponse.getError() != null
                && StringUtils.isNotBlank(errorResponse.getError().getMessage())) {
            throw new VertexChannelException(response.code(), errorResponse.getError().getMessage());
        }
        throw new VertexChannelException(response.code(), response.message());
    };

    @Override
    public String getDescription() {
        return "Anthropic Vertex AI Messages协议适配器";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AnthropicVertexProperty.class;
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, AnthropicVertexProperty property) {
        return createMessages(request, url, property, null);
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, AnthropicVertexProperty property,
            Callbacks.HttpDelegator delegator) {
        prepareLogicalRequest(request, property, false);
        if(delegator != null) {
            return delegator.request(request, MessageResponse.class, errorCallback);
        }

        MessageRequest transportRequest = prepareTransportRequest(request, property);
        byte[] requestBytes = JacksonUtils.toByte(transportRequest);
        clearLargeData(transportRequest);

        Request httpRequest = buildHttpRequest(requestBytes, vertexUrl(url, false), property);
        MessageResponse response = HttpUtils.httpRequest(httpRequest, MessageResponse.class, errorCallback);
        EndpointContext.getProcessData().setResponse(TransferToCompletionsUtils.convertResponse(response));
        return response;
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AnthropicVertexProperty property,
            Callbacks.StreamCompletionCallback callback) {
        streamMessages(request, url, property, callback, null);
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AnthropicVertexProperty property,
            Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator delegator) {
        EndpointProcessData processData = EndpointContext.getProcessData();
        boolean nativeSend = endpoint().equals(processData.getEndpoint());
        processData.setNativeSend(nativeSend);
        AnthropicVertexStreamListener listener = new AnthropicVertexStreamListener(callback, request.getModel(), nativeSend);

        prepareLogicalRequest(request, property, true);
        if(delegator != null) {
            delegator.request(request, listener);
            return;
        }

        MessageRequest transportRequest = prepareTransportRequest(request, property);
        byte[] requestBytes = JacksonUtils.toByte(transportRequest);
        clearLargeData(transportRequest);

        Request httpRequest = buildHttpRequest(requestBytes, vertexUrl(url, true), property);
        HttpUtils.streamRequest(httpRequest, listener);
    }

    private void prepareLogicalRequest(MessageRequest request, AnthropicVertexProperty property, boolean stream) {
        request.setStream(stream);
        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
    }

    MessageRequest prepareTransportRequest(MessageRequest request, AnthropicVertexProperty property) {
        MessageRequest transportRequest = JacksonUtils.deserialize(JacksonUtils.toByte(request), MessageRequest.class);
        transportRequest.setModel(null);
        transportRequest.setAnthropic_version(property.getAnthropicVersion());
        return transportRequest;
    }

    Request buildHttpRequest(byte[] requestBytes, String url, AnthropicVertexProperty property) {
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(JSON_MEDIA_TYPE, requestBytes));

        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }
        SessionIdService.inject(builder, property);
        builder.removeHeader("x-api-key");
        builder.removeHeader("anthropic-version");
        return builder.build();
    }

    String vertexUrl(String url, boolean stream) {
        String normalizedUrl = StringUtils.stripEnd(StringUtils.trim(url), "/");
        if(StringUtils.isBlank(normalizedUrl)) {
            throw new VertexChannelException(400, "Vertex Claude channel URL is required");
        }
        if(normalizedUrl.endsWith(RAW_PREDICT) || normalizedUrl.endsWith(STREAM_RAW_PREDICT)) {
            throw new VertexChannelException(400, "Vertex Claude channel URL must not include a predict action suffix");
        }
        return normalizedUrl + (stream ? STREAM_RAW_PREDICT : RAW_PREDICT);
    }

    static class AnthropicVertexStreamListener extends AnthropicAdaptor.AnthropicStreamListener {

        AnthropicVertexStreamListener(Callbacks.StreamCompletionCallback callback, String originalModel, boolean nativeSend) {
            super(callback, originalModel, nativeSend);
        }

        @Override
        protected BellaException convertToException(Response response) {
            if(response == null) {
                return new VertexChannelException(500, "Vertex Claude stream request failed");
            }
            try {
                String body = response.body() == null ? null : response.body().string();
                JsonNode root = StringUtils.isBlank(body) ? null : JacksonUtils.deserialize(body);
                JsonNode error = root == null ? null : root.path("error");
                String message = error == null ? null : error.path("message").asText(null);
                if(StringUtils.isBlank(message)) {
                    message = StringUtils.defaultIfBlank(body, response.message());
                }
                return new VertexChannelException(response.code(), message);
            } catch (Exception e) {
                return new VertexChannelException(response.code(), response.message());
            }
        }
    }

    static class VertexChannelException extends BellaException.ChannelException {
        private final Integer upstreamHttpCode;

        VertexChannelException(Integer httpCode, String message) {
            super(httpCode, message);
            this.upstreamHttpCode = httpCode;
        }

        @Override
        public Integer getHttpCode() {
            return upstreamHttpCode;
        }
    }
}
