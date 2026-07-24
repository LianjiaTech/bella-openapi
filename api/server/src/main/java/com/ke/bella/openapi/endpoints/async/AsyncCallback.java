package com.ke.bella.openapi.endpoints.async;

import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.async.DeferredResult;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.endpoints.async.AsyncExecutor.AsyncAttributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class AsyncCallback {
    private final HttpServletRequest request;
    private final DeferredResult<Object> deferredResult;
    private final String timeoutMessage;
    private volatile Future<?> future;

    AsyncCallback(HttpServletRequest request, EndpointProcessData processData, long timeoutMs, String timeoutMessage) {
        this.request = request;
        this.timeoutMessage = timeoutMessage;
        this.deferredResult = new DeferredResult<>(resolveTimeoutMs(processData, timeoutMs));
        request.setAttribute(AsyncAttributes.DEFERRED_RESULT, Boolean.TRUE);
        request.setAttribute(AsyncAttributes.PROCESS_DATA, processData);
        this.deferredResult.onTimeout(this::onTimeout);
        this.deferredResult.onError(this::onError);
        this.deferredResult.onCompletion(this::unregister);
    }

    DeferredResult<Object> getDeferredResult() {
        return deferredResult;
    }

    void bind(Future<?> future) {
        this.future = future;
    }

    void complete(Object result) {
        deferredResult.setResult(result);
    }

    void fail(Exception e) {
        deferredResult.setErrorResult(e);
    }

    void unregister() {
        request.removeAttribute(AsyncAttributes.DEFERRED_RESULT);
        request.removeAttribute(AsyncAttributes.PROCESS_DATA);
    }

    private void onTimeout() {
        log.warn("Endpoint blocking callback timeout");
        cancelTask();
        unregister();
        deferredResult.setErrorResult(BellaException.fromResponse(408, timeoutMessage));
    }

    private void onError(Throwable ex) {
        log.warn("Endpoint blocking callback error", ex);
        cancelTask();
        unregister();
    }

    private void cancelTask() {
        Future<?> task = future;
        if(task != null) {
            task.cancel(true);
        }
    }

    private long resolveTimeoutMs(EndpointProcessData processData, long defaultTimeoutMs) {
        Integer maxWaitSec = processData.getMaxWaitSec();
        if(maxWaitSec != null && maxWaitSec > 0) {
            return maxWaitSec * 1000L;
        }
        return defaultTimeoutMs;
    }
}
