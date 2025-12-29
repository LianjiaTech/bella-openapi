package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;

import java.util.function.Consumer;

/**
 * 安全检查代理服务接口
 * 通过代理模式处理不同的安全检查模式（sync/async/skip）
 *
 * <p>使用示例：
 * <pre>
 * // 初始化阶段（在请求处理开始时创建，存储在 processData 中）
 * SafetyCheckContext context = SafetyCheckContext.builder()
 *     .mode(SafetyCheckMode.fromString(property.getSafetyCheckMode()))
 *     .requestId(processData.getRequestId())
 *     .processData(processData)
 *     .build();
 * ISafetyCheckDelegatorService delegator = ISafetyCheckDelegatorService.create(safetyCheckService, context);
 * context.setDelegator(delegator);
 * processData.setSafetyCheckContext(context);
 *
 * // 使用阶段 - 执行安全检查（统一通过 context 入口）
 * context.checkRequest(request, processData, apikeyInfo, isMock);
 * context.checkResponseAndFillRiskData(response, processData, apikeyInfo, isMock);
 *
 * // 或者获取结果 - 直接从 context 获取
 * response.setRequestRiskData(context.getRequestRiskData());
 * response.setSensitives(context.pollResponseRiskData());
 * </pre>
 *
 * @param <T> 安全检查请求类型
 */
public interface ISafetyCheckDelegatorService<T extends SafetyCheckRequest> extends ISafetyCheckService<T> {

    /**
     * 统一安全检查入口 - 请求检查
     * 通过方法重载自动识别检查类型，简化调用代码
     *
     * @param request 请求对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    default void check(CompletionRequest request, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        checkRequest(request, processData, apikeyInfo, isMock);
    }

    /**
     * 统一安全检查入口 - 响应检查
     * 通过方法重载自动识别检查类型，简化调用代码
     *
     * @param response 响应对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    default void check(CompletionResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        checkResponse(response, processData, apikeyInfo, isMock);
    }

    /**
     * 执行请求输入安全检查
     * 内部会调用 Chat.convertFrom 进行数据转换
     *
     * @param request 请求对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    void checkRequest(CompletionRequest request, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock);

    /**
     * 执行响应输出安全检查
     * 内部会调用 Chat.convertFrom 进行数据转换
     *
     * @param response 响应对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    void checkResponse(CompletionResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock);

    /**
     * 执行安全检查（底层方法，带结果回调）
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     * @param resultCallback 结果回调函数，用于自定义结果处理逻辑
     * @return 同步模式返回检查结果，异步模式返回null
     */
    Object safetyCheck(T request, boolean isMock, Consumer<Object> resultCallback);

    /**
     * 工厂方法：创建代理服务实例
     *
     * @param delegate 实际的安全检查服务（单例）
     * @param context 安全检查上下文
     * @param <T> 请求类型
     * @return 代理服务实例
     */
    static <T extends SafetyCheckRequest> ISafetyCheckDelegatorService<T> create(
            ISafetyCheckService<T> delegate,
            SafetyCheckContext context) {
        return new SafetyCheckDelegator<>(delegate, context);
    }
}
