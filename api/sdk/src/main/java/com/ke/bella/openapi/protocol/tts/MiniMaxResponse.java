package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MiniMaxResponse implements Serializable {
    private DataPayload data;
    @JsonProperty("base_resp")
    private BaseResp baseResp;
    @JsonProperty("trace_id")
    private String traceId;

    public boolean isSuccess() {
        return baseResp == null || baseResp.getStatusCode() == null || baseResp.getStatusCode() == 0;
    }

    public String errorMessage() {
        if (baseResp == null || baseResp.getStatusMsg() == null) {
            return "MiniMax TTS request failed";
        }
        return baseResp.getStatusMsg();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataPayload implements Serializable {
        private String audio;
        private Integer status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BaseResp implements Serializable {
        @JsonProperty("status_code")
        private Integer statusCode;
        @JsonProperty("status_msg")
        private String statusMsg;
    }
}
