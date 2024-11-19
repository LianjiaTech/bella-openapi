package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableSortedMap;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AwsProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String region;
    String deployName;

    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of("auth", "鉴权配置", "region", "部署区域", "deployName", "部署名称");
    }
}
