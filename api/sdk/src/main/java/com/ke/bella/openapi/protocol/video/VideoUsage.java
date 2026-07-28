package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoUsage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    private Double duration;

    private String size;

    private Integer fps;

    @JsonProperty("SR")
    private String sr;

    private Boolean audio;

    @JsonProperty("video_count")
    private Integer videoCount;
}
