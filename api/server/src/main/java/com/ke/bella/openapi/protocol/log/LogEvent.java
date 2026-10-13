package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import lombok.Data;

@Data
public class LogEvent {
    private EndpointProcessData data;
    private String repositoryCode;
    private boolean costOnly;
}
