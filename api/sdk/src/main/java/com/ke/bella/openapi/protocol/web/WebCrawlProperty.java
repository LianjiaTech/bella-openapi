package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Crawl Protocol Property Configuration properties for web crawl services
 */
@Data
public class WebCrawlProperty implements IProtocolProperty {

    /**
     * Authorization property for API authentication
     */
    private AuthorizationProperty auth;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        return map;
    }
}
