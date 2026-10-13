package com.ke.bella.openapi.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.client.OpenapiClient;

public class BellaRequestFilterClientSecretTest {

    @After
    public void clearContext() {
        BellaContext.clearAll();
    }

    @Test
    public void storesClientSecretAuthResultInBellaContext() {
        String secret = "csak-123e4567-e89b-42d3-a456-426614174000";
        OpenapiClient client = mock(OpenapiClient.class);
        AuthResult authResult = authResult(secret);
        when(client.authenticate("Bearer " + secret, "/v1/audio/transcriptions")).thenReturn(authResult);
        TestBellaRequestFilter filter = new TestBellaRequestFilter(client);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/audio/transcriptions");
        request.addHeader("Authorization", "Bearer " + secret);

        filter.apply(request, new MockHttpServletResponse());

        assertThat(BellaContext.getAuthResultIgnoreNull()).isSameAs(authResult);
        assertThat(BellaContext.getClientid()).isEqualTo("client-1");
        assertThat(BellaContext.getApikey().getApikey()).isEqualTo(secret);
        verify(client).authenticate("Bearer " + secret, "/v1/audio/transcriptions");
    }

    private AuthResult authResult(String secret) {
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.setIncluded(Arrays.asList("/v1/audio/**"));
        ApikeyInfo apikeyInfo = ApikeyInfo.builder().apikey(secret).code("ak-code").rolePath(rolePath).build();
        return AuthResult.builder()
                .apikeyInfo(apikeyInfo)
                .credentialType(CredentialType.CLIENT_SECRET)
                .clientid("client-1")
                .build();
    }

    private static class TestBellaRequestFilter extends BellaRequestFilter {
        TestBellaRequestFilter(OpenapiClient client) {
            super("test-service", client);
        }

        void apply(MockHttpServletRequest request, MockHttpServletResponse response) {
            bellaRequestFilter(request, response);
        }
    }
}
