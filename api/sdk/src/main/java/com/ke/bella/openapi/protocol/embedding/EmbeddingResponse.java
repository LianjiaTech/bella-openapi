package com.ke.bella.openapi.protocol.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * @author zhangxiaojia002
 * 
 * @date 2023/7/13 10:21 上午
 **/
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
public class EmbeddingResponse extends OpenapiResponse {
    private String object;
    private List<EmbeddingData> data;
    private String model;
    private TokenUsage usage;

    @Data
    public static class EmbeddingData {
        private String object;
        private Object embedding;
        private int index;
    }

    @Data
    public static class TokenUsage {
        private int prompt_tokens;
        private int total_tokens;
    }
}
