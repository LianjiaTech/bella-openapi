package com.ke.bella.openapi.clientsecret;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ClientSecretCreateRequest {
    @JsonProperty("expires_in")
    private Integer expiresIn;
    private String clientid;
}
