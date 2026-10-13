package com.ke.bella.openapi.protocol.asr.diarization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Speaker diarization response containing transcribed segments with speaker
 * identification
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
public class SpeakerDiarizationResponse extends OpenapiResponse {
    private String task = "speaker_diarization";
    @JsonProperty("task_id")
    private String taskId = "";
    private String language = "zh";
    private float duration = 0;
    private String text = "";
    private List<Segment> segments;
    @JsonProperty("num_speakers")
    private int numSpeakers = 0;
    @JsonProperty("speaker_embeddings")
    private Map<String, List<Float>> speakerEmbeddings;
    private String user = "";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private int id = 0;
        private int seek = 0;
        private float start = 0;
        private float end = 0;
        private String text = "";
        private List<Integer> tokens;
        private float temperature = 0;
        @JsonProperty("avg_logprob")
        private float avgLogprob = 0;
        @JsonProperty("compression_ratio")
        private float compressionRatio = 0;
        @JsonProperty("no_speech_prob")
        private float noSpeechProb = 0;
        @JsonProperty("channel_id")
        private int channelId = 0;
        @JsonProperty("speaker_id")
        private int speakerId = 0;
        private float confidence = 0.0f;
        private List<Float> embedding;
    }
}
