package com.ke.bella.openapi.protocol.metrics;

import lombok.Data;
import java.util.Map;

@Data
public class MetricsQueryResult {
    private String channelCode;
    private String entityCode;
    private String endpoint;
    private Map<String, Object> metrics;
}
