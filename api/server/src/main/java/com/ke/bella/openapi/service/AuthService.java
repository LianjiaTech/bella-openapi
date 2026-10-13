package com.ke.bella.openapi.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    public AuthResult authenticate(String auth) {
        String credential = CredentialExtractor.stripBearer(auth);
        if(StringUtils.isBlank(credential)) {
            throw new BellaException.AuthorizationException("Authorization is empty");
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
}
