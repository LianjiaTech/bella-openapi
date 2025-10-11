package com.ke.bella.openapi.optimization;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 请求优化AOP切面
 * 拦截EndpointDataService的setEndpointData方法，在设置request后自动触发请求优化处理
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bella.request.optimization.enabled", havingValue = "true", matchIfMissing = true)
public class RequestOptimizationAspect {

    private final StageLoggingProcessor stageLoggingProcessor;
    private final RequestSizeChecker requestSizeChecker;

    /**
     * 拦截EndpointDataService中的setEndpointData方法
     * 在设置request后立即检查并处理大请求
     */
    @AfterReturning("execution(* com.ke.bella.openapi.service.EndpointDataService.setEndpointData(..))")
    public void afterSetEndpointData(JoinPoint joinPoint) {
        try {
            EndpointProcessData processData = EndpointContext.getProcessData();
            if (processData == null) {
                log.debug("ProcessData is null, skipping optimization");
                return;
            }

            Object request = processData.getRequest();
            if (request == null) {
                log.debug("Request is null, skipping optimization");
                return;
            }

            String endpoint = processData.getEndpoint();
            String requestId = processData.getRequestId();

            // 检查是否为大请求
            if (requestSizeChecker.isLargeRequest()) {
                log.info("Large request detected, starting async optimization. " +
                    "Endpoint: {}, RequestId: {}", endpoint, requestId);

                // 异步处理大请求
                stageLoggingProcessor.preprocessRequestAsync(processData, request);

            } else {
                log.debug("Small request, no optimization needed. " +
                    "Endpoint: {}, RequestId: {}", endpoint, requestId);
            }

        } catch (Exception e) {
            log.error("Error in request optimization aspect", e);
            // 不抛出异常，避免影响正常业务流程
        }
    }
}
