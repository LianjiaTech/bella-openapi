package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuoshanVideoQueryResponse {

    private String id;

    private String status;

    private Integer progress;

    private Content content;

    private Usage usage;

    private Integer code;

    private String message;

    private String resolution;

    private String ratio;

    private Integer duration;

    private Integer framespersecond;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        private String video_url;
        private String last_frame_url;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        private Integer completion_tokens;
        private Integer total_tokens;
    }
}
