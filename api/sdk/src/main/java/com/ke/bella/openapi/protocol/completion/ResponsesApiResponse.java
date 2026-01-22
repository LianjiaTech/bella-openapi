package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API response format
 * 用于接收Responses API响应并转换为Chat Completion格式
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesApiResponse extends OpenapiResponse {

    /**
     * Response ID
     */
    private String id;

    /**
     * Object type (always "response")
     */
    private String object;

    /**
     * Creation timestamp
     */
    private Long created;

    /**
     * Model used
     */
    private String model;

    /**
     * Response status: pending, in_progress, completed, failed, cancelled
     */
    private String status;

    /**
     * Generated text content (for simple responses)
     */
    private String output_text;

    /**
     * Output items array
     */
    private List<OutputItem> output;

    /**
     * Token usage statistics
     */
    private Usage usage;

    /**
     * Reasoning information (for reasoning models)
     */
    private ReasoningInfo reasoning;

    /**
     * Custom metadata
     */
    private Map<String, Object> metadata;

    /**
     * Whether state was stored
     */
    private Boolean store;

    /**
     * Whether processed in background
     */
    private Boolean background;

    /**
     * Bella internal response metadata
     */
    private BellaResponse _bella_response;

    /**
     * Extra body fields for vendor-specific parameters
     */
    @JsonIgnore
    private Map<String, Object> _extra_body;

    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return _extra_body != null && !_extra_body.isEmpty() ? _extra_body : null;
    }

    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if(_extra_body == null) {
            _extra_body = new HashMap<>();
        }
        _extra_body.put(key, value);
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutputItem {
        /**
         * Output item type: message_output, function_call, reasoning, etc.
         */
        private String type;

        /**
         * Item ID
         */
        private String id;

        /**
         * Message role (for message_output type)
         */
        private String role;

        /**
         * Content array (for message_output type)
         */
        private List<ContentItem> content;

        /**
         * Function call status (for function_call type)
         */
        private String status;

        /**
         * Function call ID
         */
        private String call_id;

        /**
         * Function name
         */
        private String name;

        /**
         * Function arguments
         */
        private String arguments;

        /**
         * Reasoning summary (for reasoning type)
         */
        private List<SummaryItem> summary;

        /**
         * Encrypted reasoning content
         */
        private String encrypted_content;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentItem {
        /**
         * Content type: text, output_text, etc.
         */
        private String type;

        /**
         * Text content
         */
        private String text;

        /**
         * Annotations (optional)
         */
        private List<Object> annotations;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SummaryItem {
        /**
         * Summary item type: summary_text
         */
        private String type;

        /**
         * Summary text content
         */
        private String text;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        /**
         * Input tokens (renamed from prompt_tokens)
         */
        private Integer input_tokens;

        /**
         * Output tokens (renamed from completion_tokens)
         */
        private Integer output_tokens;

        /**
         * Total tokens
         */
        private Integer total_tokens;

        /**
         * Cached Tokens
         */
        private InputTokensDetail input_tokens_details;

        /**
         * Reasoning tokens (for reasoning models)
         */
        private OutputTokensDetail output_tokens_details;

        /**
         * Tool usage count map (for tool-based billing)
         * Example: {"web_search": 1}
         */
        private Map<String, Integer> tool_usage;

        /**
         * Detailed tool usage breakdown (for source-specific billing)
         * Example: {"web_search": {"search_engine": 1, "toutiao": 1}}
         */
        private Map<String, Map<String, Integer>> tool_usage_details;

        /**
         * Extra body fields for vendor-specific parameters
         */
        @JsonIgnore
        private Map<String, Object> _extra_body;

        @JsonAnyGetter
        public Map<String, Object> getExtraBodyFields() {
            return _extra_body != null && !_extra_body.isEmpty() ? _extra_body : null;
        }

        @JsonAnySetter
        public void setExtraBodyField(String key, Object value) {
            if(_extra_body == null) {
                _extra_body = new HashMap<>();
            }
            _extra_body.put(key, value);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputTokensDetail {
        private Integer cached_tokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutputTokensDetail {
        private Integer reasoning_tokens;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReasoningInfo {
        /**
         * Reasoning effort level
         */
        private String effort;

        /**
         * Reasoning summary
         */
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BellaResponse {
        /**
         * Channel code that was used to process this request
         */
        private String channel_code;
    }
}
