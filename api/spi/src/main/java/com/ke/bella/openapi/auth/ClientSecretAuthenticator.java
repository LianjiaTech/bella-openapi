package com.ke.bella.openapi.auth;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;

/**
 * Credential authenticator for SPI capability services: client secrets are verified locally
 * against the statically configured public keys (no call to Bella OpenAPI on the request path);
 * long-lived API keys are delegated to the wrapped authenticator (typically OpenapiClient).
 *
 * Wire it into the default request filter:
 *
 * <pre>
 * new BellaRequestFilter(serviceId, new ClientSecretAuthenticator(clientSecretProperties, openapiClient));
 * </pre>
 */
public class ClientSecretAuthenticator implements CredentialAuthenticator {
    private final ClientSecretVerifier verifier;
    private final CredentialAuthenticator apikeyAuthenticator;

    public ClientSecretAuthenticator(ClientSecretProperties properties, CredentialAuthenticator apikeyAuthenticator) {
        this(properties.verifier(), apikeyAuthenticator);
    }

    public ClientSecretAuthenticator(ClientSecretVerifier verifier, CredentialAuthenticator apikeyAuthenticator) {
        this.verifier = verifier;
        this.apikeyAuthenticator = apikeyAuthenticator;
    }

    @Override
    public AuthResult authenticate(String auth, String requestPath) {
        String credential = CredentialExtractor.stripBearer(auth);
        if(StringUtils.isEmpty(credential)) {
            return null;
        }
        if(credential.startsWith(CredentialExtractor.CLIENT_SECRET_PREFIX)) {
            return verifyClientSecret(credential);
        }
        return apikeyAuthenticator == null ? null : apikeyAuthenticator.authenticate(auth, requestPath);
    }

    private AuthResult verifyClientSecret(String credential) {
        if(verifier == null) {
            return null;
        }
        ClientSecretClaims claims;
        try {
            claims = verifier.verify(credential);
        } catch (BellaException e) {
            return null;
        }
        ApikeyInfo apikeyInfo = new ApikeyInfo();
        apikeyInfo.setCode(claims.getSub());
        apikeyInfo.setApikey(credential);
        return AuthResult.builder()
                .apikeyInfo(apikeyInfo)
                .credentialType(CredentialType.CLIENT_SECRET)
                .clientid(claims.getClientid())
                .build();
    }
}
