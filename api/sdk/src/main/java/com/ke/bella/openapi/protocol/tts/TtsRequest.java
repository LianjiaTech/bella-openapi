package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;

import java.io.Serializable;

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

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            // 清理最大的内存占用 - 输入文本可能很长
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
