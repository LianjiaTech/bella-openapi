package com.ke.bella.openapi.auth;

/**
 * Authenticates a presented credential (long-lived API key or client secret) for a request path.
 * Implementations: {@code OpenapiClient} (central verification via Bella OpenAPI) and the SPI
 * module's {@code ClientSecretAuthenticator} (local JWT verification for client secrets).
 */
public interface CredentialAuthenticator {

    /**
     * @param auth        the raw authorization value, with or without the "Bearer " prefix
     * @param requestPath the path of the request being authorized
     * @return the authentication result, or null when the credential cannot be authenticated
     */
    AuthResult authenticate(String auth, String requestPath);
}
