package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QwenVideoResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("request_id")
    private String requestId;

    private Output output;

    private Usage usage;

    private String code;

    private String message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Output implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("task_id")
        private String taskId;

        @JsonProperty("task_status")
        private String taskStatus;

        @JsonProperty("submit_time")
        private String submitTime;

        @JsonProperty("scheduled_time")
        private String scheduledTime;

        @JsonProperty("end_time")
        private String endTime;

        @JsonProperty("video_url")
        private String videoUrl;

        @JsonProperty("watermark_video_url")
        private String watermarkVideoUrl;

        @JsonProperty("orig_prompt")
        private String origPrompt;

        private String code;

        private String message;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double duration;

        private String size;

        private Integer fps;

        @JsonProperty("video_count")
        private Integer videoCount;

        private Boolean audio;

        @JsonProperty("SR")
        private String sr;
    }
}
