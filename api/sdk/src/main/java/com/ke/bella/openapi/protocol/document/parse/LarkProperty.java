package com.ke.bella.openapi.protocol.document.parse;

import lombok.Data;

import java.util.Map;

@Data
public class LarkProperty extends DocParseProperty {
    private String clientId;
    private String clientSecret;
    private String uploadDirToken;
    private String cloudDirToken;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("clientId", "clientId");
        map.put("clientSecret", "clientSecret");
        map.put("uploadDirToken", "上传文档目录token");
        map.put("cloudDirToken", "云文档目录token");
        return map;
    }
}
