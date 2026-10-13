package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MiniMaxVideoResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("task_id")
    private String taskId;

    private Task task;

    private String type;

    private ErrorDetail error;

    @JsonProperty("request_id")
    private String requestId;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        private String type;

        private String message;

        @JsonProperty("http_code")
        private String httpCode;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Task implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;

        private String model;

        private String status;

        private ErrorInfo error;

        @JsonProperty("created_at")
        private Integer createdAt;

        @JsonProperty("updated_at")
        private Integer updatedAt;

        private Content content;

        private String resolution;

        private Integer duration;

        private Usage usage;

        private String ratio;

        @JsonProperty("task_type")
        private String taskType;

        private String modality;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String code;

        private String message;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content implements Serializable {
        private static final long serialVersionUID = 1L;

        private String url;

        private String prompt;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("total_seconds")
        private Double totalSeconds;

        @JsonProperty("input_seconds")
        private Double inputSeconds;

        @JsonProperty("output_seconds")
        private Double outputSeconds;

        @JsonProperty("input_image_count")
        private Integer imageCount;

        @JsonProperty("input_audio_seconds")
        private Double inputAudioSeconds;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;
    }
}
