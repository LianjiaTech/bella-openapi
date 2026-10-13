package com.ke.bella.openapi.protocol.completion;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicVertexProperty extends CompletionProperty {

    @Override
    public String getAnthropicVersion() {
        if(anthropicVersion == null) {
            return "vertex-2023-10-16";
        }
        return anthropicVersion;
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("anthropicVersion", "Vertex Claude anthropic_version，默认 vertex-2023-10-16");
        map.put("auth", "Google Service Account 鉴权配置，type 使用 GOOGLE_AUTH");
        return map;
    }
}
