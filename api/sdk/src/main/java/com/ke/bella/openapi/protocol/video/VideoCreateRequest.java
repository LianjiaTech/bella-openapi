package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class VideoCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String prompt;

    @Nullable
    private String input_reference;

    private String model;

    @Nullable
    private String seconds;

    @Nullable
    private String size;

    @JsonIgnore
    private Map<String, Object> extra_body;

    @JsonIgnore
    private Object realExtraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        Map<String, Object> result = new HashMap<>();

        if(extra_body != null) {
            result.putAll(extra_body);
        }

        if(realExtraBody != null) {
            result.put("extra_body", realExtraBody);
        }

        return result.isEmpty() ? null : result;
    }

    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if(extra_body == null) {
            extra_body = new HashMap<>();
        }
        extra_body.put(key, value);
    }
}
