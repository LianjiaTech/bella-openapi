package com.ke.bella.openapi.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 请求优化配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "bella.request.optimization")
public class RequestOptimizationConfig {

    /**
     * 是否启用请求优化
     */
    private boolean enabled = true;

    /**
     * 大请求阈值，超过此大小的请求会被优化处理（单位：字节）
     * 默认 100K
     */
    private int largeRequestThreshold = 1024 * 100;

    /**
     * 异步处理线程池大小
     */
    private int asyncThreadPoolSize = 4;

    /**
     * 请求摘要的最大长度（单位：字符）
     */
    private int requestSummaryMaxLength = 5000;
}
