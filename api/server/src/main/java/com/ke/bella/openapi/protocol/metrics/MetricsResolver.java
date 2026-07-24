package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;

import java.util.List;

public interface MetricsResolver {
    Integer resolveUnavailableSeconds(EndpointProcessData processData);

    List<String> metricsName();

    String support();
}
