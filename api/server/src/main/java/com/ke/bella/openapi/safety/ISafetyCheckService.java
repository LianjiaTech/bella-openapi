package com.ke.bella.openapi.safety;

import java.util.concurrent.CompletableFuture;

public interface ISafetyCheckService<T extends SafetyCheckRequest> {
    Object safetyCheck(T request, boolean isMock);

    /**
     * 异步安全检测方法
     * @param request 安全检测请求
     * @param isMock 是否Mock模式
     * @return CompletableFuture包装的检测结果
     */
    default CompletableFuture<Object> safetyCheckAsync(T request, boolean isMock) {
        return CompletableFuture.completedFuture(safetyCheck(request, isMock));
    }

    interface IChatSafetyCheckService extends ISafetyCheckService<SafetyCheckRequest.Chat> {
    }
}
