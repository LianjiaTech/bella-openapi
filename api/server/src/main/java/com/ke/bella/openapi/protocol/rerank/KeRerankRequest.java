package com.ke.bella.openapi.protocol.rerank;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class KeRerankRequest {
    private String query;
    private List<String> documents;
    private String introduction;
    @JsonProperty("top_n")
    private Integer topN;
}
