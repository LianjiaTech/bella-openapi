package com.ke.bella.openapi.protocol.completion;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;

/**
 * OpenAI Responses API 原生协议适配器
 * 用于 /v1/responses 端点
 * 支持同步、后台异步、流式和查询四种模式
 */
@Component("OpenAIResponses")
@Slf4j
public class OpenAIResponsesAdaptor implements ResponsesAdaptor<ResponsesApiProperty> {

    private static final int CONNECTION_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 1800;

    @Override
    public String getDescription() {
        return "OpenAI Responses API原生协议";
    }

    @Override
    public Class<ResponsesApiProperty> getPropertyClass() {
        return ResponsesApiProperty.class;
    }

    /**
     * 创建 Response（同步或后台异步取决于 request.background）
     *
     * @param request  ResponsesApiRequest 原生请求（通过 request.background 控制模式）
     * @param url      目标URL
     * @param property 配置属性
     *
     * @return ResponsesApiResponse 原生响应
     */
    @Override
    public ResponsesApiResponse createResponse(ResponsesApiRequest request, String url, ResponsesApiProperty property) {
        log.debug("Creating Responses API request, background={}", request.getBackground());

        request.setModel(property.getDeployName());
        request.setStream(false);

        if(StringUtils.isNotEmpty(property.getApiVersion())) {
            url += property.getApiVersion();
        }

        Request httpRequest = buildRequest(request, url, property);

        ResponsesApiResponse response = HttpUtils.httpRequest(httpRequest, ResponsesApiResponse.class,
                (errorResponse, res) -> {
                    if(errorResponse.getError() != null) {
                        errorResponse.getError().setHttpCode(res.code());
                    }
                }, CONNECTION_TIMEOUT, READ_TIMEOUT);

        log.debug("Responses API request completed: {}, status: {}", response.getId(), response.getStatus());
        return response;
    }

    /**
     * 查询 Response 状态
     *
     * @param responseId Response ID
     * @param url        目标URL（不含 response_id）
     * @param property   配置属性
     *
     * @return ResponsesApiResponse 原生响应
     */
    @Override
    public ResponsesApiResponse getResponse(String responseId, String url, ResponsesApiProperty property) {
        log.debug("Querying Responses API status for: {}", responseId);

        String queryUrl = url;
        if(StringUtils.isNotEmpty(property.getApiVersion())) {
            queryUrl += property.getApiVersion();
        }
        queryUrl += "/" + responseId;

        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(queryUrl)
                .get();

        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        Request httpRequest = builder.build();

        ResponsesApiResponse response = HttpUtils.httpRequest(httpRequest, ResponsesApiResponse.class,
                (errorResponse, res) -> {
                    if(errorResponse.getError() != null) {
                        errorResponse.getError().setHttpCode(res.code());
                    }
                }, CONNECTION_TIMEOUT, READ_TIMEOUT);

        log.debug("Responses API query completed: {}, status: {}", response.getId(), response.getStatus());
        return response;
    }

    /**
     * 创建流式 Response（原生 SSE 格式）
     *
     * @param request  ResponsesApiRequest 原生请求
     * @param url      目标URL
     * @param property 配置属性
     * @param callback 原生 SSE 回调
     */
    @Override
    public void streamResponseAsync(ResponsesApiRequest request, String url, ResponsesApiProperty property,
            Callbacks.ResponsesApiSseCallback callback) {
        log.debug("Creating streaming Responses API request");

        request.setModel(property.getDeployName());
        request.setStream(true);

        if(request.getStore() == null) {
            request.setStore(false);
        }

        if(StringUtils.isNotEmpty(property.getApiVersion())) {
            url += property.getApiVersion();
        }

        Request httpRequest = buildRequest(request, url, property);

        BellaEventSourceListener listener = new BellaEventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    if(StringUtils.isNotBlank(data)) {
                        callback.onEvent(id, type, data);
                    }
                } catch (Exception e) {
                    log.error("Error processing SSE event: type={}, data={}", type, data, e);
                    callback.onError(ChannelException.fromException(e));
                    eventSource.cancel();
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.debug("Streaming Responses API connection closed");
                callback.onComplete();
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                ChannelException exception;
                try {
                    if(t == null) {
                        exception = convertToException(response);
                    } else {
                        if(t instanceof ChannelException) {
                            exception = (ChannelException) t;
                        } else {
                            exception = ChannelException.fromException(t);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error converting failure to exception", e);
                    exception = ChannelException.fromException(e);
                }

                if(connectionInitFuture != null && connectionInitFuture.isDone()) {
                    callback.onError(exception);
                } else if(connectionInitFuture != null) {
                    connectionInitFuture.completeExceptionally(exception);
                } else {
                    callback.onError(exception);
                }
            }
        };

        HttpUtils.streamRequest(httpRequest, listener, CONNECTION_TIMEOUT, READ_TIMEOUT);
    }

    /**
     * 将 HTTP 错误响应转换为 ChannelException
     */
    private ChannelException convertToException(Response response) {
        try {
            if(response.body() == null) {
                return new ChannelException.OpenAIException(response.code(), "channel_error", response.message());
            }
            String msg = response.body().string();
            ResponsesApiResponse errorResponse = JacksonUtils.deserialize(msg, ResponsesApiResponse.class);
            if(errorResponse != null && errorResponse.getError() != null) {
                return new ChannelException.OpenAIException(
                        response.code(),
                        errorResponse.getError().getType(),
                        errorResponse.getError().getMessage(),
                        errorResponse.getError());
            } else {
                return new ChannelException.OpenAIException(response.code(), "channel_error", msg);
            }
        } catch (Exception e) {
            log.warn("Failed to parse error response", e);
            return new ChannelException.OpenAIException(response.code(), "channel_error", response.message());
        } finally {
            response.close();
        }
    }

    /**
     * 构建 HTTP POST 请求
     */
    private Request buildRequest(ResponsesApiRequest request, String url, ResponsesApiProperty property) {
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)));

        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }
}
