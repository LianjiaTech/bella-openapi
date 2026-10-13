package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import io.micrometer.core.instrument.Tags;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class VoiceMetricSupport {
    static final String PUBLIC_ENDPOINT_METRIC = "prometheus_api_endpoint";
    static final String UNKNOWN = "unknown";
    static final String NONE = "none";
    static final Set<String> ENDPOINTS = new HashSet<>(Arrays.asList(
            "/v1/audio/speech",
            "/v1/audio/asr/stream",
            "/v1/audio/realtime",
            "/v1/live/sessions",
            "/v1/audio/asr/flash",
            "/v1/audio/transcriptions",
            "/v1/audio/tts/stream"));
    static final double[] LATENCY_BUCKETS_MS = new double[] { 10, 25, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 30000, 60000,
            120000, 300000 };
    static final double[] RATE_BUCKETS = new double[] { 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000 };
    static final double[] BYTE_RATE_BUCKETS = new double[] { 100, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000,
            500_000, 1_000_000, 5_000_000 };
    static final double[] RATIO_BUCKETS = new double[] { 0.1, 0.25, 0.5, 0.75, 1, 1.5, 2, 3, 5, 10 };

    private VoiceMetricSupport() {
    }

    static boolean supports(EndpointProcessData processData) {
        return processData != null && !processData.isMock() && ENDPOINTS.contains(endpoint(processData));
    }

    static String endpoint(EndpointProcessData processData) {
        if(processData != null && processData.getMetrics() != null) {
            Object endpoint = processData.getMetrics().get(PUBLIC_ENDPOINT_METRIC);
            if(endpoint instanceof String && StringUtils.isNotBlank((String) endpoint)) {
                return (String) endpoint;
            }
        }
        return processData == null ? null : processData.getEndpoint();
    }

    static Tags requestTags(EndpointProcessData processData, String statusClass, int httpStatus, String errorSource) {
        return eventTags(processData).and(
                "forward_host", labelOrNone(processData.getForwardHost()),
                "forward_path", labelOrNone(processData.getForwardPath()),
                "status_class", statusClass,
                "http_status_code", String.valueOf(httpStatus),
                "error_source", errorSource);
    }

    static Tags eventTags(EndpointProcessData processData) {
        return Tags.of(
                "api_endpoint", label(endpoint(processData)),
                "model", metricModel(processData),
                "supplier", labelOrNone(processData.getSupplier()),
                "channel_code", labelOrNone(processData.getChannelCode()),
                "deploy_name", labelOrNone(processData.getDeployName()),
                "protocol", labelOrNone(processData.getProtocol()),
                "stream", String.valueOf(isStream(processData)));
    }

    private static String metricModel(EndpointProcessData processData) {
        return StringUtils.isBlank(processData.getChannelCode()) ? UNKNOWN : label(processData.getModel());
    }

    static OpenapiResponse.OpenapiError error(EndpointProcessData processData) {
        return processData.getResponse() == null ? null : processData.getResponse().getError();
    }

    static int httpStatus(EndpointProcessData processData) {
        OpenapiResponse.OpenapiError error = error(processData);
        if(error != null && error.getHttpCode() != null) {
            return error.getHttpCode();
        }
        Object metricStatus = processData.getMetrics() == null ? null : processData.getMetrics().get("status");
        if("failed".equals(metricStatus)) {
            return 500;
        }
        if("timeout".equals(metricStatus)) {
            return 504;
        }
        if("cancelled".equals(metricStatus)) {
            return 499;
        }
        return 200;
    }

    static String statusClass(EndpointProcessData processData) {
        if(EndpointProcessData.FailureStage.ROUTING.equals(processData.getFailureStage())) {
            return "route_failure";
        }
        Object metricStatus = processData.getMetrics() == null ? null : processData.getMetrics().get("status");
        if("timeout".equals(metricStatus)) {
            return "timeout";
        }
        OpenapiResponse.OpenapiError error = error(processData);
        int httpStatus = httpStatus(processData);
        if(error == null && !"failed".equals(metricStatus) && !"cancelled".equals(metricStatus)) {
            return "success";
        }
        if(httpStatus == 429) {
            return "error_429";
        }
        if(httpStatus == 408 || httpStatus == 504) {
            return "timeout";
        }
        if(httpStatus >= 500 || "failed".equals(metricStatus)) {
            return "error_5xx";
        }
        if(httpStatus >= 400 || "cancelled".equals(metricStatus)) {
            return "error_4xx";
        }
        return UNKNOWN;
    }

    static String errorSource(EndpointProcessData processData) {
        if("success".equals(statusClass(processData))) {
            return NONE;
        }
        return StringUtils.defaultIfBlank(processData.getFailureStage(), UNKNOWN);
    }

    static String label(String value) {
        return StringUtils.defaultIfBlank(value, UNKNOWN);
    }

    static String labelOrNone(String value) {
        return StringUtils.defaultIfBlank(value, NONE);
    }

    static long positiveDiff(long end, long start) {
        return end > 0 && start > 0 && end > start ? end - start : 0;
    }

    private static boolean isStream(EndpointProcessData processData) {
        if("/v1/audio/asr/stream".equals(endpoint(processData)) || "/v1/audio/realtime".equals(endpoint(processData))
                || "/v1/live/sessions".equals(endpoint(processData))
                || "/v1/audio/tts/stream".equals(endpoint(processData))) {
            return true;
        }
        Object request = processData.getRequest();
        if(request == null) {
            return false;
        }
        for (String methodName : new String[] { "isStream", "getStream" }) {
            try {
                Method method = request.getClass().getMethod(methodName);
                Object value = method.invoke(request);
                if(value instanceof Boolean) {
                    return (Boolean) value;
                }
            } catch (Exception ignored) {
                // Request types do not share a stream interface.
            }
        }
        return false;
    }
}
