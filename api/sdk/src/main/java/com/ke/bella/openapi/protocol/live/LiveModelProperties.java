package com.ke.bella.openapi.protocol.live;

import com.ke.bella.openapi.protocol.IModelProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LiveModelProperties implements IModelProperties {
    private String defaultInputAudioFormat;
    private String defaultOutputAudioFormat;
    private Integer defaultSampleRate;
    private Integer maxSessionSeconds;
    private Integer maxMessageBytes;
    private Integer startTimeoutSeconds;

    @Override
    public Map<String, String> description() {
        Map<String, String> description = new LinkedHashMap<>();
        description.put("defaultInputAudioFormat", "默认输入音频格式");
        description.put("defaultOutputAudioFormat", "默认输出音频格式");
        description.put("defaultSampleRate", "默认采样率");
        description.put("maxSessionSeconds", "最大会话时长（秒）");
        description.put("maxMessageBytes", "最大消息大小（字节）");
        description.put("startTimeoutSeconds", "首帧超时（秒）");
        return description;
    }
}
