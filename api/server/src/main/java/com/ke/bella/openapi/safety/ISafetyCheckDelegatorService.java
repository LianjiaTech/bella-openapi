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
     * 执行安全检查
     * 根据 context 中的 mode 决定执行策略（同步/异步/跳过）
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     * @return 检查结果（异步模式和跳过模式返回null）
     */
    @Override
    Object safetyCheck(T request, boolean isMock);

    /**
     * 工厂方法：创建代理服务实例
     * 从 delegate service 中自动获取 executor
     *
     * @param delegate 实际的安全检查服务（单例）
     * @param context 安全检查上下文
     * @param <T> 请求类型
     * @return 代理服务实例
     */
    static <T extends SafetyCheckRequest> ISafetyCheckDelegatorService<T> create(
            ISafetyCheckService<T> delegate,
            SafetyCheckContext context) {
        // 如果 context 中没有设置 executor，从 service 中获取
        if (context.getExecutor() == null && delegate.getExecutor() != null) {
            context = SafetyCheckContext.builder()
                    .mode(context.getMode())
                    .executor(delegate.getExecutor())
                    .resultCallback(context.getResultCallback())
                    .requestId(context.getRequestId())
                    .stage(context.getStage())
                    .build();
        }
        return new SafetyCheckDelegator<>(delegate, context);
    }

    /**
     * Chat 类型的代理服务接口
     */
    interface IChatSafetyCheckDelegatorService
            extends ISafetyCheckDelegatorService<SafetyCheckRequest.Chat> {
    }
}
