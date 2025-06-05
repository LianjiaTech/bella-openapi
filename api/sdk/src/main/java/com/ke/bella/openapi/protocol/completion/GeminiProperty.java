package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String deployName;
    String apiVersion;
    boolean supportStreamOptions;
    boolean supportThinking;
    String reasoningEffort = "medium"; // low, medium, high, none
    String location = "us-central1"; // Vertex AI的地理位置，默认为us-central1

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("auth", "鉴权配置");
        map.put("deployName", "部署名称");
        map.put("apiVersion", "API版本(url中需要拼接时填写)");
        map.put("supportStreamOptions", "是否支持StreamOptions参数");
        map.put("supportThinking", "是否支持思考模式");
        map.put("reasoningEffort", "推理努力程度(low/medium/high/none)");
        map.put("location", "Vertex AI地理位置(默认us-central1)");
        return map;
    }
}
