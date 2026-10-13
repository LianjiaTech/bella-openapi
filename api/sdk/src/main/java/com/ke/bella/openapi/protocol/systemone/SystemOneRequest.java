package com.ke.bella.openapi.protocol.systemone;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SystemOneRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    private String state;
    private String model;
    private Object questions;

    @JsonIgnore
    private Map<String, Object> extraBody = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return extraBody.isEmpty() ? null : extraBody;
    }

    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        extraBody.put(key, value);
    }

    @Override
    public String getUser() {
        return null;
    }

    @Override
    public void clearLargeData() {
        state = null;
        questions = null;
    }

    @Override
    @JsonIgnore
    public boolean isCleared() {
        return state == null && questions == null;
    }
}
