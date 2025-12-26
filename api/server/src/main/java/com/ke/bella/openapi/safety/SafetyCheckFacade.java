package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;

import java.util.function.Consumer;

/**
 * 安全检查外观类（Facade Pattern）
 * 封装数据转换和上下文构建逻辑，简化调用方代码
 *
 * <p>使用示例：
 * <pre>
 * // 请求输入检查
 * Object requestRiskData = SafetyCheckFacade.check(
 *     request, processData, apikeyInfo, isMock, safetyService,
 *     result -> processData.setRequestRiskData(result)
 * );
 *
 * // 响应输出检查
 * Object responseRiskData = SafetyCheckFacade.check(
 *     response, processData, apikeyInfo, isMock, safetyService,
 *     result -> processData.addResponseRiskData(result)
 * );
 * </pre>
 */
public class SafetyCheckFacade {

    /**
     * 执行CompletionRequest的安全检查
     *
     * @param request 请求对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     * @param service 安全检查服务
     * @param resultCallback 结果回调函数，用于自定义结果处理逻辑
     * @return 同步模式返回检查结果，异步模式返回null
     */
    public static Object check(CompletionRequest request,
                              EndpointProcessData processData,
                              ApikeyInfo apikeyInfo,
                              boolean isMock,
                              ISafetyCheckService.IChatSafetyCheckService service,
                              Consumer<Object> resultCallback) {
        SafetyCheckRequest.Chat safetyRequest =
                SafetyCheckRequest.Chat.convertFrom(request, processData, apikeyInfo);
        return doCheck(safetyRequest, processData, isMock, service, resultCallback);
    }

    /**
     * 执行CompletionResponse的安全检查
     *
     * @param response 响应对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     * @param service 安全检查服务
     * @param resultCallback 结果回调函数，用于自定义结果处理逻辑
     * @return 同步模式返回检查结果，异步模式返回null（结果会通过callback异步写入队列）
     */
    public static Object check(CompletionResponse response,
                              EndpointProcessData processData,
                              ApikeyInfo apikeyInfo,
                              boolean isMock,
                              ISafetyCheckService.IChatSafetyCheckService service,
                              Consumer<Object> resultCallback) {
        SafetyCheckRequest.Chat safetyRequest =
                SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo);
        return doCheck(safetyRequest, processData, isMock, service, resultCallback);
    }

    /**
     * 执行安全检查的通用逻辑
     *
     * @param safetyRequest 安全检查请求
     * @param processData 处理数据上下文
     * @param isMock 是否Mock模式
     * @param service 安全检查服务
     * @param resultCallback 结果回调函数
     * @return 同步模式返回检查结果，异步模式返回null
     */
    private static Object doCheck(SafetyCheckRequest.Chat safetyRequest,
                                  EndpointProcessData processData,
                                  boolean isMock,
                                  ISafetyCheckService.IChatSafetyCheckService service,
                                  Consumer<Object> resultCallback) {
        if (safetyRequest == null) return null;

        // 构建上下文
        SafetyCheckContext context = SafetyCheckContext.builder()
                .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                .requestId(processData.getRequestId())
                .processData(processData)
                .build();

        // 执行检查
        return ISafetyCheckDelegatorService.create(service, context)
                .safetyCheck(safetyRequest, isMock, resultCallback);
    }
}