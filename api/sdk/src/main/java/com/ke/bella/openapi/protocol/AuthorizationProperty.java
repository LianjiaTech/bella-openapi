package com.ke.bella.openapi.protocol;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableSortedMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.tools.ant.filters.StringInputStream;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuthorizationProperty implements IProtocolProperty {

    private static Map<String, GoogleCredentials> credentialsMap = new ConcurrentHashMap<>();
    private static Map<String, Object> credentialsLockMap = new ConcurrentHashMap<>();

    private static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform";

    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of("type", "鉴权方式", "header", "自定义的认证头",
                "apiKey", "apiKey(IAM验签时同ak)", "secret", "sk");
    }

    @Getter
    @AllArgsConstructor
    public enum AuthType {
        BASIC,
        BEARER,
        IAM,
        CUSTOM,
        GOOGLE_AUTH
    }

    AuthType type;
    String header;
    String apiKey;
    String secret;

    public String getApiKey() {
        if(type == AuthType.GOOGLE_AUTH) {
            String secret = getSecret();
            try {
                GoogleCredentials credentials = credentialsMap.computeIfAbsent(secret, k -> getGoogleCredentials());
                Object lock = credentialsLockMap.computeIfAbsent(secret, k -> new Object());
                synchronized (lock) {
                    credentials.refreshIfExpired();
                    AccessToken accessToken = credentials.getAccessToken();
                    return accessToken.getTokenValue();
                }
            } catch (IOException e) {
                String secretPrefix = secret.substring(0, Math.min(20, secret.length()));
                throw new RuntimeException("google auth error, secret prefix: " + secretPrefix + ", error: " + e.getMessage(), e);
            }
        } else {
            return apiKey;
        }
    }

    private GoogleCredentials getGoogleCredentials() {
        try {
            return GoogleCredentials.fromStream(new StringInputStream(getSecret()))
                    .createScoped(GOOGLE_CLOUD_PLATFORM_SCOPE);
        } catch (IOException e) {
            throw new RuntimeException("google auth credentials error", e);
        }
    }
}
