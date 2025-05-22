package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamCompletionResponse implements Serializable {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private OpenapiResponse.OpenapiError error;
    private Object sensitives;
    private Object requestRiskData;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    
    private CompletionResponse standardFormat;

    /**
     * Helper method to extract content from the first choice
     */
    public String content() {
        if (CollectionUtils.isEmpty(choices) || choices.get(0).getDelta() == null) {
            return null;
        }
        return choices.get(0).getDelta().getContent();
    }

    /**
     * Helper method to extract reasoning from the first choice
     */
    public String reasoning() {
        if (CollectionUtils.isEmpty(choices) || choices.get(0).getDelta() == null) {
            return null;
        }
        return choices.get(0).getDelta().getReasoning();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice implements Serializable {
        private Integer index;
        private Delta delta;
        private String finish_reason;

        public String content() {
            return delta != null && StringUtils.isNotEmpty(delta.getContent()) ? delta.getContent() : null;
        }

        public String reasoning() {
            return delta != null && StringUtils.isNotEmpty(delta.getReasoning()) ? delta.getReasoning() : null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta implements Serializable {
        private String role;
        private String content;
        private String reasoning;
        private ToolCalls tool_calls;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ToolCalls implements Serializable {
            private String id;
            private String type;
            private Function function;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Function implements Serializable {
                private String name;
                private String arguments;
            }
        }
    }
}
