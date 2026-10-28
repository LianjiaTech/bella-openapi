package com.ke.bella.openapi.intercept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.service.AuthService;

public class AuthorizationInterceptorServiceClientTest {
    private ApikeyService apikeyService;
    private AuthService authService;
    private AuthorizationInterceptor interceptor;

    @Before
    public void setUp() {
        apikeyService = mock(ApikeyService.class);
        authService = mock(AuthService.class);
        interceptor = new AuthorizationInterceptor();
        ReflectionTestUtils.setField(interceptor, "apikeyService", apikeyService);
        ReflectionTestUtils.setField(interceptor, "authService", authService);
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void acceptsLegacyServiceClientProtocolWithoutExplicitClientHeader() {
        ApikeyInfo effective = allowedApikey("ak-user-001");
        AuthResult result = AuthResult.builder()
                .apikeyInfo(effective)
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build();
        when(authService.authenticateWithEffectiveApiKeyCode(
                "Bearer sc-service-token", "ak-user-001", null)).thenReturn(result);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer sc-service-token");
        request.addHeader("X-Effective-Api-Key-Code", "ak-user-001");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), null)).isTrue();
        assertThat(EndpointContext.getProcessData().getCredentialType()).isEqualTo(CredentialType.SERVICE_CLIENT);
        assertThat(EndpointContext.getProcessData().getServiceClientCode()).isEqualTo("default-client");
        verify(authService).authenticateWithEffectiveApiKeyCode(
                "Bearer sc-service-token", "ak-user-001", null);
    }

    @Test
    public void acceptsExplicitServiceClientProtocol() {
        ApikeyInfo effective = allowedApikey("ak-user-001");
        AuthResult result = AuthResult.builder()
                .apikeyInfo(effective)
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build();
        when(authService.authenticateWithEffectiveApiKeyCode(
                "Bearer service-credential-xxx", "ak-user-001", "default-client")).thenReturn(result);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer service-credential-xxx");
        request.addHeader("X-Service-Client", "default-client");
        request.addHeader("X-Effective-Api-Key-Code", "ak-user-001");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), null)).isTrue();
        assertThat(EndpointContext.getProcessData().getCredentialType()).isEqualTo(CredentialType.SERVICE_CLIENT);
        assertThat(EndpointContext.getProcessData().getServiceClientCode()).isEqualTo("default-client");
        verify(authService).authenticateWithEffectiveApiKeyCode(
                "Bearer service-credential-xxx", "ak-user-001", "default-client");
    }

    @Test
    public void rejectsNewProtocolWhenServiceClientHeaderIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer service-credential-xxx");
        request.addHeader("X-Effective-Api-Key-Code", "ak-user-001");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), null))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("X-Service-Client");
    }

    @Test
    public void rejectsDuplicateAuthorizationHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer service-credential-xxx");
        request.addHeader("Authorization", "Bearer another-credential");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), null))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("Invalid Authorization header");
    }

    private ApikeyInfo allowedApikey(String code) {
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.getIncluded().add("/v1/chat/**");
        return ApikeyInfo.builder().code(code).rolePath(rolePath).build();
    }
}
