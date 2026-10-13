package com.ke.bella.openapi.login.oauth;

import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Data
public class OAuthProperties {
    private Map<String, ProviderConfig> providers; // 支持的OAuth提供商配置
    private String clientIndex;
    private String redirect;

    @Data
    public static class ProviderConfig {
        private String clientId;
        private String clientSecret;
        private String scope;
        private String authUri;
        private String tokenUri;
        private String userInfoUri;
        private String[] allowedRedirectDomains;
        private boolean enabled = true;
    }
}
