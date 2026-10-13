package com.ke.bella.openapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class CredentialExtractorTest {

    @Test
    public void extractsBrowserWebSocketClientSecret() {
        String secret = "csak-123e4567-e89b-42d3-a456-426614174000";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/audio/realtime");
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Sec-WebSocket-Protocol", "chat, bella-auth." + encoded);

        assertThat(CredentialExtractor.extract(request)).isEqualTo(secret);
    }

    @Test
    public void rejectsMalformedBrowserWebSocketCredential() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/audio/realtime");
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Sec-WebSocket-Protocol", "bella-auth.not-base64!");

        assertThat(CredentialExtractor.extract(request)).isNull();
    }

    @Test
    public void fallsBackToApiKeyHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/images/generations");
        request.addHeader("x-api-key", "sk-test");

        assertThat(CredentialExtractor.extract(request)).isEqualTo("sk-test");
    }
}
