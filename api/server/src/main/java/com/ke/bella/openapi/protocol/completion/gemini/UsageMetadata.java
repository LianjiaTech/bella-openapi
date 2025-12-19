package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageMetadata {
    private Integer promptTokenCount;
    private Integer candidatesTokenCount;
    private Integer totalTokenCount;
    private Integer toolUsePromptTokenCount;
    private Integer thoughtsTokenCount;
    private Integer cachedContentTokenCount;
    private List<TokensDetails> promptTokensDetails;
    private List<TokensDetails> cacheTokensDetails;
    private List<TokensDetails> candidatesTokensDetails;
    private List<TokensDetails> toolUsePromptTokensDetails;
    private String trafficType;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokensDetails {
        private String modality;
        private int tokenCount;
    }

    public enum Modality {
        MODALITY_UNSPECIFIED,
        TEXT,
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT
    }
}
