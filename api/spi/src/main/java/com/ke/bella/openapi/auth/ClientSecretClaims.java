package com.ke.bella.openapi.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Claims carried by a client-secret JWT. {@code sub} is the ak_code of the source API key;
 * the token never contains the full API key or a snapshot of authorized paths.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientSecretClaims {
    private String iss;
    private String aud;
    private String sub;
    private String clientid;
    private long iat;
    private long nbf;
    private long exp;
    private String jti;
}
