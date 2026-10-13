package com.ke.bella.openapi.endpoints.async;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;

@Component
public class AsyncExecutor {
    private static final String DEFAULT_TIMEOUT_MESSAGE = "request timeout";
    private static final String DEFAULT_OVERLOADED_MESSAGE = "request overloaded, please retry later";

    private final ThreadPoolTaskExecutor executor;
    private final long timeoutMs;

    public AsyncExecutor(@Qualifier("endpointAsyncExecutor") ThreadPoolTaskExecutor executor,
            @Value("${bella.endpoint.async.timeout-ms:${bella.chat.async.timeout-ms:600000}}") long timeoutMs) {
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    public DeferredResult<Object> submit(HttpServletRequest request, Callable<Object> task) {
        RequestContextSnapshot snapshot = RequestContextSnapshot.capture();
        AsyncCallback callback = new AsyncCallback(request, snapshot.getProcessData(), timeoutMs, DEFAULT_TIMEOUT_MESSAGE);
        try {
            Future<?> future = executor.submit(() -> {
                snapshot.restore();
                try {
                    callback.complete(task.call());
                } catch (Exception e) {
                    callback.fail(e);
                } finally {
                    EndpointContext.clearAll();
                }
            });
            callback.bind(future);
        } catch (RejectedExecutionException e) {
            callback.unregister();
            throw new BellaException.RateLimitException(DEFAULT_OVERLOADED_MESSAGE);
        }
        return callback.getDeferredResult();
    }

    public static final class AsyncAttributes {
        public static final String DEFERRED_RESULT = AsyncAttributes.class.getName() + ".DEFERRED_RESULT";
        public static final String PROCESS_DATA = AsyncAttributes.class.getName() + ".PROCESS_DATA";

        private AsyncAttributes() {
        }
    }

    private static class RequestContextSnapshot {
        private final Map<String, Object> bellaContext;
        private final EndpointProcessData processData;

        private RequestContextSnapshot(Map<String, Object> bellaContext, EndpointProcessData processData) {
            this.bellaContext = bellaContext;
            this.processData = processData;
        }

        private static RequestContextSnapshot capture() {
            return new RequestContextSnapshot(copyBellaContext(BellaContext.snapshot()), EndpointContext.getProcessData());
        }

        private void restore() {
            BellaContext.replace(bellaContext);
            EndpointContext.setProcessData(processData);
        }

        private EndpointProcessData getProcessData() {
            return processData;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static Map<String, Object> copyBellaContext(Map<String, Object> context) {
            Map<String, Object> copied = new HashMap<>(context);
            Object headers = copied.get("headers");
            if(headers instanceof Map) {
                copied.put("headers", new HashMap<>((Map) headers));
            }
            return copied;
        }
    }
}
