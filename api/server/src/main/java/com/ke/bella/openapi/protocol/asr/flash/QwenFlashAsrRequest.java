package com.ke.bella.openapi.protocol.asr.flash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QwenFlashAsrRequest {
    private String model;
    private Input input;
    private Parameters parameters;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Input {
        private List<Message> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private List<Content> content;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Content {
        private String text;
        private String audio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Parameters {
        @JsonProperty("asr_options")
        private AsrOptions asrOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AsrOptions {
        @JsonProperty("enable_lid")
        private Boolean enableLid;
        @JsonProperty("enable_itn")
        private Boolean enableItn;
    }
}
