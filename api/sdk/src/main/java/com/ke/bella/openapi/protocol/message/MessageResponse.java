package com.ke.bella.openapi.protocol.message;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MessageResponse extends OpenapiResponse {
    private String id;
    private String type; // "message"
    private String role; // "assistant"
    private List<ContentBlock> content;
    private String model;
    @JsonProperty("stop_reason")
    private String stopReason;
    @JsonProperty("stop_sequence")
    private String stopSequence; // Nullable
    private Usage usage;
    @JsonProperty("thought_signature")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String thoughtSignature; // Gemini 3 思维签名

    // Base ContentBlock for response
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ResponseTextBlock.class, name = "text"),
            @JsonSubTypes.Type(value = ResponseToolUseBlock.class, name = "tool_use"),
            @JsonSubTypes.Type(value = ResponseServerToolUseBlock.class, name = "server_tool_use"),
            @JsonSubTypes.Type(value = ResponseWebSearchToolResultBlock.class, name = "web_search_tool_result"),
            @JsonSubTypes.Type(value = ResponseCodeExecutionToolResultBlock.class, name = "code_execution_tool_result"),
            @JsonSubTypes.Type(value = ResponseMCPToolUseBlock.class, name = "mcp_tool_use"),
            @JsonSubTypes.Type(value = ResponseMCPToolResultBlock.class, name = "mcp_tool_result"),
            @JsonSubTypes.Type(value = ResponseContainerUploadBlock.class, name = "container_upload"),
            @JsonSubTypes.Type(value = ResponseThinkingBlock.class, name = "thinking"),
            @JsonSubTypes.Type(value = ResponseRedactedThinkingBlock.class, name = "redacted_thinking")
    // Note: The spec also implies a generic "tool_result" which might be an
    // abstract type
    // or handled by specific tool result types. For now, specific ones are
    // listed.
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static abstract class ContentBlock {
        private String type;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseTextBlock extends ContentBlock {
        private String text;

        public ResponseTextBlock(String text) {
            this.text = text;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseToolUseBlock extends ContentBlock {
        private String id; // tool_use_id
        private String name;
        private Map<String, Object> input;

        public ResponseToolUseBlock(String id, String name, Map<String, Object> input) {
            this.id = id;
            this.name = name;
            this.input = input;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseServerToolUseBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private String name;
        private Map<String, Object> input;

        public ResponseServerToolUseBlock(String toolUseId, String name, Map<String, Object> input) {
            this.toolUseId = toolUseId;
            this.name = name;
            this.input = input;
        }
    }

    // Abstract base for tool results if common fields exist, or directly
    // implement specifics
    // For now, let's assume specific tool result blocks

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseWebSearchToolResultBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private List<Map<String, Object>> documents; // Define Document class if
                                                     // structure is
                                                     // complex/known
        @JsonProperty("is_error")
        private Boolean isError;

        public ResponseWebSearchToolResultBlock(String toolUseId, List<Map<String, Object>> documents, Boolean isError) {
            this.toolUseId = toolUseId;
            this.documents = documents;
            this.isError = isError;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseCodeExecutionToolResultBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private String stdout;
        private String stderr;
        @JsonProperty("is_error")
        private Boolean isError;

        public ResponseCodeExecutionToolResultBlock(String toolUseId, String stdout, String stderr, Boolean isError) {
            this.toolUseId = toolUseId;
            this.stdout = stdout;
            this.stderr = stderr;
            this.isError = isError;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMCPToolUseBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private String name;
        private Map<String, Object> input;

        public ResponseMCPToolUseBlock(String toolUseId, String name, Map<String, Object> input) {
            this.toolUseId = toolUseId;
            this.name = name;
            this.input = input;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMCPToolResultBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private String output; // Assuming string output, adjust if complex
        @JsonProperty("is_error")
        private Boolean isError;

        public ResponseMCPToolResultBlock(String toolUseId, String output, Boolean isError) {
            this.toolUseId = toolUseId;
            this.output = output;
            this.isError = isError;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseContainerUploadBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        @JsonProperty("container_name")
        private String containerName;
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("full_path")
        private String fullPath;
        @JsonProperty("object_name")
        private String objectName;
        private String status; // e.g., "success", "failure"
        private String message; // Optional message

        public ResponseContainerUploadBlock(String toolUseId, String containerName, String fileName, String fullPath, String objectName,
                String status, String message) {
            this.toolUseId = toolUseId;
            this.containerName = containerName;
            this.fileName = fileName;
            this.fullPath = fullPath;
            this.objectName = objectName;
            this.status = status;
            this.message = message;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    public static class ResponseThinkingBlock extends ContentBlock {
        private String thinking;
        private String signature;

        public ResponseThinkingBlock(String thinking, String signature) {
            this.thinking = thinking;
            this.signature = signature;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseRedactedThinkingBlock extends ContentBlock {
        private String data;

        public ResponseRedactedThinkingBlock(String data) {
            this.data = data;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
        @JsonProperty("cache_creation")
        private CacheCreation cacheCreation; // Nullable
        @JsonProperty("cache_creation_input_tokens")
        private int cacheCreationInputTokens; // Nullable
        @JsonProperty("cache_read_input_tokens")
        private int cacheReadInputTokens; // Nullable
        @JsonProperty("server_tool_use")
        private ServerToolUsage serverToolUse; // Nullable
        @JsonProperty("service_tier")
        private String serviceTier; // Nullable

        @JsonIgnore
        public int getInputTokens() {
            return inputTokens + cacheCreationInputTokens + cacheReadInputTokens;
        }

        @JsonProperty("input_tokens")
        public int inputTokens() {
            return inputTokens;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CacheCreation {
        @JsonProperty("ephemeral_1h_input_tokens")
        private int ephemeral1hInputTokens; // Using Integer for potential
                                            // nullability
        @JsonProperty("ephemeral_5m_input_tokens")
        private int ephemeral5mInputTokens; // Using Integer for potential
                                            // nullability
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerToolUsage {
        @JsonProperty("web_search_requests")
        private Integer webSearchRequests; // Using Integer for potential
                                           // nullability
        // Add other tool usage counts if specified, e.g.
        // code_execution_requests
    }
}
