package com.ke.bella.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.common.exception.BellaException;

@RunWith(MockitoJUnitRunner.class)
public class AuthServiceTest {
    @Mock
    private ApikeyService apikeyService;
    @Mock
    private ClientSecretService clientSecretService;
    private AuthService authService;

    @Before
    public void setUp() {
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "apikeyService", apikeyService);
        ReflectionTestUtils.setField(authService, "clientSecretService", clientSecretService);
    }

    @Test
    public void routesBearerClientSecretToRedisAuthentication() {
        String secret = "csak-123e4567-e89b-42d3-a456-426614174000";
        AuthResult expected = AuthResult.builder().credentialType(CredentialType.CLIENT_SECRET).build();
        when(clientSecretService.authenticate(secret)).thenReturn(expected);

        assertThat(authService.authenticate("Bearer " + secret)).isSameAs(expected);
        verify(clientSecretService).authenticate(secret);
    }

    @Test
    public void keepsLongApiKeyAuthenticationBehavior() {
        ApikeyInfo apikeyInfo = ApikeyInfo.builder().code("ak-code").build();
        when(apikeyService.verifyAuth("Bearer sk-long-lived")).thenReturn(apikeyInfo);

        AuthResult result = authService.authenticate("Bearer sk-long-lived");

        assertThat(result.getCredentialType()).isEqualTo(CredentialType.API_KEY);
        assertThat(result.getApikeyInfo()).isSameAs(apikeyInfo);
        verify(apikeyService).verifyAuth("Bearer sk-long-lived");
    }

    @Test
    public void revalidationUsesCurrentSourcePermission() {
        ApikeyInfo apikeyInfo = org.mockito.Mockito.mock(ApikeyInfo.class);
        when(apikeyInfo.hasPermission("/v1/audio/realtime")).thenReturn(false);
        String secret = "csak-123e4567-e89b-42d3-a456-426614174000";
        when(clientSecretService.authenticate(secret)).thenReturn(AuthResult.builder()
                .apikeyInfo(apikeyInfo)
                .credentialType(CredentialType.CLIENT_SECRET)
                .build());

        assertThatThrownBy(() -> authService.authenticate(secret, "/v1/audio/realtime"))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_forbidden"));
    }

    @Test
    public void acceptsLegacyServiceClientCredentialWithEffectiveAkCode() {
        String token = "sc-service-token";
        ApikeyInfo effective = ApikeyInfo.builder().code("ak-user-001").build();
        ReflectionTestUtils.setField(authService, "serviceClientToken", token);
        ReflectionTestUtils.setField(authService, "serviceClientCode", "default-client");
        when(apikeyService.verifyAuthByCode("ak-user-001")).thenReturn(effective);

        AuthResult result = authService.authenticateWithEffectiveApiKeyCode(
                "Bearer " + token, "ak-user-001", null);

        assertThat(result.getCredentialType()).isEqualTo(CredentialType.SERVICE_CLIENT);
        assertThat(result.getServiceClientCode()).isEqualTo("default-client");
        assertThat(result.getApikeyInfo()).isSameAs(effective);
        verify(apikeyService).verifyAuthByCode("ak-user-001");
    }

    @Test
    public void rejectsEffectiveAkCodeForOrdinaryApiKey() {
        assertThatThrownBy(() -> authService.authenticateWithEffectiveApiKeyCode(
                "Bearer sk-user-token", "ak-user-001", null))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("only valid for ServiceClient");
    }
}
