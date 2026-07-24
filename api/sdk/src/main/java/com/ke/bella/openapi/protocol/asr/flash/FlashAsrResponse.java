package com.ke.bella.openapi.protocol.asr.flash;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FlashAsrResponse extends OpenapiResponse {
    @JsonProperty("task_id")
    private String taskId;
    private String user;
    @JsonProperty("flash_result")
    private FlashResult flashResult;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FlashResult {
        private int duration;
        private List<Sentence> sentences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Sentence {
        private String text;
        @JsonProperty("begin_time")
        private long beginTime;
        @JsonProperty("end_time")
        private long endTime;
    }
}
