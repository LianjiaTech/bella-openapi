package com.ke.bella.openapi.auth;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import com.ke.bella.openapi.common.exception.BellaException;

/**
 * Local client-secret verifier backed by statically configured public keys, selected by
 * {@code kid}. Verification is fully offline: signature, issuer, audience, and validity window
 * with clock-skew leeway. It intentionally reveals no failure detail to callers.
 */
public class ClientSecretVerifier {
    private static final long LEEWAY_SECONDS = 60;

    private final Map<String, RSAPublicKey> publicKeys;
    private final String issuer;
    private final String audience;

    public ClientSecretVerifier(Map<String, RSAPublicKey> publicKeys, String issuer, String audience) {
        this.publicKeys = new LinkedHashMap<>(publicKeys);
        this.issuer = issuer;
        this.audience = audience;
    }

    public ClientSecretClaims verify(String clientSecret) {
        ClientSecretJwt.Decoded decoded;
        try {
            decoded = ClientSecretJwt.decode(clientSecret);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        RSAPublicKey publicKey = decoded.getKid() == null ? null : publicKeys.get(decoded.getKid());
        if(publicKey == null || !verifySignature(decoded, publicKey)) {
            throw invalid();
        }
        ClientSecretClaims claims = decoded.getClaims();
        if(!StringUtils.equals(issuer, claims.getIss()) || !StringUtils.equals(audience, claims.getAud())
                || StringUtils.isBlank(claims.getSub()) || StringUtils.isBlank(claims.getJti())) {
            throw invalid();
        }
        long now = System.currentTimeMillis() / 1000;
        if(now < claims.getNbf() - LEEWAY_SECONDS || now >= claims.getExp() + LEEWAY_SECONDS || claims.getExp() <= claims.getNbf()) {
            throw invalid();
        }
        return claims;
    }

    private boolean verifySignature(ClientSecretJwt.Decoded decoded, RSAPublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(decoded.getSigningInput());
            return signature.verify(decoded.getSignature());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static BellaException.ClientSecretException invalid() {
        return new BellaException.ClientSecretException("client_secret_invalid", HttpStatus.UNAUTHORIZED,
                "Client secret is invalid or expired");
    }
}
