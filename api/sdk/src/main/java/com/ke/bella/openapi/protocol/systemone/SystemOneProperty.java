package com.ke.bella.openapi.protocol.systemone;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SystemOneProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    private String deployName;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("deployName", "供应商侧部署/模型名称");
        return map;
    }
}
