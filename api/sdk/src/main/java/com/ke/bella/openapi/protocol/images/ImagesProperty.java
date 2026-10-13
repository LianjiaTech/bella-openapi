package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import com.ke.bella.openapi.safety.SafetyCheckMode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesProperty implements IProtocolProperty {
    AuthorizationProperty auth;
    String deployName;
    SafetyCheckMode safetyCheckMode = SafetyCheckMode.async;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("deployName", "部署名称");
        map.put("safetyCheckMode", "安全检测模式");
        return map;
    }
}
