package com.ke.bella.openapi.protocol.systemone;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class SystemOneResponse extends OpenapiResponse {
    private String model;
    private Map<String, Object> answers;
    private SystemOneUsage usage;
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
}
