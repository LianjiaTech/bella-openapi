package com.ke.bella.openapi.auth;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

/**
 * Static key material for client-secret JWTs. The private key (PKCS#8 PEM) is held only by the
 * issuer (Bella OpenAPI); verifiers are configured with the public keys (X.509 PEM) keyed by kid.
 * Multiple public keys may coexist to support rotation.
 */
@Data
@ConfigurationProperties(prefix = "bella.client-secret")
public class ClientSecretProperties {
    private String issuer = "bella-openapi";
    private String audience = "bella-openapi";
    private String kid = "default";
    @ToString.Exclude
    private String privateKey;
    private Map<String, String> publicKeys = new LinkedHashMap<>();

    /** Signing key for issuing client secrets, or null on verification-only deployments. */
    public RSAPrivateKey signingKey() {
        return StringUtils.isBlank(privateKey) ? null : PemKeys.readPrivateKey(privateKey);
    }

    /** Local verifier built from the configured public keys, or null when none are configured. */
    public ClientSecretVerifier verifier() {
        if(publicKeys.isEmpty()) {
            return null;
        }
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        publicKeys.forEach((keyId, pem) -> keys.put(keyId, PemKeys.readPublicKey(pem)));
        return new ClientSecretVerifier(keys, issuer, audience);
    }
}
