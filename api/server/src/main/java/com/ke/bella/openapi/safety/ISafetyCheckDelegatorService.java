package com.ke.bella.openapi.safety;

import java.util.function.Consumer;

/**
 * 安全检查代理服务接口
 * 通过代理模式处理不同的安全检查模式（sync/async/skip）
 *
 * <p><b>推荐使用 {@link SafetyCheckFacade} 工具类</b>，它封装了数据转换、上下文构建等细节：
 * <pre>
 * // 请求输入检查
 * SafetyCheckFacade.check(request, processData, apikeyInfo, isMock, safetyService,
 *     processData::setRequestRiskData);
 *
 * // 响应输出检查
 * SafetyCheckFacade.check(response, processData, apikeyInfo, isMock, safetyService,
 *     processData::addResponseRiskData);
 * </pre>
 *
 * <p>底层使用方式（带callback）：
 * <pre>
 * SafetyCheckContext context = SafetyCheckContext.builder()
 *     .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
 *     .requestId(processData.getRequestId())
 *     .processData(processData)
 *     .build();
 *
 * ISafetyCheckDelegatorService.create(safetyCheckService, context)
 *     .safetyCheck(safetyRequest, isMock, result -> processData.addResponseRiskData(result));
 * </pre>
 *
 * @param <T> 安全检查请求类型
 */
public interface ISafetyCheckDelegatorService<T extends SafetyCheckRequest> extends ISafetyCheckService<T> {

    /**
     * 执行安全检查（带结果回调）
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
