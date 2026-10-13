package com.ke.bella.openapi.clientsecret;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSecretCreateResponse {
    @JsonProperty("client_secret")
    @ToString.Exclude
    private String clientSecret;
    private String clientid;
    @JsonProperty("expires_at")
    private long expiresAt;
}
