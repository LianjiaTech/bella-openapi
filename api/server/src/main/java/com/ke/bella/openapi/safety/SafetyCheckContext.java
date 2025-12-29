package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 安全检查上下文
 * 保存安全检查所需的执行上下文信息，包括模式、检查结果等
 * 异步执行统一使用 TaskExecutor
 *
 * <p>统一入口：提供便捷方法简化 Controller 层调用
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

    /**
     * 安全检查代理服务（执行器）
     */
    private transient ISafetyCheckDelegatorService<?> delegator;

    /**
     * 请求输入安全检查结果
     */
    private Object requestRiskData;

    /**
     * 响应输出安全检查结果队列（支持流式异步多次检查）
     * 使用 ConcurrentLinkedQueue 保证线程安全
     */
    @Builder.Default
    private final ConcurrentLinkedQueue<Object> responseRiskDataQueue = new ConcurrentLinkedQueue<>();

	/**
     * 添加响应输出安全检查结果到队列
     *
     * @param riskData 响应输出风险数据
     */
    public void addResponseRiskData(Object riskData) {
        if (riskData != null) {
            responseRiskDataQueue.offer(riskData);
        }
    }

    /**
     * 从队列中取出响应安全检查结果（消费性读取）
     *
     * @return 响应输出风险数据
     */
    public Object pollResponseRiskData() {
        return responseRiskDataQueue.poll();
    }

    /**
     * 检查队列中是否有响应安全检查结果
     *
     * @return true if 队列中有数据
     */
    public boolean hasResponseRiskData() {
        return !responseRiskDataQueue.isEmpty();
    }

    // ========== Controller 层便捷方法 ==========

    /**
     * 执行请求安全检查（便捷方法）
     * 封装 delegator 调用，简化 Controller 层代码
     *
     * @param request 请求对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    public void checkRequest(CompletionRequest request, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        if (delegator != null) {
            delegator.check(request, processData, apikeyInfo, isMock);
        }
    }

    /**
     * 执行响应安全检查并填充风险数据到响应对象（便捷方法）
     * 一次调用完成：执行检查 + 填充风险数据
     *
     * @param response 响应对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    public void checkResponseAndFillRiskData(CompletionResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        if (delegator != null) {
            // 执行响应安全检查
            delegator.check(response, processData, apikeyInfo, isMock);
        }

        // 填充风险数据到响应对象
        response.setSensitives(pollResponseRiskData());
        response.setRequestRiskData(getRequestRiskData());
    }

    /**
     * 仅执行响应安全检查，不填充数据（用于流式场景）
     *
     * @param response 响应对象
     * @param processData 处理数据上下文
     * @param apikeyInfo API密钥信息
     * @param isMock 是否Mock模式
     */
    public void checkResponse(CompletionResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        if (delegator != null) {
            delegator.check(response, processData, apikeyInfo, isMock);
        }
    }
}
