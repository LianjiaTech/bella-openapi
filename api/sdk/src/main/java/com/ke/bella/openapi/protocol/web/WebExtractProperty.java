package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Extract Property
 * Configuration properties for web content extraction
 */
@Data
public class WebExtractProperty implements IProtocolProperty, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Authorization configuration for Tavily API access
     */
    private AuthorizationProperty auth;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "鉴权配置");
        return map;
    }
}
