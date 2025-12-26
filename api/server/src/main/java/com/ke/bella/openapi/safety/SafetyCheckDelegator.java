package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 安全检查代理实现
 * 通过适配器模式根据不同的 mode 执行不同的安全检查策略
 *
 * <p>支持三种模式：
 * <ul>
 *   <li>sync: 同步模式，等待检查结果，异常会阻断主流程</li>
 *   <li>async: 异步模式，在独立线程池执行，不阻断主流程，异常仅记录日志</li>
 *   <li>skip: 跳过模式，不执行任何检查</li>
 * </ul>
 *
 * @param <T> 安全检查请求类型
 */
@Slf4j
public class SafetyCheckDelegator<T extends SafetyCheckRequest>
        implements ISafetyCheckDelegatorService<T> {

    /**
     * 实际的安全检查服务（单例）
     */
    private final ISafetyCheckService<T> delegate;

    /**
     * 安全检查上下文（包含mode、executor等信息）
     */
    private final SafetyCheckContext context;

    /**
     * 请求输入安全检查结果
     */
    private Object requestRiskData;

    /**
     * 响应输出安全检查结果队列（支持异步多次检查）
     */
    private final ConcurrentLinkedQueue<Object> responseRiskDataQueue = new ConcurrentLinkedQueue<>();

    /**
     * 构造函数
     *
     * @param delegate 实际的安全检查服务
     * @param context 安全检查上下文
     */
    public SafetyCheckDelegator(ISafetyCheckService<T> delegate, SafetyCheckContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Override
    public Object safetyCheck(T request, boolean isMock) {
        return safetyCheck(request, isMock, null);
    }

    @Override
    public Object safetyCheck(T request, boolean isMock, java.util.function.Consumer<Object> resultCallback) {
        SafetyCheckMode mode = context.getMode() != null ? context.getMode() : SafetyCheckMode.getDefault();

        switch (mode) {
            case sync:
                return executeSyncCheck(request, isMock, resultCallback);
            case async:
                executeAsyncCheck(request, isMock, resultCallback);
                return null;
            case skip:
                return null;
            default:
                // 默认使用异步模式
                executeAsyncCheck(request, isMock, resultCallback);
                return null;
        }
    }

    /**
     * 执行同步安全检查
     * 等待检查结果，如果检测到敏感数据会抛出异常阻断主流程
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     * @param resultCallback 结果回调函数
     * @return 检查结果
     */
    private Object executeSyncCheck(T request, boolean isMock, java.util.function.Consumer<Object> resultCallback) {
        Object result = delegate.safetyCheck(request, isMock);

        // 同步模式：立即调用callback处理结果
        if (result != null && resultCallback != null) {
            resultCallback.accept(result);
        }

        return result;
    }

    /**
     * 执行异步安全检查
     * 在 TaskExecutor 线程池中执行检测，不等待结果，不阻断主流程
     * 检测到敏感数据或异常时仅记录日志，并通过callback处理结果
     *
     * @param request 安全检查请求
     * @param isMock 是否Mock模式
     * @param resultCallback 结果回调函数
     */
    private void executeAsyncCheck(T request, boolean isMock, java.util.function.Consumer<Object> resultCallback) {
        TaskExecutor.submit(() -> {
            try {
                Object result = delegate.safetyCheck(request, isMock);

                // 异步模式：在线程池中执行后调用callback
                if (result != null && resultCallback != null) {
                    resultCallback.accept(result);
                }

            } catch (ChannelException.SafetyCheckException e) {
                // 异步模式：检测到敏感数据，仅记录日志，不阻断主流程
                log.warn("异步安全检测发现敏感数据: requestId={}, sensitiveData={}",
                        context.getRequestId(), e.getSensitive());

                // 将异常中的敏感数据也通过callback返回
                if (e.getSensitive() != null && resultCallback != null) {
                    resultCallback.accept(e.getSensitive());
                }

            } catch (Exception e) {
                // 异步模式：其他异常（如网络错误），仅记录日志，不阻断主流程
                log.warn("异步安全检测异常: requestId={}, error={}",
                        context.getRequestId(), e.getMessage(), e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void checkRequest(CompletionRequest request, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        // 数据转换
        SafetyCheckRequest.Chat safetyRequest =
                SafetyCheckRequest.Chat.convertFrom(request, processData, apikeyInfo);
        if (safetyRequest == null) return;

        // 执行检查，结果保存到 requestRiskData
        Object result = safetyCheck((T) safetyRequest, isMock, riskData -> {
            this.requestRiskData = riskData;
        });

        // 同步模式会立即返回结果
        if (result != null) {
            this.requestRiskData = result;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void checkResponse(CompletionResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo, boolean isMock) {
        // 数据转换
        SafetyCheckRequest.Chat safetyRequest =
                SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo);
        if (safetyRequest == null) return;

        // 执行检查，结果通过callback写入队列
        safetyCheck((T) safetyRequest, isMock, this::addResponseRiskData);
    }

    @Override
    public Object getRequestRiskData() {
        return requestRiskData;
    }

    @Override
    public void addResponseRiskData(Object riskData) {
        if (riskData != null) {
            responseRiskDataQueue.offer(riskData);
        }
    }

    @Override
    public Object pollResponseRiskData() {
        return responseRiskDataQueue.poll();
    }

    @Override
    public boolean hasResponseRiskData() {
        return !responseRiskDataQueue.isEmpty();
    }
}
