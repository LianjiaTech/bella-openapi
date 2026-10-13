package com.ke.bella.openapi.protocol.rerank;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IModelProperties;
import lombok.Data;

import java.util.Map;

@Data
public class RerankModelProperties implements IModelProperties {
    private Integer max_documents;
    private Integer max_tokens_per_item;
    private Integer max_total_tokens;

    @Override
    public Map<String, String> description() {
        return ImmutableMap.<String, String>builder()
                .put("max_documents", "最大文档数")
                .put("max_tokens_per_item", "单条最大输入token")
                .put("max_total_tokens", "请求最大输入token")
                .build();
    }
}
