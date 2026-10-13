package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VertexProperty implements VideoProperty {

    private AuthorizationProperty auth;
    private Integer rpm;
    private String deployName;

    @Override
    public Integer getRpm() {
        return rpm;
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置 (required)");
        map.put("rpm", "RPM限制 (optional)");
        map.put("deployName", "Gemini Omni Flash模型名称 (required)");
        return map;
    }
}
