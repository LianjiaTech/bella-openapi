package com.ke.bella.openapi.protocol.video;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuoshanVideoRequest {

    private String model;

    private List<Content> content;

    private String callback_url;

    private Boolean return_last_frame;

    private Boolean generate_audio;

    private String ratio;

    private Integer duration;

    private Boolean watermark;

    private String service_tier;

    private Integer execution_expires_after;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        private String type;
        private String text;
        private ImageUrl image_url;
        private VideoUrl video_url;
        private AudioUrl audio_url;
        private String role;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageUrl {
        private String url;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VideoUrl {
        private String url;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioUrl {
        private String url;
    }
}
