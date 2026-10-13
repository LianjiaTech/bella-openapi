package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuoShanV3Request implements IMemoryClearable, ITransfer {
    private static final String REQ_PARAMS = "req_params";
    private static final String AUDIO_PARAMS = "audio_params";
    private static final String SPEECH_RATE = "speech_rate";
    private static final String EXTRA_BODY = "extra_body";
    private static final Set<String> ROOT_RESERVED_FIELDS = new HashSet<>(Arrays.asList("user", REQ_PARAMS, AUDIO_PARAMS, EXTRA_BODY));
    private static final Set<String> REQ_PARAMS_RESERVED_FIELDS = new HashSet<>(Arrays.asList("text", "speaker", AUDIO_PARAMS));
    private static final String FORMAT = "format";
    private static final String SAMPLE_RATE = "sample_rate";
    private static final Set<String> AUDIO_PARAMS_RESERVED_FIELDS = new HashSet<>(Arrays.asList(FORMAT, SAMPLE_RATE, SPEECH_RATE));

    private User user;
    @JsonProperty(REQ_PARAMS)
    private ReqParams reqParams;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        if (additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        getAdditionalProperties().put(key, value);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String uid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReqParams {
        private String text;
        private String speaker;
        @JsonProperty(AUDIO_PARAMS)
        private AudioParams audioParams;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            if (additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            getAdditionalProperties().put(key, value);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioParams {
        private String format;
        @JsonProperty("sample_rate")
        private Integer sampleRate;
        @JsonProperty(SPEECH_RATE)
        private Integer speechRate;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            if (additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            getAdditionalProperties().put(key, value);
        }
    }

    public static HuoShanV3Request from(TtsRequest ttsRequest, HuoShanV3Property property) {
        User user = User.builder()
                .uid(ttsRequest.getUser() != null ? ttsRequest.getUser() : "")
                .build();

        AudioParams audioParams = AudioParams.builder()
                .format(ttsRequest.getResponseFormat() != null ? ttsRequest.getResponseFormat()
                        : (property.getDefaultContentType() != null ? property.getDefaultContentType() : "mp3"))
                .sampleRate(ttsRequest.getSampleRate() != null ? ttsRequest.getSampleRate()
                        : (property.getDefaultSampleRate() != null ? property.getDefaultSampleRate() : 24000))
                .build();

        Double speed = ttsRequest.getSpeed();
        if (speed != null && Double.compare(speed, 1.0) != 0) {
            int speechRate = (int) Math.round((speed - 1.0) * 100);
            speechRate = Math.max(-50, Math.min(100, speechRate));
            audioParams.setSpeechRate(speechRate);
        }

        Map<String, Object> extraBody = normalizeExtraBody(ttsRequest.getExtra_body());

        ReqParams reqParams = ReqParams.builder()
                .text(ttsRequest.getInput())
                .speaker(ttsRequest.getVoice() != null ? ttsRequest.getVoice() : property.getDefaultVoice())
                .audioParams(audioParams)
                .build();

        // Support extra_body["req_params"] pass-through
        if (extraBody != null && extraBody.containsKey(REQ_PARAMS)) {
            Object reqObj = extraBody.get(REQ_PARAMS);
            if (reqObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> reqMap = (Map<String, Object>) reqObj;
                for (Map.Entry<String, Object> entry : reqMap.entrySet()) {
                    if (AUDIO_PARAMS.equals(entry.getKey()) && entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> audioMap = (Map<String, Object>) entry.getValue();
                        applyAudioParams(audioParams, audioMap);
                    } else if (!REQ_PARAMS_RESERVED_FIELDS.contains(entry.getKey())) {
                        reqParams.setAdditionalProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        // Support extra_body["audio_params"] pass-through. This shorthand wins over nested req_params.audio_params.
        if (extraBody != null && extraBody.containsKey(AUDIO_PARAMS)) {
            Object audioObj = extraBody.get(AUDIO_PARAMS);
            if (audioObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> audioMap = (Map<String, Object>) audioObj;
                applyAudioParams(audioParams, audioMap);
            }
        }

        HuoShanV3Request request = HuoShanV3Request.builder()
                .user(user)
                .reqParams(reqParams)
                .build();

        if (extraBody != null) {
            for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
                if (!ROOT_RESERVED_FIELDS.contains(entry.getKey())) {
                    request.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        return request;
    }

    private static Map<String, Object> normalizeExtraBody(Map<String, Object> extraBody) {
        if (extraBody == null) {
            return null;
        }

        Map<String, Object> normalized = new HashMap<>();
        Object wrappedExtraBody = extraBody.get(EXTRA_BODY);
        if (wrappedExtraBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wrappedMap = (Map<String, Object>) wrappedExtraBody;
            normalized.putAll(wrappedMap);
        }

        for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
            if (!EXTRA_BODY.equals(entry.getKey())) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }

        return normalized;
    }

    private static void applyAudioParams(AudioParams audioParams, Map<String, Object> audioMap) {
        for (Map.Entry<String, Object> entry : audioMap.entrySet()) {
            if (FORMAT.equals(entry.getKey()) && entry.getValue() instanceof String) {
                audioParams.setFormat((String) entry.getValue());
            } else if (SAMPLE_RATE.equals(entry.getKey()) && entry.getValue() instanceof Number) {
                audioParams.setSampleRate(((Number) entry.getValue()).intValue());
            } else if (SPEECH_RATE.equals(entry.getKey()) && entry.getValue() instanceof Number) {
                audioParams.setSpeechRate(((Number) entry.getValue()).intValue());
            } else if (!AUDIO_PARAMS_RESERVED_FIELDS.contains(entry.getKey())) {
                audioParams.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            if (this.reqParams != null) {
                this.reqParams.text = null;
            }
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
