package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VertexResponse {
    private String id;
    private String model;
    private String status;
    private String object;
    private Long created;
    private Long updated;
    private String code;
    private String message;
    private Object metadata;
    @JsonProperty("system_instruction")
    private Object systemInstruction;
    private Object tools;
    @JsonProperty("tool_config")
    private Object toolConfig;
    @JsonProperty("safety_settings")
    private Object safetySettings;
    private ErrorDetail error;
    @JsonProperty("last_error")
    private ErrorDetail lastError;
    @JsonProperty("incomplete_details")
    private ErrorDetail incompleteDetails;
    private List<Step> steps;
    private Usage usage;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Step {
        private String id;
        private String type;
        private String status;
        private String role;
        private String name;
        private List<Content> content;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        private String type;
        private String text;
        private String uri;
        private String data;
        @JsonProperty("mime_type")
        private String mimeType;
        private Object annotations;
        @JsonProperty("inline_data")
        private Object inlineData;
        @JsonProperty("file_data")
        private Object fileData;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        @JsonProperty("total_input_tokens")
        private Integer totalInputTokens;
        @JsonProperty("total_output_tokens")
        private Integer totalOutputTokens;
        @JsonProperty("total_cached_tokens")
        private Integer totalCachedTokens;
        @JsonProperty("total_thought_tokens")
        private Integer totalThoughtTokens;
        @JsonProperty("total_tool_use_tokens")
        private Integer totalToolUseTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        @JsonProperty("input_tokens_by_modality")
        private List<ModalityTokenCount> inputTokensByModality;
        @JsonProperty("output_tokens_by_modality")
        private List<ModalityTokenCount> outputTokensByModality;
        @JsonProperty("cached_tokens_by_modality")
        private List<ModalityTokenCount> cachedTokensByModality;
        @JsonProperty("tool_use_tokens_by_modality")
        private List<ModalityTokenCount> toolUseTokensByModality;
        @JsonProperty("grounding_tool_count")
        private Integer groundingToolCount;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModalityTokenCount {
        private String modality;
        @JsonProperty("tokens")
        private Integer tokenCount;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code;
        private String message;
        private String status;
        private String type;
        private String param;
        private String reason;
    }
}
