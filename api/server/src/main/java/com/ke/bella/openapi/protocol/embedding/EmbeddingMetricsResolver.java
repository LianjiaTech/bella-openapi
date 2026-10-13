package com.ke.bella.openapi.protocol.embedding;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.metrics.MetricsResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class EmbeddingMetricsResolver implements MetricsResolver {

    @Override
    public Integer resolveUnavailableSeconds(EndpointProcessData processData) {
        return 30;
    }

    @Override
    public List<String> metricsName() {
        return Lists.newArrayList("ttlt", "token");
    }

    @Override
    public String support() {
        return "/v*/embeddings";
    }
}
