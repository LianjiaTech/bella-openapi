package com.ke.bella.openapi.server;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BellaServerContext {
    private String ip;
    private Integer port;
    private String applicationName;
}
