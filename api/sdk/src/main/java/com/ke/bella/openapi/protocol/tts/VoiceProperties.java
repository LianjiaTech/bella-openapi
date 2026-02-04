package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.protocol.IModelProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class VoiceProperties implements IModelProperties {

    private Map<String, String> voiceTypes;
    private String input_state;

    @Override
    public Map<String, String> description() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put("voiceTypes", "声音类型");
        desc.put("input_state", "补充说明");
        return desc;
    }
}
