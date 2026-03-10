package com.ke.bella.openapi.safety;

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
            return executeSyncCheck(request, isMock);
        case skip:
            return null;
        default:
            executeAsyncCheck(request, isMock);
            return null;
        }
    }

    private Object executeSyncCheck(T request, boolean isMock) {
        try {
            Object result = delegate.safetyCheck(request, isMock);
            if(result != null) {
                addRiskData(result, request.isRequest());
            }
            return result;
        } catch (BellaException.SafetyCheckException e) {
            log.warn("异步安全检测发现敏感数据: requestId={}, sensitiveData={}",
                    request.getRequestId(), e.getSensitive());
            if(e.getSensitive() != null) {
                addRiskData(e.getSensitive(), request.isRequest());
            }
            return e.getSensitive();
        } catch (Exception e) {
            log.warn("异步安全检测异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return null;
        }
    }

    private void executeAsyncCheck(T request, boolean isMock) {
        TaskExecutor.submit(() -> executeSyncCheck(request, isMock));
    }

    @Override
    public ISafetyResultStorage getStorage() {
        return storage;
    }
}
