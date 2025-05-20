package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableSortedMap;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

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
    Integer budgetTokens;
    Integer defaultMaxTokens;

    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("auth", "鉴权配置");
        map.put("region", "部署区域");
        map.put("deployName", "部署名称");
        map.put("supportThink", "是否支持思考过程");
        map.put("additionalParams", "请求需要的额外参数");
        map.put("budgetTokens", "思考过程的最大token");
        map.put("defaultMaxTokens", "max_tokens的最大值");
        return map;
    }
}
