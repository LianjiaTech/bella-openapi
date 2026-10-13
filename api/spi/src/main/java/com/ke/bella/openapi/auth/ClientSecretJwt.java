package com.ke.bella.openapi.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Data;

/**
 * Minimal compact-JWS codec for client secrets. The algorithm is pinned to RS256: signing always
 * emits it and decoding rejects anything else, so tokens with a substituted {@code alg} header
 * never reach signature verification.
 *
 * A full client secret is the {@code csak-} prefix followed by the compact JWS.
 */
public final class ClientSecretJwt {
    public static final String PREFIX = CredentialExtractor.CLIENT_SECRET_PREFIX;
    public static final String ALGORITHM = "RS256";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private ClientSecretJwt() {
    }

    public static String sign(ClientSecretClaims claims, String kid, RSAPrivateKey privateKey) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("alg", ALGORITHM);
        header.put("typ", "JWT");
        header.put("kid", kid);
        String signingInput = encode(JacksonUtils.serialize(header)) + "." + encode(JacksonUtils.serialize(claims));
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return PREFIX + signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign client secret", e);
        }
    }

    public static Decoded decode(String clientSecret) {
        if(clientSecret == null || !clientSecret.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a client secret");
        }
        String[] parts = clientSecret.substring(PREFIX.length()).split("\\.", -1);
        if(parts.length != 3 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1]) || StringUtils.isBlank(parts[2])) {
            throw new IllegalArgumentException("Malformed client secret");
        }
        Header header = parse(decodeSegment(parts[0]), Header.class);
        ClientSecretClaims claims = parse(decodeSegment(parts[1]), ClientSecretClaims.class);
        if(header == null || claims == null || !ALGORITHM.equals(header.getAlg())) {
            throw new IllegalArgumentException("Malformed client secret");
        }
        Decoded decoded = new Decoded();
        decoded.setKid(header.getKid());
        decoded.setClaims(claims);
        decoded.setSigningInput((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        decoded.setSignature(Base64.getUrlDecoder().decode(parts[2]));
        return decoded;
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeSegment(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private static <T> T parse(String json, Class<T> type) {
        try {
            return JacksonUtils.deserialize(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed client secret", e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Header {
        private String alg;
        private String typ;
        private String kid;
    }

    @Data
    public static class Decoded {
        private String kid;
        private ClientSecretClaims claims;
        private byte[] signingInput;
        private byte[] signature;
    }
}
