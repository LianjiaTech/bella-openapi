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

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuoShanV3Request implements IMemoryClearable, ITransfer {
    private User user;
    @JsonProperty("req_params")
    private ReqParams reqParams;

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
        @JsonProperty("audio_params")
        private AudioParams audioParams;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            this.additionalProperties.put(key, value);
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
        @JsonProperty("speech_rate")
        private Integer speechRate;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            this.additionalProperties.put(key, value);
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

        // Support extra_body["audio_params"] pass-through
        if (ttsRequest.getExtra_body() != null && ttsRequest.getExtra_body().containsKey("audio_params")) {
            Object audioObj = ttsRequest.getExtra_body().get("audio_params");
            if (audioObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> audioMap = (Map<String, Object>) audioObj;
                for (Map.Entry<String, Object> entry : audioMap.entrySet()) {
                    audioParams.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        ReqParams reqParams = ReqParams.builder()
                .text(ttsRequest.getInput())
                .speaker(ttsRequest.getVoice() != null ? ttsRequest.getVoice() : property.getDefaultVoice())
                .audioParams(audioParams)
                .build();

        // Support extra_body["req_params"] pass-through
        if (ttsRequest.getExtra_body() != null && ttsRequest.getExtra_body().containsKey("req_params")) {
            Object reqObj = ttsRequest.getExtra_body().get("req_params");
            if (reqObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> reqMap = (Map<String, Object>) reqObj;
                for (Map.Entry<String, Object> entry : reqMap.entrySet()) {
                    reqParams.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        return HuoShanV3Request.builder()
                .user(user)
                .reqParams(reqParams)
                .build();
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
