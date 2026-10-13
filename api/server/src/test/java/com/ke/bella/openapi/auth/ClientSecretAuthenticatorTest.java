package com.ke.bella.openapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Test;

public class ClientSecretAuthenticatorTest {
    private static KeyPair keyPair;
    private static ClientSecretProperties properties;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        properties = new ClientSecretProperties();
        properties.getPublicKeys().put("default", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    @Test
    public void verifiesClientSecretsLocally() {
        CredentialAuthenticator delegate = mock(CredentialAuthenticator.class);
        ClientSecretAuthenticator authenticator = new ClientSecretAuthenticator(properties, delegate);
        long now = System.currentTimeMillis() / 1000;

        AuthResult result = authenticator.authenticate("Bearer " + sign(now, now + 600), "/v1/audio/transcriptions");

        assertThat(result.getCredentialType()).isEqualTo(CredentialType.CLIENT_SECRET);
        assertThat(result.getApikeyInfo().getCode()).isEqualTo("ak-code");
        assertThat(result.getClientid()).isEqualTo("client-1");
        verify(delegate, never()).authenticate(anyString(), anyString());

        assertThat(authenticator.authenticate("Bearer " + sign(now - 600, now - 120), "/v1/audio/transcriptions")).isNull();
    }

    @Test
    public void delegatesApiKeysToWrappedAuthenticator() {
        CredentialAuthenticator delegate = mock(CredentialAuthenticator.class);
        AuthResult expected = AuthResult.builder().credentialType(CredentialType.API_KEY).build();
        when(delegate.authenticate("Bearer sk-test", "/v1/audio/speech")).thenReturn(expected);
        ClientSecretAuthenticator authenticator = new ClientSecretAuthenticator(properties, delegate);

        assertThat(authenticator.authenticate("Bearer sk-test", "/v1/audio/speech")).isSameAs(expected);
        verify(delegate).authenticate("Bearer sk-test", "/v1/audio/speech");
    }

    @Test
    public void rejectsClientSecretsWhenPublicKeysAreNotConfigured() {
        ClientSecretAuthenticator authenticator = new ClientSecretAuthenticator(new ClientSecretProperties(), null);

        assertThat(authenticator.authenticate("Bearer csak-not-configured", "/v1/audio/transcriptions")).isNull();
    }

    private String sign(long nbf, long exp) {
        ClientSecretClaims claims = ClientSecretClaims.builder()
                .iss("bella-openapi")
                .aud("bella-openapi")
                .sub("ak-code")
                .clientid("client-1")
                .iat(nbf)
                .nbf(nbf)
                .exp(exp)
                .jti(UUID.randomUUID().toString())
                .build();
        return ClientSecretJwt.sign(claims, "default", (RSAPrivateKey) keyPair.getPrivate());
    }
}
