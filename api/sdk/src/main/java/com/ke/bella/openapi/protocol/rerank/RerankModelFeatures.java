package com.ke.bella.openapi.protocol.rerank;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

import java.util.Map;

@Data
public class RerankModelFeatures implements IModelFeatures {
    private Boolean text;
    private Boolean instruct;

    @Override
    public Map<String, String> description() {
        return ImmutableMap.<String, String>builder()
                .put("text", "是否支持文本输入")
                .put("instruct", "是否支持排序指令")
                .build();
    }
}
