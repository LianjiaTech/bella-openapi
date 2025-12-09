package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VideoJob extends OpenapiResponse {

    private String id;

    private String object;

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
}
