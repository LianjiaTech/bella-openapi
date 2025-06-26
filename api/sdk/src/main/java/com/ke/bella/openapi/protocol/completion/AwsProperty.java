package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AwsProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String region;
    String deployName;
    boolean supportThink;
    boolean supportCache;
    Map<String, Object> additionalParams = new HashMap<>();

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("region", "部署区域");
        map.put("deployName", "部署名称");
        map.put("supportThink", "是否支持思考过程");
        map.put("supportCache", "是否支持缓存");
        map.put("additionalParams", "请求需要的额外参数");;
        return map;
    }
}
