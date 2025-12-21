package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VideoJob extends OpenapiResponse {

    @JsonIgnore
    public static final String ENDPOINT = "/v1/videos";

    private String id;

    @Builder.Default
    private String object = "video";

    private String model;

    private String status;

    private Integer progress;

    @JsonProperty("created_at")
    private Integer created_at;

    @JsonProperty("completed_at")
    private Integer completed_at;

    @JsonProperty("expires_at")
    private Integer expires_at;

    private String prompt;

    private String seconds;

    private String size;

    @JsonProperty("remixed_from_video_id")
    private String remixed_from_video_id;

    public enum Status {
        queued, submitting, processing, completed, failed, cancelled, deleted
    }
}
