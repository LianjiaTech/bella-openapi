package com.ke.bella.openapi.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("bella.openapi")
public class OpenapiProperties {
    public String host;
    public String service;
}
