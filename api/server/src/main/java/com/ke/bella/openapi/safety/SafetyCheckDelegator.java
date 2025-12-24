package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.ChannelException;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全检查代理实现
 * 通过适配器模式根据不同的 mode 执行不同的安全检查策略
 *
 * <p>支持三种模式：
 * <ul>
 *   <li>sync: 同步模式，等待检查结果，异常会阻断主流程</li>
 *   <li>async: 异步模式，在独立线程池执行，不阻断主流程，异常仅记录日志</li>
 *   <li>skip: 跳过模式，不执行任何检查</li>
 * </ul>
 *
 * @param <T> 安全检查请求类型
 */
@Slf4j
public class SafetyCheckDelegator<T extends SafetyCheckRequest>
        implements ISafetyCheckDelegatorService<T> {

    /**
     * 实际的安全检查服务（单例）
     */
    private final ISafetyCheckService<T> delegate;

    /**
     * 安全检查上下文（包含mode、executor等信息）
     */
    private final SafetyCheckContext context;

    /**
     * 构造函数
     *
     * @param delegate 实际的安全检查服务
     * @param context 安全检查上下文
     */
    public SafetyCheckDelegator(ISafetyCheckService<T> delegate, SafetyCheckContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Override
    public Object safetyCheck(T request, boolean isMock) {
        SafetyCheckMode mode = context.getMode() != null ? context.getMode() : SafetyCheckMode.getDefault();

        switch (mode) {
            case sync:
                return executeSyncCheck(request, isMock);
            case async:
                executeAsyncCheck(request, isMock);
                return null;
            case skip:
                return null;
            default:
                // 默认使用同步模式
                return executeSyncCheck(request, isMock);
        }
    }

    /**
     * 执行同步安全检查
     * 等待检查结果，如果检测到敏感数据会抛出异常阻断主流程
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     * @return 检查结果
     */
    private Object executeSyncCheck(T request, boolean isMock) {
        try {
            Object result = delegate.safetyCheck(request, isMock);

            // 同步模式：也写入 processData（统一数据源）
            if (result != null && context.getProcessData() != null
                && "output".equals(context.getStage())) {
                context.getProcessData().setResponseRiskData(result);
            }

            return result;
        } catch (ChannelException.SafetyCheckException e) {
            // 同步模式：直接抛出异常，阻断主流程
            throw e;
        }
    }

    /**
     * 执行异步安全检查
     * 在 TaskExecutor 线程池中执行检测，不等待结果，不阻断主流程
     * 检测到敏感数据或异常时仅记录日志，并将结果写入 processData
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     */
    private void executeAsyncCheck(T request, boolean isMock) {
        TaskExecutor.submit(() -> {
            try {
                Object result = delegate.safetyCheck(request, isMock);

                // 异步模式：将结果写入 processData
                if (result != null && context.getProcessData() != null
                    && "output".equals(context.getStage())) {
                    context.getProcessData().setResponseRiskData(result);
                    log.debug("异步安全检测完成: requestId={}, stage={}",
                             context.getRequestId(), context.getStage());
                }

            } catch (ChannelException.SafetyCheckException e) {
                // 异步模式：检测到敏感数据，仅记录日志，不阻断主流程
                // 统一从 context 中获取日志所需信息
                log.warn("异步安全检测发现敏感数据: requestId={}, stage={}, sensitiveData={}",
                        context.getRequestId(), context.getStage(), e.getSensitive());

                // 将异常中的敏感数据也存储到 processData
                if (context.getProcessData() != null && "output".equals(context.getStage())) {
                    context.getProcessData().setResponseRiskData(e.getSensitive());
                }

            } catch (Exception e) {
                // 异步模式：其他异常（如网络错误），仅记录日志，不阻断主流程
                log.warn("异步安全检测异常: requestId={}, stage={}, error={}",
                        context.getRequestId(), context.getStage(), e.getMessage(), e);
            }
        });
    }
}
