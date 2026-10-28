package com.ke.bella.openapi.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

public final class CredentialExtractor {
    public static final String CLIENT_SECRET_PREFIX = "csak-";
    public static final String SERVICE_CLIENT_PREFIX = "sc-";

    private CredentialExtractor() {
    }

    /** Strips an optional "Bearer " prefix from an authorization value. */
    public static String stripBearer(String auth) {
        if(auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return auth;
    }

    public static boolean isServiceClient(String auth) {
        String credential = stripBearer(auth);
        return StringUtils.isNotBlank(credential) && credential.startsWith(SERVICE_CLIENT_PREFIX);
    }

    public static String extract(HttpServletRequest request) {
        String credential = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(StringUtils.isNotEmpty(credential)) {
            return credential;
        }
        String uri = request.getRequestURI();
        if(uri.startsWith("/v1beta/models") || uri.startsWith("/v1beta1/publishers/google/models")) {
            credential = request.getHeader("x-goog-api-key");
        }
        if(StringUtils.isEmpty(credential) && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            credential = webSocketCredential(request.getHeader("Sec-WebSocket-Protocol"));
        }
        return StringUtils.isEmpty(credential) ? request.getHeader("x-api-key") : credential;
    }

    private static String webSocketCredential(String protocols) {
        if(StringUtils.isBlank(protocols)) {
            return null;
        }
        for(String item : protocols.split(",")) {
            String protocol = item.trim();
            if(protocol.startsWith(CLIENT_SECRET_PREFIX)) {
                return protocol;
            }
            if(protocol.startsWith("bella-auth.")) {
                try {
                    String credential = new String(Base64.getUrlDecoder().decode(protocol.substring("bella-auth.".length())),
                            StandardCharsets.UTF_8);
                    if(credential.startsWith(CLIENT_SECRET_PREFIX)) {
                        return credential;
                    }
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
