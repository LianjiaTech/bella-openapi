package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AwsMessageProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String region;
    String deployName;
    String anthropicVersion = "bedrock-2023-05-31";
    Integer defaultMaxToken;
    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("auth", "鉴权配置");
        map.put("region", "部署区域");
        map.put("deployName", "部署名称");
        map.put("defaultMaxToken", "默认最大输出token");
        map.put("anthropicVersion", "默认anthropic版本");
        return map;
    }
}
