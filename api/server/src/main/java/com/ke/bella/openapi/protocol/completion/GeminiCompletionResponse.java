package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Gemini API响应数据结构
 */
@Data
public class GeminiCompletionResponse {

    // Gemini UsageMetadata字段枚举
    public enum UsageField {
        PROMPT_TOKEN_COUNT("promptTokenCount"),
        CANDIDATES_TOKEN_COUNT("candidatesTokenCount"),
        TOTAL_TOKEN_COUNT("totalTokenCount"),
        THOUGHTS_TOKEN_COUNT("thoughtsTokenCount"),
        CACHED_CONTENT_TOKEN_COUNT("cachedContentTokenCount"),
        TOOL_USE_PROMPT_TOKEN_COUNT("toolUsePromptTokenCount");

        private final String fieldName;

        UsageField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    @Data
    public static class GeminiUsage {
        @JsonProperty("promptTokenCount")
        private Integer promptTokenCount;

        @JsonProperty("candidatesTokenCount")
        private Integer candidatesTokenCount;

        @JsonProperty("totalTokenCount")
        private Integer totalTokenCount;

        @JsonProperty("thoughtsTokenCount")
        private Integer thoughtsTokenCount;

        @JsonProperty("cachedContentTokenCount")
        private Integer cachedContentTokenCount;

        @JsonProperty("toolUsePromptTokenCount")
        private Integer toolUsePromptTokenCount;
    }
}
