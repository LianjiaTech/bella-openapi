package com.ke.bella.openapi.safety;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 流式安全检查通用组件。
 * <p>
 * 负责文本积累、阈值判断和调用安全服务，不关心具体协议格式。
 * 三个流式 Callback（Chat/Messages/Responses）通过组合此组件来实现安全检查，
 * 避免重复的阈值逻辑和状态管理代码。
 * <p>
 * 支持两种使用方式：
 * <ul>
 *   <li>内置 buffer 模式：调用 {@link #accumulate(String)} 积累文本，check 时自动读取</li>
 *   <li>外部文本模式：调用 {@link #check(boolean, String, Function)} 直接传入当前全量文本</li>
 * </ul>
 */
@Slf4j
public class StreamSafetyChecker {

    private static final int DEFAULT_CHAR_THRESHOLD = 100;
    private static volatile int configuredCharThreshold = DEFAULT_CHAR_THRESHOLD;

    @Component
    static class Config {
        @Value("${safety.check.stream.char-threshold:100}")
        public void setCharThreshold(int threshold) {
            configuredCharThreshold = threshold;
        }
    }

    private final ISafetyCheckService<SafetyCheckRequest.Chat> safetyService;
    private final boolean isMock;
    private final StringBuilder textBuffer = new StringBuilder();
    private int checkIndex = 0;
    private volatile boolean blocked = false;

    public StreamSafetyChecker(ISafetyCheckService<SafetyCheckRequest.Chat> safetyService, boolean isMock) {
        this.safetyService = safetyService;
        this.isMock = isMock;
    }

    /**
     * 积累文本内容（内置 buffer 模式）
     */
    public void accumulate(String text) {
        if(text != null) {
            textBuffer.append(text);
        }
    }

    /**
     * 获取内置 buffer 中积累的文本
     */
    public String getBufferedText() {
        return textBuffer.toString();
    }

    /**
     * 通知阶段切换（如 reasoning → content），重置检查位置
     */
    public void notifyPhaseChange() {
        checkIndex = 0;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void markBlocked() {
        blocked = true;
    }

    /**
     * 使用内置 buffer 的文本执行安全检查。
     *
     * @param done           是否是最终检查
     * @param requestBuilder 构造安全检查请求的供应者
     * @return 是否实际执行了安全检查
     * @throws com.ke.bella.openapi.common.exception.BellaException.SafetyCheckException 命中拦截时抛出
     */
    public boolean check(boolean done, Supplier<SafetyCheckRequest.Chat> requestBuilder) {
        return check(done, textBuffer.toString(), requestBuilder);
    }

    /**
     * 使用外部提供的文本执行安全检查。
     * <p>
     * 适用于文本由外部（如 choiceBuffer）管理的场景。
     *
     * @param done           是否是最终检查
     * @param currentText    当前全量文本，用于阈值判断
     * @param requestBuilder 构造安全检查请求的供应者
     * @return 是否实际执行了安全检查
     * @throws com.ke.bella.openapi.common.exception.BellaException.SafetyCheckException 命中拦截时抛出
     */
    public boolean check(boolean done, String currentText, Supplier<SafetyCheckRequest.Chat> requestBuilder) {
        if(safetyService == null) {
            return false;
        }
        if(currentText == null || currentText.isEmpty()) {
            return false;
        }
        if(!done) {
            String delta = currentText.substring(checkIndex);
            if(delta.length() < configuredCharThreshold) {
                return false;
            }
            checkIndex = currentText.length();
        }
        long startNanos = System.nanoTime();
        try {
            SafetyCheckRequest.Chat request = requestBuilder.get();
            safetyService.safetyCheck(request, isMock);
        } finally {
            SafetyCheckMetricsRecorder.recordStreamLatency(done, startNanos);
        }
        return true;
    }
}
