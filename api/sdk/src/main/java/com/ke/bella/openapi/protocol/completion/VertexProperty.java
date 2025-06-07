package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VertexProperty extends CompletionProperty {
    String vertexAICredentials;
    String deployName;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String location = "us-central1";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    boolean supportStreamOptions;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    boolean supportThinking;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String reasoningEffort = "medium";

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("vertexAICredentials", "Google Vertex AI服务账户JSON密钥内容");
        map.put("deployName", "部署模型名称");
        map.put("location", "部署区域");
        map.put("supportStreamOptions", "流式响应选项支持");
        map.put("supportThinking", "思考模式支持");
        map.put("reasoningEffort", "推理努力程度(low/medium/high/none)");
        return map;
    }
} 