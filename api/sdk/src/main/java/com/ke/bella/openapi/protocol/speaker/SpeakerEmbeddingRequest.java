package com.ke.bella.openapi.protocol.speaker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.ISummary;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * Speaker embedding request for generating voice embeddings from audio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SpeakerEmbeddingRequest implements UserRequest, Serializable, IMemoryClearable, ISummary {
    private static final long serialVersionUID = 1L;

    private String url;
    private String base64;
    private String model;
    private boolean normalize = true;
    private String user = "";
    @JsonProperty("sample_rate")
    private int sampleRate = 16000;
    @JsonProperty("task_id")
    private String taskId = "";

    // Voice Activity Detection parameters
    @JsonProperty("enable_vad")
    private boolean enableVad = false;
    @JsonProperty("vad_aggressiveness")
    private int vadAggressiveness = 1;
    @JsonProperty("min_speech_duration")
    private double minSpeechDuration = 1.0;
    @JsonProperty("max_silence_duration")
    private double maxSilenceDuration = 0.5;

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - base64音频数据
            this.base64 = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

    @Override
    public String[] ignoreFields() {
        return new String[] { "base64" };
    }
}
