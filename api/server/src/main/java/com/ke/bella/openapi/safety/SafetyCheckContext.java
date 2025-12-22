package com.ke.bella.openapi.safety;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 安全检查上下文
 * 保存安全检查所需的执行上下文信息，包括模式、执行器、回调等
 *
 */
@Data
@Builder
public class SafetyCheckContext {
    /**
     * 安全检查模式：sync, async, skip
     */
    private SafetyCheckMode mode;

    /**
     * 异步执行器（仅异步模式需要）
     */
    private Executor executor;

    /**
     * 结果回调函数（用于同步模式，如SSE发送）
     * 当安全检查返回结果时，会调用此回调处理结果
     */
    private Consumer<Object> resultCallback;

    /**
     * 请求ID（用于日志追踪）
     */
    private String requestId;

    /**
     * 阶段标识（input/output，用于日志标识）
     */
    private String stage;
}
