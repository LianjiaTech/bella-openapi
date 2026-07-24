package com.ke.bella.openapi.protocol.rerank;

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
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class RerankRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    private String user;
    private String model;
    private String query;
    private List<String> documents;
    @JsonProperty("top_n")
    private Integer topN;
    private String instruct;

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

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            this.query = null;
            this.documents = null;
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
