package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * OpenAI Responses API Adapter
 * 使用 store=false 和 previous_response_id 为空的 responses api 模拟 chat completion 功能
 */
@Component("ResponsesApiAdaptor")
@Slf4j
public class ResponsesApiAdaptor implements CompletionAdaptor<ResponsesApiProperty> {

    @Override
    public String getDescription() {
        return "OpenAI Responses API协议";
    }

    @Override
    public Class<ResponsesApiProperty> getPropertyClass() {
        return ResponsesApiProperty.class;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, ResponsesApiProperty property) {
        log.debug("Converting Chat Completion request to Responses API format");

        // 转换请求格式
        ResponsesApiRequest responsesRequest = ResponsesApiConverter.convertChatCompletionToResponses(request,
                EndpointContext.getProcessData().getAkCode());

        // 构建HTTP请求
        Request httpRequest = buildResponsesApiRequest(responsesRequest, url, property);
        clearLargeData(request, responsesRequest);
        // 发送请求并获取Responses API响应
        ResponsesApiResponse responsesResponse = HttpUtils.httpRequest(httpRequest, ResponsesApiResponse.class,
                (errorResponse, res) -> {
                    if(errorResponse.getError() != null) {
                        errorResponse.getError().setHttpCode(res.code());
                    }
                });
        // 转换为Chat Completion格式
        CompletionResponse response = ResponsesApiConverter.convertResponsesToChatCompletion(responsesResponse);
        response.setCreated(DateTimeUtils.getCurrentSeconds());

        log.debug("Converted Responses API response to Chat Completion format");
        return response;
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, ResponsesApiProperty property,
            Callbacks.StreamCompletionCallback callback) {
        log.debug("Converting Chat Completion stream request to Responses API format");

        // 转换请求格式
        ResponsesApiRequest responsesRequest = ResponsesApiConverter.convertChatCompletionToResponses(request,
                EndpointContext.getProcessData().getAkCode());
        responsesRequest.setStream(true);  // 确保启用流式

        // 创建 SSE 转换器和监听器
        ResponsesApiSseConverter sseConverter = new ResponsesApiSseConverter();
        CompletionSseListener listener = new CompletionSseListener(callback, sseConverter);

        Request httpRequest = buildResponsesApiRequest(responsesRequest, url, property);
        clearLargeData(request, responsesRequest);
        // 发送流式请求
        HttpUtils.streamRequest(httpRequest, listener);

        log.debug("Started Responses API stream conversion");
    }

    /**
     * 构建 Responses API HTTP 请求
     */
    private Request buildResponsesApiRequest(ResponsesApiRequest request, String url, ResponsesApiProperty property) {
        // 设置部署模型名称
        request.setModel(property.getDeployName());

        // 确保 store=false 和 previous_response_id=null (用于模拟 chat completion)
        request.setStore(false);
        request.setPrevious_response_id(null);

        // 添加API版本
        if(StringUtils.isNotEmpty(property.getApiVersion())) {
            url += property.getApiVersion();
        }

        // 构建请求
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)));

        // 添加额外的请求头
        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }
}
