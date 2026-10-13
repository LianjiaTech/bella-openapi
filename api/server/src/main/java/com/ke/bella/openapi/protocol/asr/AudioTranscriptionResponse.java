package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AudioTranscriptionResponse {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioTranscriptionResp {

        @JsonProperty("task_id")
        private String taskId;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioTranscriptionResultResp {

        private List<Object> data;

    }
}
