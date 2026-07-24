package com.ke.bella.openapi.protocol.limiter;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * QPS 限流检查结果
 */
@Data
@AllArgsConstructor
public class QpsCheckResult {
    /**
     * 是否允许通过
     */
    private boolean allowed;

    /**
     * 当前 QPS
     */
    private long currentQps;

    /**
     * QPS 限制值
     */
    private int limit;

    /**
     * 创建通过结果
     */
    public static QpsCheckResult allowed(long currentQps, int limit) {
        return new QpsCheckResult(true, currentQps, limit);
    }

    /**
     * 创建拒绝结果
     */
    public static QpsCheckResult rejected(long currentQps, int limit) {
        return new QpsCheckResult(false, currentQps, limit);
    }

    /**
     * 创建跳过检查结果（限流关闭或不限制）
     */
    public static QpsCheckResult skipped() {
        return new QpsCheckResult(true, 0, -1);
    }
}