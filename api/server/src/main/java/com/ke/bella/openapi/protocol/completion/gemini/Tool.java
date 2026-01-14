package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.completion.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tool {
    private List<FunctionDeclaration> functionDeclarations;
    private Map<String, Object> codeExecution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionDeclaration {
        private String name;
        private String description;
        private Message.Function.FunctionParameter parameters;
    }
}
