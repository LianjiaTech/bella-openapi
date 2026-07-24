package com.ke.bella.openapi.protocol.batch;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BatchProperty implements IProtocolProperty {

    AuthorizationProperty auth;
    String fileServiceUrl;
    int maxSize;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        map.put("fileServiceUrl", "文件服务地址");
        map.put("maxSize", "batch文件最大条数");
        return map;
    }

}
