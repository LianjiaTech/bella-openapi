package com.ke.bella.openapi.protocol.completion;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String deployName;
    String anthropicVersion = "2023-06-01";
    Integer defaultMaxToken;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("auth", "鉴权配置（使用x-api-key）");
        map.put("deployName", "模型名称（如deepseek-chat、claude-3-5-sonnet等）");
        map.put("baseUrl", "API基础URL）");
        map.put("anthropicVersion", "Anthropic API版本（如2023-06-01）");
        map.put("defaultMaxToken", "默认最大token数");
        return map;
    }
}
