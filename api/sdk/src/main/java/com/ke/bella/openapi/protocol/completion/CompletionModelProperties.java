package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableSortedMap;
import com.ke.bella.openapi.protocol.IModelProperties;
import lombok.Data;

import java.util.Map;

@Data
public class CompletionModelProperties implements IModelProperties {
    private int max_input_context;
    private int max_output_context;

    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of("max_input_context", "最大输入长度", "max_output_context", "最大输出长度");
    }
}
