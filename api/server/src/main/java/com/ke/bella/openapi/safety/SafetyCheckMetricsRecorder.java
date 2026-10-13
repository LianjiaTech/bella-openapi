package com.ke.bella.openapi.safety;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

@Component
public class SafetyCheckMetricsRecorder {
    static final String SAFETY_CHECK_LATENCY_METRIC = "bella_safety_check_latency_milliseconds";
    static final String SAFETY_CHECK_STREAM_LATENCY_METRIC = "bella_safety_check_stream_latency_milliseconds";

    private static final String UNKNOWN = "unknown";
    private static final double[] LATENCY_BUCKETS_MS = new double[] { 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 30000 };

    private static volatile MeterRegistry meterRegistry;

    public SafetyCheckMetricsRecorder(MeterRegistry meterRegistry) {
        SafetyCheckMetricsRecorder.meterRegistry = meterRegistry;
    }

    static void recordCheckLatency(SafetyCheckRequest request, String mode, String status, long startNanos) {
        MeterRegistry registry = meterRegistry;
        if(registry == null) {
            return;
        }
        Tags tags = Tags.of(
                "type", safetyType(request),
                "status", label(status),
                "mode", label(mode));
        summary(registry, SAFETY_CHECK_LATENCY_METRIC, tags).record(elapsedMillis(startNanos));
    }

    static void recordStreamLatency(boolean done, long startNanos) {
        MeterRegistry registry = meterRegistry;
        if(registry == null) {
            return;
        }
        Tags tags = Tags.of("done", String.valueOf(done));
        summary(registry, SAFETY_CHECK_STREAM_LATENCY_METRIC, tags).record(elapsedMillis(startNanos));
    }

    private static DistributionSummary summary(MeterRegistry registry, String name, Tags tags) {
        return DistributionSummary.builder(name)
                .baseUnit("milliseconds")
                .serviceLevelObjectives(LATENCY_BUCKETS_MS)
                .tags(tags)
                .register(registry);
    }

    private static double elapsedMillis(long startNanos) {
        return Math.max(0D, (System.nanoTime() - startNanos) / (double) TimeUnit.MILLISECONDS.toNanos(1));
    }

    private static String safetyType(SafetyCheckRequest request) {
        if(request == null) {
            return UNKNOWN;
        }
        if(StringUtils.isNotBlank(request.getType())) {
            return request.getType();
        }
        return request.isRequest() ? "input" : "output";
    }

    private static String label(String value) {
        return StringUtils.defaultIfBlank(value, UNKNOWN);
    }
}
