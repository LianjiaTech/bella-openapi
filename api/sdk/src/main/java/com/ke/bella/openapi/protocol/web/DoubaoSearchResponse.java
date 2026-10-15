package com.ke.bella.openapi.protocol.web;

import com.fasterxml.jackson.annotation.*;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class DoubaoSearchResponse extends OpenapiResponse {
    @JsonProperty("ResponseMetadata") private Map<String, Object> responseMetadata;
    @JsonProperty("Result") private Map<String, Object> result;
    @JsonIgnore private Map<String, Object> extraFields = new LinkedHashMap<>();
    @JsonAnySetter public void setExtraField(String key, Object value) { extraFields.put(key, value); }
    @JsonAnyGetter public Map<String, Object> getExtraFields() { return extraFields; }
}
