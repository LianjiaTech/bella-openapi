package com.ke.bella.openapi.optimization;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.configuration.RequestOptimizationConfig;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求大小检查器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestSizeChecker {

    private final RequestOptimizationConfig config;

    /**
     * 检查是否为大请求
     * 
     * @return true 如果是大请求需要优化
     */
    public boolean isLargeRequest(Object request) {
        if(!config.isEnabled()) {
            return false;
        }

        try {
            int requestSize = calculateRequestSize();
            boolean isLarge = requestSize > config.getLargeRequestThreshold();

            if(isLarge) {
                requestSize = calculateRequestSize(request);
                isLarge = requestSize > config.getLargeRequestThreshold();
            }

            if(isLarge) {
                EndpointContext.markLargeRequest();
                log.debug("Large request detected: {} bytes (threshold: {} bytes)",
                        requestSize, config.getLargeRequestThreshold());
            }

            return isLarge;
        } catch (Exception e) {
            log.warn("Failed to check request size", e);
            return false;
        }
    }

    /**
     * 计算请求大小
     */
    private int calculateRequestSize() {
        HttpServletRequest request = EndpointContext.getRequestIgnoreNull();
        if(request != null) {
            return request.getContentLength();
        }
        return 0;
    }

    /**
     * 计算请求大小
     */
    private int calculateRequestSize(Object request) {
        if(request != null) {
            return JacksonUtils.toByte(request).length;
        }
        return 0;
    }
}
