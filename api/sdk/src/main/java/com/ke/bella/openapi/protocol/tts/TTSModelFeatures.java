package com.ke.bella.openapi.protocol.tts;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

@Data
public class TTSModelFeatures implements IModelFeatures {

    private boolean stream;
    private boolean customize_sound_color;

    @Override
    public Map<String, String> description() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put("stream", "是否支持流式输出");
        desc.put("customize_sound_color", "是否支持自定义音色");
        return desc;
    }
}
