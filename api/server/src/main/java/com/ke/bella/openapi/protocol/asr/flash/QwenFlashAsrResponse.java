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
public class QwenFlashAsrResponse {
    private Output output;
    private Usage usage;
    @JsonProperty("request_id")
    private String requestId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Output {
        private List<Choice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Choice {
        @JsonProperty("finish_reason")
        private String finishReason;
        private Message message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private List<Annotation> annotations;
        private List<Content> content;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Annotation {
        private String language;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Content {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        @JsonProperty("input_tokens_details")
        private InputTokensDetails inputTokensDetails;
        @JsonProperty("output_tokens_details")
        private OutputTokensDetails outputTokensDetails;
        private Integer seconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InputTokensDetails {
        @JsonProperty("text_tokens")
        private Integer textTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OutputTokensDetails {
        @JsonProperty("text_tokens")
        private Integer textTokens;
    }
}