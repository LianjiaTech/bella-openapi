package com.ke.bella.openapi.protocol.rerank;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class RerankResponse extends OpenapiResponse {
    @JsonProperty("request_id")
    private String requestId;
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<RerankResult> results;
    private Usage usage;
    private String code;
    private String message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankResult {
        private Integer index;
        private Object document;
        @JsonProperty("relevance_score")
        private Double relevanceScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
