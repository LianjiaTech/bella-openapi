package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Property class for Direct mode completion adaptor.
 * Contains minimal configuration needed for direct passthrough.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectCompletionProperty extends CompletionProperty {
    AuthorizationProperty auth;
    String deployName;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("auth", "鉴权配置");
        map.put("deployName", "部署名称");
        return map;
    }
}
