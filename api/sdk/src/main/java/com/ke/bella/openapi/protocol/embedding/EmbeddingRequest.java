package com.ke.bella.openapi.protocol.embedding;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhangxiaojia002
 *
 * @date 2023/7/13 10:20 上午
 **/
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class EmbeddingRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;
    private String user;
    private String model;
    private Object input;
    /**
     * Optional
     * Defaults to float
     * The format to return the embeddings in. Can be either float or base64.
     */
    @JsonProperty("encoding_format")
    private String encodingFormat;
    /**
     * Optional
     * The number of dimensions the resulting output embeddings should have.
     * Only supported in text-embedding-3 and later models.
     */
    private Integer dimensions;
    /**
     * Provider-specific multimodal embedding fields, used by providers such as
     * Volcengine Ark.
     */
    private Object instructions;
    @JsonProperty("sparse_embedding")
    private Object sparseEmbedding;
    @JsonProperty("multi_embedding")
    private Object multiEmbedding;

    @JsonIgnore
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return extraBody == null || extraBody.isEmpty() ? null : extraBody;
    }

    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if(extraBody == null) {
            extraBody = new HashMap<>();
        }
        extraBody.put(key, value);
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 输入内容可能包含大量文本数据
            this.input = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
