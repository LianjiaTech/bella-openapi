package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class RouteAffinityKeyGenerator {
    private static final String KEY_PREFIX = "bella-openapi-route-affinity";

    @Value("${bella.openapi.route-affinity.enabled:true}")
    private boolean enabled;

    @Value("${bella.openapi.route-affinity.hash-prefix-bytes:1024}")
    private int hashPrefixBytes;

    @Value("${bella.openapi.route-affinity.min-prefix-bytes:1024}")
    private int minPrefixBytes;

    public String forChat(String endpoint, String model, CompletionRequest request) {
        String sessionId = SessionIdService.resolveForChat(request, hashPrefixBytes, minPrefixBytes);
        return buildKey(endpoint, model, sessionId);
    }

    public String forMessages(String endpoint, String model, MessageRequest request) {
        String sessionId = SessionIdService.resolveForMessages(request, hashPrefixBytes, minPrefixBytes);
        return buildKey(endpoint, model, sessionId);
    }

    public String forResponses(String endpoint, String model, ResponsesApiRequest request) {
        String sessionId = SessionIdService.resolveForResponses(request, hashPrefixBytes, minPrefixBytes);
        return buildKey(endpoint, model, sessionId);
    }

    private String buildKey(String endpoint, String model, String sessionId) {
        if(!enabled || StringUtils.isBlank(endpoint) || StringUtils.isBlank(model) || StringUtils.isBlank(sessionId)) {
            return null;
        }
        return KEY_PREFIX + ":" + endpoint + ":" + model + ":session:" + sha256Hex(sessionId.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

}
