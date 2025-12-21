package com.ke.bella.openapi.protocol.video;

import lombok.Data;

@Data
public class HuoshanCallbackRequest {

    private String task_id;

    private String status;

    private Integer progress;

    private String video_url;

    private Long completed_at;

    private Error error;

    @Data
    public static class Error {
        private String code;
        private String message;
    }
}
