package com.ke.bella.openapi.service;

import java.security.interfaces.RSAPrivateKey;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.ClientSecretClaims;
import com.ke.bella.openapi.auth.ClientSecretJwt;
import com.ke.bella.openapi.auth.ClientSecretProperties;
import com.ke.bella.openapi.auth.ClientSecretVerifier;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateRequest;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateResponse;
import com.ke.bella.openapi.common.exception.BellaException;

@Component
public class ClientSecretService {
    public static final int DEFAULT_EXPIRES_IN = 600;
    public static final int MIN_EXPIRES_IN = 60;
    public static final int MAX_EXPIRES_IN = 28800;

    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private ClientSecretProperties properties;

    private RSAPrivateKey signingKey;
    private ClientSecretVerifier verifier;

    @PostConstruct
    public void init() {
        signingKey = properties.signingKey();
        verifier = properties.verifier();
    }

    public ClientSecretCreateResponse create(ApikeyInfo source, ClientSecretCreateRequest request) {
        if(signingKey == null) {
            throw new IllegalStateException("Client secret signing key is not configured");
        }
        int expiresIn = validateExpiresIn(request.getExpiresIn());
        String clientid = validateClientid(request.getClientid());
        long issuedAt = System.currentTimeMillis() / 1000;
        long expiresAt = issuedAt + expiresIn;
        ClientSecretClaims claims = ClientSecretClaims.builder()
                .iss(properties.getIssuer())
                .aud(properties.getAudience())
                .sub(source.getCode())
                .clientid(clientid)
                .iat(issuedAt)
                .nbf(issuedAt)
                .exp(expiresAt)
                .jti(UUID.randomUUID().toString())
                .build();
        String clientSecret = ClientSecretJwt.sign(claims, properties.getKid(), signingKey);
        return ClientSecretCreateResponse.builder()
                .clientSecret(clientSecret)
                .clientid(clientid)
                .expiresAt(expiresAt)
                .build();
    }

    public AuthResult authenticate(String clientSecret) {
        if(verifier == null) {
            throw invalid();
        }
        ClientSecretClaims claims = verifier.verify(clientSecret);
        if(!isValidClientid(claims.getClientid())) {
            throw invalid();
        }
        ApikeyInfo source;
        try {
            source = apikeyService.verifyAuthByCode(claims.getSub());
        } catch (BellaException.AuthorizationException e) {
            throw new BellaException.ClientSecretException("client_secret_source_invalid", HttpStatus.UNAUTHORIZED,
                    "Client secret source is invalid");
        }
        source.setApikey(clientSecret);
        return AuthResult.builder()
                .apikeyInfo(source)
                .credentialType(CredentialType.CLIENT_SECRET)
                .clientid(claims.getClientid())
                .build();
    }

    private static int validateExpiresIn(Integer expiresIn) {
        int value = expiresIn == null ? DEFAULT_EXPIRES_IN : expiresIn;
        if(value < MIN_EXPIRES_IN || value > MAX_EXPIRES_IN) {
            throw new IllegalArgumentException("expires_in must be between 60 and 28800 seconds");
        }
        return value;
    }

    private static String validateClientid(String clientid) {
        String value = StringUtils.trim(clientid);
        if(!isValidClientid(value)) {
            throw new IllegalArgumentException("clientid must contain 1 to 128 characters without control characters");
        }
        return value;
    }

    private static boolean isValidClientid(String clientid) {
        return StringUtils.isNotEmpty(clientid) && clientid.length() <= 128
                && clientid.chars().noneMatch(Character::isISOControl);
    }

    private static BellaException.ClientSecretException invalid() {
        return new BellaException.ClientSecretException("client_secret_invalid", HttpStatus.UNAUTHORIZED, "Client secret is invalid or expired");
    }
}
