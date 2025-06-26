package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesProperty implements IProtocolProperty {
    AuthorizationProperty auth;
    String deployName;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("deployName", "部署名称");
        return map;
    }
}
