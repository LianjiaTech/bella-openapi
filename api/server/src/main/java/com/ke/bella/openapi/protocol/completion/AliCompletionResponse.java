package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Author: Stan Sai
 * Date: 2024/4/23 10:58
 * description:
 */
@Data
public class AliCompletionResponse {
    private Integer httpCode = 200;
    private String requestId;
    private AliCompletionOutput output;
    private AliUsage usage;
    private String code;
    private String message;

    @Data
    public static class AliCompletionOutput {
        // 入参result_format=text时候的返回值
        private String text;
        // 入参result_format=text时候的返回值
        @JsonProperty("finish_reason")
        private String finishReason;
        // 入参result_format=message时候的返回值
        private List<CompletionResponse.Choice> choices;
    }

    @Data
    public static class AliUsage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outTokens;
        @JsonProperty("image_tokens")
        private int imageTokens;
        @JsonProperty("audio_tokens")
        private int audioTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

}
