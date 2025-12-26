package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import lombok.Builder;
import lombok.Data;

/**
 * 安全检查上下文
 * 保存安全检查所需的执行上下文信息，包括模式等
 * 异步执行统一使用 TaskExecutor
 */
@Data
@Builder
public class SafetyCheckContext {
    /**
     * 安全检查模式：sync, async, skip
     */
    private SafetyCheckMode mode;

    /**
     * 请求ID（用于日志追踪）
     */
    private String requestId;

    /**
     * 阶段标识（input/output，用于日志标识）
     */
    private String stage;

    /**
     * 处理数据（用于存储检测结果）
     */
    private EndpointProcessData processData;
}
