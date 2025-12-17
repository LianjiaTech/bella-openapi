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
public class VertexProperty extends CompletionProperty {
    boolean supportSystemInstruction = true;
    boolean supportThinkConfig = false;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("supportSystemInstruction", "是否支持系统指令");
        map.put("supportThinkConfig", "是否支持开启思考");
        return map;
    }
}
