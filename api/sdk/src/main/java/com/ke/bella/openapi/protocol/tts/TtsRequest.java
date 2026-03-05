package com.ke.bella.openapi.protocol.tts;

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

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TtsRequest implements UserRequest, Serializable, IMemoryClearable {
    String user;
    String model;
    String input;
    String voice;
    @JsonProperty("response_format")
    String responseFormat;
    Double speed;
    @JsonProperty("sample_rate")
    Integer sampleRate;
    boolean stream = true;
    Map<String, Object> speakers;

    @JsonIgnore
    private Map<String, Object> extra_body;

    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return extra_body != null && !extra_body.isEmpty() ? extra_body : null;
    }

    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if(extra_body == null) {
            extra_body = new HashMap<>();
        }
        extra_body.put(key, value);
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 输入文本可能很长
            this.input = null;
            this.extra_body = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
