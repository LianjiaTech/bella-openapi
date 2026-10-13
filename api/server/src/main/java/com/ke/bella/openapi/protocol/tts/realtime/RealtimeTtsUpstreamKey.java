package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Value
public class RealtimeTtsUpstreamKey {
    String protocol;
    String channelCode;
    String forwardUrl;
    String propertyClass;
    String resourceId;
    String appId;
    String websocketUrl;
    String authMode;
    String authType;
    String authHeader;
    String accessKeyHash;
    String authApiKeyHash;
    String authSecretHash;

    public static RealtimeTtsUpstreamKey of(String protocol, String channelCode, String forwardUrl, RealtimeTtsProperty property) {
        if(property instanceof HuoshanRealtimeTtsProperty) {
            HuoshanRealtimeTtsProperty huoshan = (HuoshanRealtimeTtsProperty) property;
            AuthorizationProperty auth = huoshan.getAuth();
            return new RealtimeTtsUpstreamKey(protocol, channelCode, forwardUrl, property.getClass().getName(),
                    huoshan.getResourceId(), huoshan.getAppId(), huoshan.getWebsocketUrl(),
                    hasLegacyAuth(auth) ? "legacy" : "latest",
                    auth == null || auth.getType() == null ? null : auth.getType().name(),
                    auth == null ? null : auth.getHeader(),
                    fingerprint(huoshan.getAccessKey()),
                    auth == null ? null : fingerprint(auth.getApiKey()),
                    auth == null ? null : fingerprint(auth.getSecret()));
        }
        return new RealtimeTtsUpstreamKey(protocol, channelCode, forwardUrl, property == null ? null : property.getClass().getName(),
                null, null, null, "default", null, null, null, null, null);
    }

    private static boolean hasLegacyAuth(AuthorizationProperty auth) {
        return auth != null && (auth.getType() != null
                || notBlank(auth.getHeader())
                || notBlank(auth.getApiKey())
                || notBlank(auth.getSecret()));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String fingerprint(String value) {
        if(!notBlank(value)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
