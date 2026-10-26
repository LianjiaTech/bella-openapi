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

    @JsonProperty("text_output_tokens")
    private Integer textOutputTokens;

    @JsonProperty("video_output_tokens")
    private Integer videoOutputTokens;

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    private Double duration;

    @JsonProperty("total_seconds")
    private Double totalSeconds;

    private String size;

    private Integer fps;

    @JsonProperty("SR")
    private String sr;

    private String ratio;

    private Boolean audio;

    @JsonProperty("video_count")
    private Integer videoCount;

    @JsonProperty("input_seconds")
    private Double inputSeconds;

    @JsonProperty("output_seconds")
    private Double outputSeconds;

    @JsonProperty("input_image_count")
    private Integer imageCount;

    @JsonProperty("input_audio_seconds")
    private Double inputAudioSeconds;
}
