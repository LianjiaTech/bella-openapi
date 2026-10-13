package com.ke.bella.openapi.protocol.live;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LiveSessionProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    private String provider;
    private String model;
    private String apiVersion;
    private String project;
    private String location;
    private LiveModelFeatures features;

    @Override
    public Map<String, String> description() {
        Map<String, String> description = new LinkedHashMap<>();
        description.put("auth", "上游鉴权配置");
        description.put("provider", "供应商标识");
        description.put("model", "供应商模型或部署名称");
        description.put("apiVersion", "供应商 API 版本");
        description.put("project", "Google Cloud project");
        description.put("location", "Google Cloud region");
        description.put("features", "Channel 能力覆盖");
        return description;
    }
}
