package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RerankProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    private String deployName;
    private String encodingType = StringUtils.EMPTY;
    private Integer maxDocuments;
    private Integer maxTokensPerItem;
    private Integer maxTotalTokens;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("deployName", "供应商侧部署/模型名称");
        map.put("encodingType", "token编码类型");
        map.put("maxDocuments", "最大文档数");
        map.put("maxTokensPerItem", "单条最大输入token");
        map.put("maxTotalTokens", "请求最大输入token");
        return map;
    }
}
