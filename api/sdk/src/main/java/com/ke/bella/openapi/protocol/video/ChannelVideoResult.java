package com.ke.bella.openapi.protocol.video;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelVideoResult {

    private String channelVideoId;

    private String status;

    private String fileId;

    private String size;

    private Double actualSeconds;

    private VideoUsage usage;

    private ErrorInfo error;

    @Data
    @Builder
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}
