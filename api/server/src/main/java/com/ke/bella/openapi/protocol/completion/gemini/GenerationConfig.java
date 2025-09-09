package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class GenerationConfig {
    private Float temperature;
    private Float topP;
    private Integer topK;
    private Integer candidateCount;
    private Integer maxOutputTokens;
    private List<String> stopSequences;
    private Float presencePenalty;
    private Float frequencyPenalty;
    private String responseMimeType;
    private Map<String, Object> responseSchema;
    private Integer seed;
    private Boolean responseLogprobs;
    private Integer logprobs;
    private Boolean audioTimestamp;
    private ThinkingConfig thinkingConfig;

    @Data
    public static class ThinkingConfig {
        private Integer thinkingBudget = -1;
        private boolean includeThoughts = true;
    }
}
