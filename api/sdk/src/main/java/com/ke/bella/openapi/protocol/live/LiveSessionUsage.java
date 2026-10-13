package com.ke.bella.openapi.protocol.live;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class LiveSessionUsage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("input_text_tokens")
    private Long inputTextTokens;
    @JsonProperty("input_audio_tokens")
    private Long inputAudioTokens;
    @JsonProperty("input_video_tokens")
    private Long inputVideoTokens;
    @JsonProperty("output_text_tokens")
    private Long outputTextTokens;
    @JsonProperty("output_audio_tokens")
    private Long outputAudioTokens;
    @JsonProperty("cached_tokens")
    private Long cachedTokens;
    @JsonProperty("tool_calls")
    private Long toolCalls;
    @JsonProperty("session_duration_seconds")
    private Long sessionDurationSeconds;
    @JsonProperty("final_usage_confirmed")
    private Boolean finalUsageConfirmed;
    private Map<String, Object> provider = new HashMap<>();

    public void mergeMaximum(LiveSessionUsage update) {
        if(update == null) {
            return;
        }
        inputTextTokens = max(inputTextTokens, update.inputTextTokens);
        inputAudioTokens = max(inputAudioTokens, update.inputAudioTokens);
        inputVideoTokens = max(inputVideoTokens, update.inputVideoTokens);
        outputTextTokens = max(outputTextTokens, update.outputTextTokens);
        outputAudioTokens = max(outputAudioTokens, update.outputAudioTokens);
        cachedTokens = max(cachedTokens, update.cachedTokens);
        toolCalls = max(toolCalls, update.toolCalls);
        sessionDurationSeconds = max(sessionDurationSeconds, update.sessionDurationSeconds);
        if(update.finalUsageConfirmed != null) {
            finalUsageConfirmed = update.finalUsageConfirmed;
        }
        if(update.provider != null) {
            provider.putAll(update.provider);
        }
    }

    private Long max(Long current, Long update) {
        if(current == null) {
            return update;
        }
        return update == null ? current : Math.max(current, update);
    }
}
