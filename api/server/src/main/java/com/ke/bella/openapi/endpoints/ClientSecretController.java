package com.ke.bella.openapi.endpoints;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateRequest;
import com.ke.bella.openapi.clientsecret.ClientSecretCreateResponse;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.service.ClientSecretService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@EndpointAPI
@RestController
@RequestMapping("/v1/client_secrets")
@Tag(name = "client_secrets", description = "Short-lived client credentials")
public class ClientSecretController {
    @Autowired
    private ClientSecretService clientSecretService;

    @PostMapping
    @Operation(summary = "Create a client secret")
    public ResponseEntity<ClientSecretCreateResponse> create(@RequestBody ClientSecretCreateRequest request) {
        if(CredentialType.API_KEY != EndpointContext.getProcessData().getCredentialType()) {
            throw new BellaException.ClientSecretException("client_secret_forbidden", org.springframework.http.HttpStatus.FORBIDDEN,
                    "Client secrets cannot create client secrets");
        }
        ClientSecretCreateResponse response = clientSecretService.create(EndpointContext.getApikey(), request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

}
