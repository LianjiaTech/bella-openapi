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
    private Map<String, String> headers = new LinkedHashMap<>();

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("deployName", "供应商侧部署/模型名称");
        map.put("headers", "额外请求头");
        return map;
    }
}
