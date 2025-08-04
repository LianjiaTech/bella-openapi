package com.ke.bella.openapi.protocol.speaker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * Speaker embedding request for generating voice embeddings from audio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SpeakerEmbeddingRequest implements UserRequest, Serializable {
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
}