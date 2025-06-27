package com.ke.bella.job.queue.api.entity.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class TaskParam {

    @Data
    public static class TaskUpdateParam {

        @NotEmpty
        @JsonProperty("task_id")
        private String taskId;

        @NotEmpty
        private String status;

        @JsonProperty("output_data")
        private Object outputData;

        private Integer code = 200;

    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskPutParam {

        @NotEmpty
        private String endpoint;

        @NotEmpty
        private String model;

        private Object data;

        @JsonProperty("response_mode")
        private String responseMode = "callback";

        @JsonProperty("callback_url")
        private String callbackUrl;

        private Integer timeout = 5;

    }

    @Data
    public static class TaskGetParam {

        @NotEmpty
        private String endpoint;

        @NotEmpty
        private List<String> models;

        @Min(1)
        @Max(100)
        private Integer size = 1;

        private Integer level = 0;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskGetDetailParam {

        @NotEmpty
        @JsonProperty("task_id")
        private String taskId;

    }

    @Data
    public static class TaskDetailPendingParam {

        @NotEmpty
        private String endpoint;

        private String model;

        private Integer level;
    }
}
