package com.ke.bella.openapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.openapi.common.exception.BellaException;

public class ClientSecretJwtTest {
    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @Test
    public void signVerifyRoundtripKeepsClaims() {
        long now = now();
        ClientSecretClaims claims = claims(now, now + 600);
        String secret = sign(claims);

        assertThat(secret).startsWith("csak-eyJ");
        ClientSecretClaims verified = verifier().verify(secret);
        assertThat(verified).isEqualTo(claims);
    }

    @Test
    public void verifierRejectsUnknownKidAndForeignKey() {
        long now = now();
        String unknownKid = ClientSecretJwt.sign(claims(now, now + 600), "other-kid", (RSAPrivateKey) keyPair.getPrivate());
        assertInvalid(unknownKid);

        String foreignKey = ClientSecretJwt.sign(claims(now, now + 600), "default", (RSAPrivateKey) otherKeyPair.getPrivate());
        assertInvalid(foreignKey);
    }

    @Test
    public void verifierRejectsWrongIssuerOrAudience() {
        long now = now();
        ClientSecretClaims wrongIssuer = claims(now, now + 600);
        wrongIssuer.setIss("someone-else");
        assertInvalid(sign(wrongIssuer));

        ClientSecretClaims wrongAudience = claims(now, now + 600);
        wrongAudience.setAud("someone-else");
        assertInvalid(sign(wrongAudience));
    }

    @Test
    public void verifierAppliesClockSkewLeewayToValidityWindow() {
        long now = now();
        assertInvalid(sign(claims(now - 600, now - 120)));
        assertInvalid(sign(claims(now + 120, now + 600)));
        // within the 60-second leeway on both edges
        assertThat(verifier().verify(sign(claims(now - 600, now - 30)))).isNotNull();
        assertThat(verifier().verify(sign(claims(now + 30, now + 600)))).isNotNull();
    }

    @Test
    public void verifierRejectsAlgorithmSubstitution() {
        long now = now();
        String secret = sign(claims(now, now + 600));
        String[] parts = secret.substring(ClientSecretJwt.PREFIX.length()).split("\\.");
        String noneHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"default\"}".getBytes(StandardCharsets.UTF_8));
        assertInvalid(ClientSecretJwt.PREFIX + noneHeader + "." + parts[1] + "." + parts[2]);
        assertInvalid(ClientSecretJwt.PREFIX + noneHeader + "." + parts[1] + ".");
    }

    @Test
    public void decodeRejectsMalformedTokens() {
        assertThatThrownBy(() -> ClientSecretJwt.decode("sk-not-a-client-secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientSecretJwt.decode("csak-only.two")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientSecretJwt.decode("csak-a.b.c.d")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientSecretJwt.decode("csak-!!!.@@@.###")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void pemKeysParsePemArmorAndRawBase64() {
        String armored = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        assertThat(PemKeys.readPublicKey(armored)).isEqualTo(keyPair.getPublic());
        assertThat(PemKeys.readPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())))
                .isEqualTo(keyPair.getPublic());
        assertThat(PemKeys.readPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())))
                .isEqualTo(keyPair.getPrivate());
        assertThatThrownBy(() -> PemKeys.readPublicKey("not a key")).isInstanceOf(IllegalArgumentException.class);
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private ClientSecretClaims claims(long nbf, long exp) {
        return ClientSecretClaims.builder()
                .iss("bella-openapi")
                .aud("bella-openapi")
                .sub("ak-code")
                .clientid("client-1")
                .iat(nbf)
                .nbf(nbf)
                .exp(exp)
                .jti(UUID.randomUUID().toString())
                .build();
    }

    private String sign(ClientSecretClaims claims) {
        return ClientSecretJwt.sign(claims, "default", (RSAPrivateKey) keyPair.getPrivate());
    }

    private ClientSecretVerifier verifier() {
        Map<String, RSAPublicKey> keys = Collections.singletonMap("default", (RSAPublicKey) keyPair.getPublic());
        return new ClientSecretVerifier(keys, "bella-openapi", "bella-openapi");
    }

    private void assertInvalid(String secret) {
        assertThatThrownBy(() -> verifier().verify(secret))
                .isInstanceOfSatisfying(BellaException.ClientSecretException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("client_secret_invalid"));
    }
}
