package com.ke.bella.openapi.protocol.alpha.search;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AlphaSearchProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    private String deployName;
    private Map<String, String> headers = new LinkedHashMap<>();

    @Override
    public Map<String, String> description() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("auth", "普通 Bearer/API key 或 Alpha Search OAuth JSON");
        result.put("deployName", "供应商侧模型名称（可选）");
        result.put("headers", "显式允许转发的额外请求头");
        return result;
    }
}
