package com.ke.bella.openapi.auth;

import com.ke.bella.openapi.apikey.ApikeyInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult {
    private ApikeyInfo apikeyInfo;
    private CredentialType credentialType;
    private String clientid;
    private String serviceClientCode;

    public boolean isClientSecret() {
        return CredentialType.CLIENT_SECRET == credentialType;
    }

    public boolean isServiceClient() {
        return CredentialType.SERVICE_CLIENT == credentialType;
    }
}
