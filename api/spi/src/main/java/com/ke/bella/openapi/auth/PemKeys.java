package com.ke.bella.openapi.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Parses RSA keys from PEM text: PKCS#8 for private keys ("BEGIN PRIVATE KEY") and X.509
 * SubjectPublicKeyInfo for public keys ("BEGIN PUBLIC KEY"). Raw base64 without PEM armor is
 * also accepted.
 */
public final class PemKeys {

    private PemKeys() {
    }

    public static RSAPrivateKey readPrivateKey(String pem) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pemBody(pem));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid RSA private key (expecting PKCS#8 PEM)", e);
        }
    }

    public static RSAPublicKey readPublicKey(String pem) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(pemBody(pem));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid RSA public key (expecting X.509 PEM)", e);
        }
    }

    private static byte[] pemBody(String pem) {
        if(pem == null) {
            throw new IllegalArgumentException("PEM content is empty");
        }
        StringBuilder body = new StringBuilder();
        for(String line : pem.split("\\R")) {
            String trimmed = line.trim();
            if(trimmed.isEmpty() || trimmed.startsWith("-----")) {
                continue;
            }
            body.append(trimmed);
        }
        return Base64.getDecoder().decode(body.toString());
    }
}
