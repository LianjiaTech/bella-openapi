package com.ke.bella.openapi.protocol.completion;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HuoshanProperty extends OpenAIProperty {
    boolean convertDeveloperToSystem;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("convertDeveloperToSystem", "是否将developer role转换为system role");
        return map;
    }
}
