package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API request format
 * 用于将Chat Completion请求转换为Responses API请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesApiRequest implements IMemoryClearable, ITransfer {

    /**
     * Model to use
     */
    private String model;

    /**
     * Input for the response
     * Can be string or array of input items
     */
    private Object input;

    /**
     * System-level instructions
     */
    private String instructions;

    /**
     * Temperature for response randomness
     */
    private Float temperature;

    /**
     * Maximum tokens to generate
     */
    private Integer max_tokens;

    /**
     * Top-p nucleus sampling parameter
     */
    private Float top_p;

    /**
     * Frequency penalty
     */
    private Float frequency_penalty;

    /**
     * Presence penalty
     */
    private Float presence_penalty;

    /**
     * Whether to stream the response
     */
    private Boolean stream = false;

    /**
     * Whether to store conversation state
     * For chat completion simulation, this is false
     */
    private Boolean store = false;

    /**
     * Background processing mode
     */
    private Boolean background = false;

    /**
     * Previous response ID for conversation continuation
     * For chat completion simulation, this is null
     */
    private String previous_response_id;

    /**
     * Available tools
     */
    private List<ResponsesApiTool> tools;

    /**
     * Tool choice configuration
     */
    private Object tool_choice;

    /**
     * Fields to include in response
     */
    private List<String> include;

    /**
     * Custom metadata
     */
    private Map<String, Object> metadata;

    /**
     * Reasoning configuration for reasoning models
     */
    private ReasoningConfig reasoning;

    /**
     * Use prompt cache key to enable cache
     */
    private String prompt_cache_key;

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReasoningConfig {
        /**
         * Reasoning effort level: minimal, medium, high
         */
        private String effort;

        /**
         * A summary of the reasoning performed by the model. This can be useful for debugging and understanding the model's reasoning process. One of auto, concise, or detailed.
         */
        private String summary;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponsesApiTool {
        /**
         * Tool type (e.g., "function")
         */
        private String type;

        /**
         * Function name
         */
        private String name;

        /**
         * Function description
         */
        private String description;

        /**
         * Function parameters schema
         */
        private Object parameters;

        /**
         * Whether to use strict mode
         */
        private Boolean strict;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputItem {
        /**
         * Input item type
         */
        private String type;

        /**
         * Message role (for message type)
         */
        private String role;

        /**
         * Content (string or array)
         */
        private Object content;

        /**
         * Function call ID (for function_call type)
         */
        private String call_id;

        /**
         * Function name (for function_call type)
         */
        private String name;

        /**
         * Function arguments (for function_call type)
         */
        private String arguments;

        /**
         * Function output (for function_call_output type)
         */
        private String output;

        /**
         * Status (for function calls)
         */
        private String status;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentItem {
        /**
         * Content type (input_text, input_image, etc.)
         */
        private String type;

        /**
         * Text content
         */
        private String text;

        /**
         * Image URL
         */
        private String image_url;

        /**
         * File ID
         */
        private String file_id;

        /**
         * Audio URL
         */
        private String audio_url;

        /**
         * Image detail level
         */
        private String detail;
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            // 清理最大的内存占用 - 输入数据、工具列表、元数据等
            this.input = null;
            this.instructions = null;
            if (this.tools != null) {
                this.tools.clear();
            }
            if (this.metadata != null) {
                this.metadata.clear();
            }
            this.reasoning = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
