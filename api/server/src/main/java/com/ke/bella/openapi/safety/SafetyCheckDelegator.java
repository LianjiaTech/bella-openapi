package com.ke.bella.openapi.safety;

import java.util.function.Supplier;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.BellaException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全检查代理实现
 * 通过适配器模式根据不同的 mode 执行不同的安全检查策略
 *
 * @param <T> 安全检查请求类型
 */
@Slf4j
@AllArgsConstructor
public class SafetyCheckDelegator<T extends SafetyCheckRequest> implements ISafetyCheckService<T>, ISafetyResultStorageDelegator {

    /**
     * 实际的安全检查服务（单例）
     */
    private final ISafetyCheckService<T> delegate;

    /**
     * 安全检查上下文（包含mode等配置信息）
     */
    private final SafetyCheckMode mode;

    /**
     * 结果存储服务
     */
    private final ISafetyResultStorage storage;

    @Override
    public Object safetyCheck(T request, boolean isMock) {
        if(delegate == null) {
            return null;
        }
        switch (mode) {
        case sync:
            return executeSyncCheck(request, isMock, "sync");
        case skip:
            return null;
        default:
            executeAsyncCheck(request, isMock);
            return null;
        }
    }

    @Override
    public Supplier<Object> prepareAsyncCheck(T request, boolean isMock) {
        return delegate == null ? null : delegate.prepareAsyncCheck(request, isMock);
    }

    private Object executeSyncCheck(T request, boolean isMock, String metricMode) {
        return executeCheck(request, () -> delegate.safetyCheck(request, isMock), metricMode);
    }

    private Object executeCheck(SafetyCheckRequest request, Supplier<Object> check, String metricMode) {
        long startNanos = System.nanoTime();
        String status = "success";
        try {
            Object result = check.get();
            if(result != null && request != null) {
                addRiskData(result, request.isRequest());
            }
            return result;
        } catch (BellaException.SafetyCheckException e) {
            status = "blocked";
            log.warn("安全检测发现敏感数据: requestId={}, mode={}, sensitiveData={}",
                    requestId(request), metricMode, e.getSensitive());
            if(e.getSensitive() != null && request != null) {
                addRiskData(e.getSensitive(), request.isRequest());
            }
            throw e;
        } catch (Exception e) {
            log.warn("安全检测异常: requestId={}, mode={}, error={}",
                    requestId(request), metricMode, e.getMessage(), e);
            status = "error";
            return null;
        } finally {
            SafetyCheckMetricsRecorder.recordCheckLatency(request, metricMode, status, startNanos);
        }
    }

    private void executeAsyncCheck(T request, boolean isMock) {
        SafetyCheckRequest checkContext = snapshotCheckContext(request);
        Supplier<Object> preparedCheck;
        try {
            preparedCheck = delegate.prepareAsyncCheck(request, isMock);
        } catch (Exception e) {
            log.warn("异步安全检测请求准备失败: requestId={}, error={}",
                    requestId(checkContext), e.getMessage(), e);
            return;
        }
        if(preparedCheck == null) {
            log.warn("异步安全检测请求准备结果为空: requestId={}", requestId(checkContext));
            return;
        }
        TaskExecutor.submit(() -> executeCheck(checkContext, preparedCheck, "async"));
    }

    private SafetyCheckRequest snapshotCheckContext(SafetyCheckRequest request) {
        if(request == null) {
            return null;
        }
        return SafetyCheckRequest.builder()
                .requestId(request.getRequestId())
                .type(request.getType())
                .isRequest(request.isRequest())
                .build();
    }

    private String requestId(SafetyCheckRequest request) {
        return request == null ? null : request.getRequestId();
    }

    @Override
    public ISafetyResultStorage getStorage() {
        return storage;
    }
}
