package com.ke.bella.openapi.service;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;

/**
 * The credential presented at a WebSocket handshake, captured so that long-lived connections can
 * re-authenticate when a new logical task starts. Handlers hold this instead of the auth layer:
 * for long-lived API keys revalidation is a no-op, for client secrets it re-checks token validity
 * and the current state of the source API key. A failed revalidation throws and must reject the
 * new task without closing the connection or interrupting an active task.
 */
public class CredentialSession {
    public static final CredentialSession NOOP = new CredentialSession(null, null, null);

    private final AuthService authService;
    private final CredentialType credentialType;
    private final String credential;

    private CredentialSession(AuthService authService, CredentialType credentialType, String credential) {
        this.authService = authService;
        this.credentialType = credentialType;
        this.credential = credential;
    }

    public static CredentialSession of(AuthService authService, EndpointProcessData processData) {
        return new CredentialSession(authService, processData.getCredentialType(), processData.getApikey());
    }

    /**
     * Re-authenticates the connection credential for a new logical task and applies the refreshed
     * identity to the given process data. Returns null (and changes nothing) when the credential
     * needs no revalidation.
     */
    public AuthResult refreshForNewTask(String endpoint, EndpointProcessData target) {
        if(authService == null || CredentialType.CLIENT_SECRET != credentialType) {
            return null;
        }
        AuthResult result = authService.authenticate(credential, endpoint);
        target.setApikeyInfo(result.getApikeyInfo());
        target.setClientid(result.getClientid());
        return result;
    }
}
