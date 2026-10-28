package com.ke.bella.openapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialExtractor;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.common.exception.BellaException;

@Component
public class AuthService {
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private ClientSecretService clientSecretService;

    @Value("${bella.service-client.code:default-client}")
    private volatile String serviceClientCode;

    @Value("${bella.service-client.token:}")
    private volatile String serviceClientToken;

    public AuthResult authenticate(String auth) {
        String credential = CredentialExtractor.stripBearer(auth);
        if(StringUtils.isBlank(credential)) {
            throw new BellaException.AuthorizationException("Authorization is empty");
        }
        if(credential.startsWith(CredentialExtractor.SERVICE_CLIENT_PREFIX)) {
            return authenticateServiceClient(credential, null, null);
        }
        if(credential.startsWith(CredentialExtractor.CLIENT_SECRET_PREFIX)) {
            return clientSecretService.authenticate(credential);
        }
        ApikeyInfo apikeyInfo = apikeyService.verifyAuth(auth);
        return AuthResult.builder()
                .apikeyInfo(apikeyInfo)
                .credentialType(CredentialType.API_KEY)
                .build();
    }

    public AuthResult authenticateWithEffectiveApiKeyCode(String auth, String effectiveAkCode) {
        return authenticateWithEffectiveApiKeyCode(auth, effectiveAkCode, null);
    }

    public AuthResult authenticateWithEffectiveApiKeyCode(String auth, String effectiveAkCode,
            String requestedServiceClientCode) {
        String credential = CredentialExtractor.stripBearer(auth);
        if(StringUtils.isBlank(credential)) {
            throw new BellaException.AuthorizationException("Authorization is empty");
        }
        boolean serviceClient = credential.startsWith(CredentialExtractor.SERVICE_CLIENT_PREFIX)
                || StringUtils.isNotBlank(requestedServiceClientCode);
        if(!serviceClient) {
            if(StringUtils.isNotBlank(effectiveAkCode)) {
                throw new BellaException.AuthorizationException("X-Effective-Api-Key-Code is only valid for ServiceClient credentials");
            }
            return authenticate(auth);
        }
        return authenticateServiceClient(credential, effectiveAkCode, requestedServiceClientCode);
    }

    public AuthResult authenticate(String auth, String endpoint) {
        AuthResult result = authenticate(auth);
        assertPermission(result, endpoint);
        return result;
    }

    private void assertPermission(AuthResult result, String url) {
        if(result.getApikeyInfo().hasPermission(url)) {
            return;
        }
        if(result.isClientSecret()) {
            throw new BellaException.ClientSecretException("client_secret_forbidden", HttpStatus.FORBIDDEN,
                    "Client secret is not allowed to access this endpoint");
        }
        throw new BellaException.AuthorizationException("没有操作权限");
    }

    private AuthResult authenticateServiceClient(String credential, String effectiveAkCode,
            String requestedServiceClientCode) {
        String configuredToken = serviceClientToken;
        if(StringUtils.isBlank(configuredToken) || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8), credential.getBytes(StandardCharsets.UTF_8))) {
            throw new BellaException.AuthorizationException("ServiceClient credential is invalid");
        }
        if(StringUtils.isNotBlank(requestedServiceClientCode)
                && !StringUtils.equals(serviceClientCode, requestedServiceClientCode.trim())) {
            throw new BellaException.AuthorizationException("ServiceClient code is invalid");
        }
        if(StringUtils.isBlank(effectiveAkCode)) {
            throw new BellaException.AuthorizationException("X-Effective-Api-Key-Code is required");
        }
        ApikeyInfo effectiveApikey = apikeyService.verifyAuthByCode(effectiveAkCode.trim());
        return AuthResult.builder()
                .apikeyInfo(effectiveApikey)
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode(StringUtils.defaultIfBlank(requestedServiceClientCode, serviceClientCode))
                .build();
    }
}
