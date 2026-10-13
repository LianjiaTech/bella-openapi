package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RealtimeTtsModelFeatures implements IModelFeatures {
    private boolean realtime_tts;
    private boolean connection_reuse;
    private boolean timestamps;
    private boolean clear_text_buffer;

    @Override
    public Map<String, String> description() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put("realtime_tts", "是否支持双向流式语音合成");
        desc.put("connection_reuse", "是否支持连接复用");
        desc.put("timestamps", "是否支持时间戳");
        desc.put("clear_text_buffer", "是否支持清空待合成文本缓存");
        return desc;
    }
}
