package com.ke.bella.openapi.protocol.tts.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeTtsPayload {
    private String model;
    private String voice;
    private String format;
    @JsonProperty("sample_rate")
    private Integer sampleRate;
    private Integer channels;
    private String encoding;
    private Double speed;
    private Double volume;
    private Double pitch;
    private String language;
    @JsonProperty("text_type")
    private String textType;
    @JsonProperty("enable_timestamp")
    private Boolean enableTimestamp;
    @JsonProperty("extra_body")
    private Map<String, Object> extraBody;
    private String text;
    private String reason;
    private RealtimeTtsCapability capabilities;
    @JsonProperty("accepted_characters")
    private Integer acceptedCharacters;
    @JsonProperty("audio_id")
    private String audioId;
    @JsonProperty("byte_length")
    private Integer byteLength;
    @JsonProperty("duration_ms")
    private Long durationMs;
    @JsonProperty("is_final")
    private Boolean isFinal;
    private String type;
    private List<TimestampItem> items;
    @JsonProperty("provider_metadata")
    private Map<String, Object> providerMetadata;
    @JsonProperty("audio_duration_ms")
    private Long audioDurationMs;
    private Map<String, Object> usage;
    private Map<String, Object> metrics;
    private Error error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimestampItem {
        private String text;
        @JsonProperty("start_ms")
        private Long startMs;
        @JsonProperty("end_ms")
        private Long endMs;
        @JsonProperty("begin_index")
        private Integer beginIndex;
        @JsonProperty("end_index")
        private Integer endIndex;
        private String phoneme;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {
        private String code;
        private String message;
        @JsonProperty("provider_code")
        private String providerCode;
        @JsonProperty("provider_message")
        private String providerMessage;
    }
}
