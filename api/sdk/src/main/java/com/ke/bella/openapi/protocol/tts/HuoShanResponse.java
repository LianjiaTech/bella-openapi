package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuoShanResponse implements Serializable {
    @JsonProperty("reqid")
    private String reqId;
    private int code;
    private String message;
    private int sequence;
    private String data;
    private Addition addition;
    private String duration;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Addition {
        private String duration;
    }
}
