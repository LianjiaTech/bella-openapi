package com.ke.bella.openapi.login.session;

import lombok.Data;

@Data
public class SessionProperty {
    private String sessionPrefix;
    private String cookieName;
    private Integer maxInactiveInterval;
    private Integer cookieMaxAge;
    private String cookieDomain;
    private String cookieContextPath;
}
