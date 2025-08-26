package com.ke.bella.queue.remote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.queue.WorkerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class QueueOps {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Register {
        private String queue;
        private String endpoint;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Put {
        private String endpoint;
        private String queue;
        @Builder.Default
        private Integer level = 0;
        private Map<String, Object> data;
        @Builder.Default
        private String responseMode = "callback";
        private String callbackUrl;
        private int timeout;

        @JsonIgnore
        public String getFullQueueName() {
            return String.format("%s:%d", queue, level);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Take {
        private String endpoint;
        private List<String> queues;
        private Integer size;
        @Builder.Default
        private String strategy = "fifo";

        public static Take of(WorkerConfig config) {
            return Take.builder()
                    .endpoint(config.getEndpoint())
                    .queues(config.getQueues())
                    .size(config.getTakeSize())
                    .strategy(config.getTakeStrategy())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {
        @JsonProperty("task_id")
        private String taskId;
        private String ak;
        private String endpoint;
        private String queue;
        private Integer level;
        private Map<String, Object> data;
        private String status;
        private Object result;
        @JsonProperty("instance_id")
        private String instanceId;
        @JsonProperty("start_time")
        private long startTime;
        @JsonProperty("running_time")
        private long runningTime;
        @JsonProperty("completed_time")
        private long completedTime;
        @JsonProperty("callback_url")
        private String callbackUrl;
        @JsonProperty("response_mode")
        private String responseMode;

        @JsonIgnore
        public String getFullQueueName() {
            return String.format("%s:%d", queue, level);
        }

        @JsonIgnore
        public boolean isFinish() {
            return "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
        }
    }

}
