package com.ke.bella.openapi.login;

import lombok.Data;

@Data
public class LoginProperties {
    private String type;
    private String loginPageUrl;
    private String openapiBase;
    private String authorizationHeader = "Authorization";
}
