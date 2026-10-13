package com.ke.bella.openapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.UUID;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.ClientSecretClaims;
import com.ke.bella.openapi.auth.ClientSecretJwt;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateRequest;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateResponse;
import com.ke.bella.openapi.auth.ClientSecretProperties;
import com.ke.bella.openapi.common.exception.BellaException;

@RunWith(MockitoJUnitRunner.class)
public class ClientSecretServiceTest {
    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @Mock
    private ApikeyService apikeyService;

    private ClientSecretService service;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @Before
    public void setUp() {
        service = newService(properties());
    }

    private ClientSecretProperties properties() {
        ClientSecretProperties properties = new ClientSecretProperties();
        properties.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        properties.getPublicKeys().put("default", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        return properties;
    }

    private ClientSecretService newService(ClientSecretProperties properties) {
        ClientSecretService created = new ClientSecretService();
        ReflectionTestUtils.setField(created, "apikeyService", apikeyService);
        ReflectionTestUtils.setField(created, "properties", properties);
        created.init();
        return created;
    }

    @Test
    public void createSignsJwtWithSnapshotClaims() {
        ClientSecretCreateRequest request = new ClientSecretCreateRequest();
        request.setExpiresIn(60);
        request.setClientid("  app-client-123  ");

        long before = System.currentTimeMillis() / 1000;
        ClientSecretCreateResponse response = service.create(ApikeyInfo.builder().code("ak-code").build(), request);

        assertThat(response.getClientSecret()).startsWith("csak-");
        assertThat(response.getClientid()).isEqualTo("app-client-123");
        assertThat(response.getExpiresAt()).isBetween(before + 60, before + 61);

        ClientSecretClaims claims = ClientSecretJwt.decode(response.getClientSecret()).getClaims();
        assertThat(claims.getIss()).isEqualTo("bella-openapi");
        assertThat(claims.getAud()).isEqualTo("bella-openapi");
        assertThat(claims.getSub()).isEqualTo("ak-code");
        assertThat(claims.getClientid()).isEqualTo("app-client-123");
        assertThat(claims.getNbf()).isEqualTo(claims.getIat());
        assertThat(claims.getExp()).isEqualTo(response.getExpiresAt());
        assertThat(claims.getJti()).isNotBlank();
    }

    @Test
    public void toStringNeverLeaksKeyMaterialOrTokens() {
        ClientSecretProperties properties = properties();
        assertThat(properties.toString()).doesNotContain(properties.getPrivateKey());

        ClientSecretCreateRequest request = new ClientSecretCreateRequest();
        request.setClientid("client");
        ClientSecretCreateResponse response = service.create(ApikeyInfo.builder().code("ak-code").build(), request);
        assertThat(response.toString()).doesNotContain(response.getClientSecret());
    }

    @Test
    public void createRejectsInvalidTtlAndClientid() {
        ClientSecretCreateRequest shortTtl = new ClientSecretCreateRequest();
        shortTtl.setExpiresIn(59);
        shortTtl.setClientid("client");
        assertThatThrownBy(() -> service.create(ApikeyInfo.builder().code("ak-code").build(), shortTtl))
                .isInstanceOf(IllegalArgumentException.class);

        ClientSecretCreateRequest longTtl = new ClientSecretCreateRequest();
        longTtl.setExpiresIn(ClientSecretService.MAX_EXPIRES_IN + 1);
        longTtl.setClientid("client");
        assertThatThrownBy(() -> service.create(ApikeyInfo.builder().code("ak-code").build(), longTtl))
                .isInstanceOf(IllegalArgumentException.class);

        ClientSecretCreateRequest injectedClientid = new ClientSecretCreateRequest();
        injectedClientid.setClientid("client\nforged");
        assertThatThrownBy(() -> service.create(ApikeyInfo.builder().code("ak-code").build(), injectedClientid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void createFailsWithoutSigningKey() {
        ClientSecretProperties properties = properties();
        properties.setPrivateKey(null);
        ClientSecretService withoutKey = newService(properties);

        ClientSecretCreateRequest request = new ClientSecretCreateRequest();
        request.setClientid("client");
        assertThatThrownBy(() -> withoutKey.create(ApikeyInfo.builder().code("ak-code").build(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void authenticateResolvesCurrentSourceAndClientContext() {
        String secret = createSecret();
        ApikeyInfo source = ApikeyInfo.builder().code("ak-code").build();
        when(apikeyService.verifyAuthByCode("ak-code")).thenReturn(source);

        AuthResult result = service.authenticate(secret);

        assertThat(result.getCredentialType()).isEqualTo(CredentialType.CLIENT_SECRET);
        assertThat(result.getApikeyInfo()).isSameAs(source);
        assertThat(result.getApikeyInfo().getApikey()).isEqualTo(secret);
        assertThat(result.getClientid()).isEqualTo("client-7");
    }

    @Test
    public void authenticateRejectsExpiredToken() {
        long now = System.currentTimeMillis() / 1000;
        String expired = sign(claims("ak-code", now - 600, now - 120));

        assertThatThrownBy(() -> service.authenticate(expired))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_invalid"));
        verify(apikeyService, never()).verifyAuthByCode(anyString());
    }

    @Test
    public void authenticateRejectsTamperedOrForeignSignature() {
        String secret = createSecret();
        String tampered = secret.substring(0, secret.length() - 4) + "AAAA";
        assertThatThrownBy(() -> service.authenticate(tampered))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_invalid"));

        long now = System.currentTimeMillis() / 1000;
        String foreign = ClientSecretJwt.sign(claims("ak-code", now, now + 600), "default", (RSAPrivateKey) otherKeyPair.getPrivate());
        assertThatThrownBy(() -> service.authenticate(foreign))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_invalid"));
        verify(apikeyService, never()).verifyAuthByCode(anyString());
    }

    @Test
    public void authenticateFailsClosedForInvalidSource() {
        String secret = createSecret();
        when(apikeyService.verifyAuthByCode("ak-code")).thenThrow(new BellaException.AuthorizationException("inactive"));

        assertThatThrownBy(() -> service.authenticate(secret))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_source_invalid"));
    }

    @Test
    public void authenticateFailsClosedWithoutPublicKeys() {
        ClientSecretProperties properties = properties();
        properties.getPublicKeys().clear();
        ClientSecretService withoutKeys = newService(properties);

        assertThatThrownBy(() -> withoutKeys.authenticate(createSecret()))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_invalid"));
    }

    private String createSecret() {
        ClientSecretCreateRequest request = new ClientSecretCreateRequest();
        request.setExpiresIn(600);
        request.setClientid("client-7");
        return service.create(ApikeyInfo.builder().code("ak-code").build(), request).getClientSecret();
    }

    private ClientSecretClaims claims(String akCode, long nbf, long exp) {
        return ClientSecretClaims.builder()
                .iss("bella-openapi")
                .aud("bella-openapi")
                .sub(akCode)
                .clientid("client-7")
                .iat(nbf)
                .nbf(nbf)
                .exp(exp)
                .jti(UUID.randomUUID().toString())
                .build();
    }

    private String sign(ClientSecretClaims claims) {
        return ClientSecretJwt.sign(claims, "default", (RSAPrivateKey) keyPair.getPrivate());
    }
}
