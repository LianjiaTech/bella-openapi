package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface ResponsesAdaptor<T extends ResponsesApiProperty> extends IProtocolAdaptor {

    /**
     * 创建 Response（同步或后台异步取决于 request.background）
     *
     * @param request  ResponsesApiRequest 原生请求（通过 request.background 控制模式）
     * @param url      目标URL
     * @param property 配置属性
     *
     * @return ResponsesApiResponse 原生响应
     *         - background=false: 返回 status=completed
     *         - background=true: 返回 status=pending
     */
    ResponsesApiResponse createResponse(ResponsesApiRequest request, String url, T property);

    /**
     * 创建流式 Response（原生 SSE 格式）
     *
     * @param request  ResponsesApiRequest 原生请求
     * @param url      目标URL
     * @param property 配置属性
     * @param callback 原生 SSE 回调
     */
    void streamResponseAsync(ResponsesApiRequest request, String url, T property,
            Callbacks.ResponsesApiSseCallback callback);

    /**
     * 查询 Response 状态
     *
     * @param responseId Response ID
     * @param url        目标URL（不含 response_id）
     * @param property   配置属性
     *
     * @return ResponsesApiResponse 原生响应
     */
    ResponsesApiResponse getResponse(String responseId, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/responses";
    }
}
