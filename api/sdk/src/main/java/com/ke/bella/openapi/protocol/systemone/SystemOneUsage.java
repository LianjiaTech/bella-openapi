package com.ke.bella.openapi.protocol.systemone;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SystemOneUsage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("input_tokens")
    private Integer inputTokens;
    @JsonProperty("output_tokens")
    private Integer outputTokens;
    @JsonIgnore
    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extra.isEmpty() ? null : extra;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        extra.put(key, value);
    }

    @JsonIgnore
    public Integer getTotalTokens() {
        if(inputTokens == null && outputTokens == null) {
            return null;
        }
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }
}
