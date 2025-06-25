package com.ke.bella.openapi.login;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;

@Data
public class LoginProperties {
    private String type;
    private String loginPageUrl;
    private String openapiBase;
    private String authorizationHeader;
}
