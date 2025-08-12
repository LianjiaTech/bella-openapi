package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * OpenAI Responses API property configuration
 * 支持使用 store=false 和 previous_response_id 为空的 responses api 模拟 chat completion 功能
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ResponsesApiProperty extends CompletionProperty {

    AuthorizationProperty auth;
    String deployName;
    String apiVersion;


    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("auth", "鉴权配置");
        map.put("deployName", "部署名称");
        map.put("apiVersion", "API版本(url中需要拼接时填写)");
        return map;
    }
}
