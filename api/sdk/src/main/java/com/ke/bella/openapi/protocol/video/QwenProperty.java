package com.ke.bella.openapi.protocol.video;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QwenProperty implements VideoProperty {

    private AuthorizationProperty auth;

    private Integer rpm;

    private String deployName;

    private String taskQueryUrl;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置 (required)");
        map.put("rpm", "RPM限制 (optional)");
        map.put("deployName", "部署名称 (required)");
        map.put("taskQueryUrl", "获取任务URL (required)");
        return map;
    }
}
