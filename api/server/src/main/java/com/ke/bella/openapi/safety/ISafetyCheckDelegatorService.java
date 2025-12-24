package com.ke.bella.openapi.safety;

/**
 * 安全检查代理服务接口
 * 通过代理模式处理不同的安全检查模式（sync/async/skip）
 *
 * <p>使用方式：
 * <pre>
 * SafetyCheckContext context = SafetyCheckContext.builder()
 *     .mode(SafetyCheckMode.async)
 *     .executor(taskExecutor)
 *     .requestId(requestId)
 *     .stage("input")
 *     .build();
 *
 * ISafetyCheckDelegatorService.create(safetyCheckService, context)
 *     .safetyCheck(request, isMock);
 * </pre>
 *
 * @param <T> 安全检查请求类型
 */
public interface ISafetyCheckDelegatorService<T extends SafetyCheckRequest> extends ISafetyCheckService<T> {

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
