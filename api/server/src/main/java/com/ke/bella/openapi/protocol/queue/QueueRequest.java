package com.ke.bella.openapi.protocol.queue;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class QueueRequest {
    @Data
    @Builder
    public static class QueueTaskPutReq {

        private String endpoint;

        private String model;

        private Object data;

        private Integer timeout;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueTaskGetResultReq {

        @JsonProperty("task_id")
        private String taskId;

    }
}
