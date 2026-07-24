package com.ke.bella.openapi.protocol.realtime;

import lombok.Getter;

@Getter
public enum RealTimeEventType {
    // 转录
    // 会话开始事件
    TRANSCRIPTION_STARTED("TranscriptionStarted", true, true),
    // 句子开始事件
    SENTENCE_BEGIN("SentenceBegin", true, true),
    // 中间识别结果事件
    TRANSCRIPTION_RESULT_CHANGED("TranscriptionResultChanged", true, true),
    // 句子结束事件
    SENTENCE_END("SentenceEnd", true, true),
    // 转录完成事件
    TRANSCRIPTION_COMPLETED("TranscriptionCompleted", true, true),
    // 转录失败事件
    TRANSCRIPTION_FAILED("TranscriptionFailed", true, false),
    // 开始转录请求
    START_TRANSCRIPTION("StartTranscription", false, true),
    // 结束转录请求
    STOP_TRANSCRIPTION("StopTranscription", false, true),

    // VAD
    // 静音事件
    VOICE_QUIET("VOICE_QUIET", true, true),
    // 开始说话事件
    VOICE_STARTING("VOICE_STARTING", true, true),
    // 说话中事件
    VOICE_SPEAKING("VOICE_SPEAKING", true, true),
    // 停止说话事件
    VOICE_STOPPING("VOICE_STOPPING", true, true),
    // 说话暂停事件
    VOICE_PAUSING("VOICE_PAUSING", true, true),

    // LLM相关事件
    LLM_CHAT_BEGIN("LLM_CHAT_BEGIN", true, true),
    LLM_CHAT_DELTA("LLM_CHAT_DELTA", true, true),
    LLM_CHAT_END("LLM_CHAT_END", true, true),
    LLM_CHAT_CANCELLED("LLM_CHAT_CANCELLED", true, true),
    LLM_CHAT_ERROR("LLM_CHAT_ERROR", true, false),
    LLM_WORKER_BEGIN("LLM_WORKER_BEGIN", true, false),
    LLM_WORKER_END("LLM_WORKER_END", true, false),
    LLM_WORKER_CANCELLED("LLM_WORKER_CANCELLED", true, false),
    LLM_WORKER_ERROR("LLM_WORKER_ERROR", true, false),

    // TTS相关事件
    TTS_BEGIN("TTS_BEGIN", true, true),
    TTS_TTFT("TTS_TTFT", true, true),
    TTS_DELTA("TTS_DELTA", true, true),
    TTS_END("TTS_END", true, true),

    // 本轮会话信息
    THREAD_MESSSATE_CREATED("THREAD_MESSSATE_CREATED", true, true),

    // 未知事件
    UNKNOWN("Unknown", true, false),

    // 任务失败事件
    TASK_FAILED("TaskFailed", true, false),

    // 关闭会话
    SESSION_CLOSE("SESSION_CLOSE", true, true);

    private final String value;
    private final boolean server;
    private final boolean success;

    RealTimeEventType(String value, boolean sever, boolean success) {
        this.value = value;
        this.server = sever;
        this.success = success;
    }

    public static RealTimeEventType fromString(String text) {
        for (RealTimeEventType type : RealTimeEventType.values()) {
            if(type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
