package com.ke.bella.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求指标数据 - 用于日志处理器的轻量级数据结构
 * 只包含LogHandler真正需要的核心信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestMetrics {

    // ========== TTS相关 ==========
    /**
     * TTS输入文本长度 (TtsLogHandler需要)
     */
    private Integer inputLength;

    // ========== Completion相关 ==========
    /**
     * 预计算的输入token数量 (CompletionLogHandler需要)
     */
    private Integer inputTokens;

    // ========== Embedding相关 ==========
    /**
     * 预计算的embedding token数量 (EmbeddingLogHandler需要)
     */
    private Integer embeddingTokens;

    // ========== 通用信息 ==========
    /**
     * 编码类型
     */
    private String encodingType;
}
