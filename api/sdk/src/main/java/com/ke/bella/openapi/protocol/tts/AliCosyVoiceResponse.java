package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AliCosyVoiceResponse implements Serializable {
    @JsonProperty("request_id")
    private String requestId;
    private Output output;
    private Object usage;
    private String code;
    private String message;

    public boolean isSuccess() {
        return StringUtils.isBlank(code);
    }

    public String errorMessage() {
        return StringUtils.defaultIfBlank(message, "Ali CosyVoice TTS request failed");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Output implements Serializable {
        private Audio audio;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Audio implements Serializable {
        private String url;
        private String data;
        @JsonProperty("expires_at")
        private Long expiresAt;
    }
}
