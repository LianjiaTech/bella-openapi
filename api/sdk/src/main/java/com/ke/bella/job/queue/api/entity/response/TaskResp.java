package com.ke.bella.job.queue.api.entity.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class TaskResp {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskPutResp {

        private String taskId;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskGetResp {

        private List<TaskGetData> data;

        @JsonIgnore
        public boolean isEmpty() {
            return data == null || data.isEmpty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskGetData {
        @JsonProperty("task_id")
        private String taskId;
        private String endpoint;
        private String model;
        private String ak;
        @JsonProperty("input_data")
        private Object inputData;
        @JsonProperty("input_file_id")
        private String inputFileId;
        private String status;
        @JsonProperty("response_mode")
        private String responseMode = "callback";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskGetDetailResp {

        private DetailData data;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailData {
        private String endpoint;
        private String model;
        @JsonProperty("input_data")
        private Object inputData;
        private String status;
        private String ak;
        @JsonProperty("input_file_id")
        private String inputFileId;
        @JsonProperty("output_file_id")
        private String outputFileId;
        @JsonProperty("output_data")
        private Object outputData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskUpdateStatusResp {
        @JsonProperty("task_id")
        private String taskId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDetailPendingResp {
        private List<TaskDetailPendingData> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDetailPendingData {
        private String endpoint;
        private String model;
        @JsonProperty("pending_count")
        private Long pendingCount;
    }

}
