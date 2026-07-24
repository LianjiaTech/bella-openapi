package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ChatCompletionPrometheusRecorder {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";
    private static final String MESSAGES_ENDPOINT = "/v1/messages";
    private static final String RESPONSES_ENDPOINT = "/v1/responses";
    private static final Set<String> TEXT_GENERATION_ENDPOINTS = new HashSet<>(
            Arrays.asList(CHAT_COMPLETIONS_ENDPOINT, MESSAGES_ENDPOINT, RESPONSES_ENDPOINT));
    private static final String UNKNOWN = "unknown";
    private static final String NONE = "none";
    private static final long RECORDED_REQUEST_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final double[] TTFT_BUCKETS_MS = new double[] { 500, 1000, 2000, 4000, 8000, 15000, 30000, 45000, 60000, 90000,
            120000, 180000, 300000 };
    private static final double[] LATENCY_BUCKETS_MS = new double[] { 1000, 3000, 10000, 30000, 60000, 120000, 300000 };
    private static final double[] TPS_BUCKETS = new double[] { 5, 10, 20, 40, 80, 160, 320, 640, 1000 };

    private final MeterRegistry meterRegistry;
    private final Map<String, Long> recordedRequests = new ConcurrentHashMap<>();

    public ChatCompletionPrometheusRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void finish(EndpointProcessData processData) {
        if(processData == null || processData.isMock() || !TEXT_GENERATION_ENDPOINTS.contains(processData.getEndpoint())) {
            return;
        }
        long responseMillis = responseMillis(processData);
        if(!markRecorded(processData, responseMillis)) {
            return;
        }
        try {
            if(isRouteFailure(processData)) {
                recordRouteFailure(processData);
                return;
            }
            recordChannelUsage(processData, responseMillis);
        } catch (Exception e) {
            log.warn("record text generation prometheus metric failed, requestId={}", processData.getRequestId(), e);
        }
    }

    private void recordRouteFailure(EndpointProcessData log) {
        int httpStatus = httpStatus(getError(log));
        Tags tags = Tags.of(
                "api_endpoint", label(log.getEndpoint()),
                "model", label(log.getModel()));
        counter("bella_channel_route_failure", tags).increment();
        counter("bella_channel_requests", channelTags(log).and(
                "status_class", "route_failure",
                "http_status_code", String.valueOf(httpStatus))).increment();
    }

    private void recordChannelUsage(EndpointProcessData log, long responseMillis) {
        OpenapiResponse.OpenapiError error = getError(log);
        int httpStatus = httpStatus(error);
        String statusClass = statusClass(error, httpStatus);
        Tags base = channelTags(log);
        counter("bella_channel_requests", base.and(
                "status_class", statusClass,
                "http_status_code", String.valueOf(httpStatus))).increment();

        MetricUsage usage = usage(log);
        int inputTokens = usage.inputTokens;
        int outputTokens = usage.outputTokens;
        int cacheTokens = usage.cacheTokens;
        int reasoningTokens = usage.reasoningTokens;
        String inputBucket = inputTokenBucket(inputTokens);
        String outputBucket = outputTokenBucket(outputTokens);

        long systemLatencyMs = positiveDiff(responseMillis, log.getRequestMillis());
        if(systemLatencyMs > 0) {
            summary("bella_system_ttlt_milliseconds", base.and(
                    "input_token_bucket", inputBucket,
                    "output_token_bucket", outputBucket), LATENCY_BUCKETS_MS).record(systemLatencyMs);
        }

        long channelLatencyMs = positiveDiff(responseMillis, log.getForwardMillis());
        if(channelLatencyMs > 0) {
            summary("bella_channel_ttlt_milliseconds", base.and(
                    "input_token_bucket", inputBucket,
                    "output_token_bucket", outputBucket), LATENCY_BUCKETS_MS).record(channelLatencyMs);
        }

        long firstPackageTime = log.getFirstPackageTime();
        if(firstPackageTime > 0) {
            long systemTtftMs = positiveDiff(firstPackageTime, log.getRequestMillis());
            if(systemTtftMs > 0) {
                summary("bella_system_ttft_milliseconds", base.and("input_token_bucket", inputBucket), TTFT_BUCKETS_MS)
                        .record(systemTtftMs);
            }
            long channelTtftMs = positiveDiff(firstPackageTime, log.getForwardMillis());
            if(channelTtftMs > 0) {
                summary("bella_channel_ttft_milliseconds", base.and("input_token_bucket", inputBucket), TTFT_BUCKETS_MS)
                        .record(channelTtftMs);
            }
            double tps = tps(outputTokens, firstPackageTime, responseMillis);
            if(isStream(log) && tps > 0) {
                summary("bella_chat_completion_tps", base.and("output_token_bucket", outputBucket), TPS_BUCKETS).record(tps);
            }
        }

        recordTokens(base, inputTokens, outputTokens, cacheTokens, reasoningTokens);
    }

    private void recordTokens(Tags base, int inputTokens, int outputTokens, int cacheTokens, int reasoningTokens) {
        incrementCounter("bella_channel_tokens", base.and("type", "input"), inputTokens);
        incrementCounter("bella_channel_tokens", base.and("type", "output"), outputTokens);
        incrementCounter("bella_channel_tokens", base.and("type", "cache"), cacheTokens);
        incrementCounter("bella_channel_tokens", base.and("type", "reasoning"), reasoningTokens);
        incrementCounter("bella_channel_tokens", base.and("type", "total"), inputTokens + outputTokens);
    }

    private void incrementCounter(String name, Tags tags, int amount) {
        if(amount > 0) {
            counter(name, tags).increment(amount);
        }
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private DistributionSummary summary(String name, Tags tags, double[] buckets) {
        return DistributionSummary.builder(name)
                .baseUnit(name.contains("tps") ? "tokens_per_second" : "milliseconds")
                .serviceLevelObjectives(buckets)
                .tags(tags)
                .register(meterRegistry);
    }

    private Tags channelTags(EndpointProcessData log) {
        return Tags.of(
                "api_endpoint", label(log.getEndpoint()),
                "model", label(log.getModel()),
                "supplier", labelOrNone(log.getSupplier()),
                "channel_code", labelOrNone(log.getChannelCode()),
                "deploy_name", labelOrNone(log.getDeployName()),
                "forward_host", labelOrNone(log.getForwardHost()),
                "forward_path", labelOrNone(log.getForwardPath()),
                "protocol", labelOrNone(log.getProtocol()),
                "stream", String.valueOf(isStream(log)));
    }

    private boolean isRouteFailure(EndpointProcessData log) {
        return EndpointProcessData.FAILURE_STAGE_ROUTE.equals(log.getFailureStage());
    }

    private OpenapiResponse.OpenapiError getError(EndpointProcessData log) {
        return log.getResponse() == null ? null : log.getResponse().getError();
    }

    private int httpStatus(OpenapiResponse.OpenapiError error) {
        return error == null || error.getHttpCode() == null ? 200 : error.getHttpCode();
    }

    private String statusClass(OpenapiResponse.OpenapiError error, int httpStatus) {
        if(error == null) {
            return "success";
        }
        if(httpStatus == 429) {
            return "error_429";
        }
        if(httpStatus == 408) {
            return "timeout";
        }
        if(httpStatus >= 500) {
            return "error_5xx";
        }
        if(httpStatus >= 400) {
            return "error_4xx";
        }
        return UNKNOWN;
    }

    private MetricUsage usage(EndpointProcessData log) {
        Object response = log.getResponse();
        if(response instanceof MessageResponse && ((MessageResponse) response).getUsage() != null) {
            return messageUsage(((MessageResponse) response).getUsage());
        }
        if(response instanceof ResponsesApiResponse && ((ResponsesApiResponse) response).getUsage() != null) {
            return responsesUsage(((ResponsesApiResponse) response).getUsage());
        }

        Object usage = log.getUsage();
        if(usage instanceof CompletionResponse.TokenUsage) {
            return completionUsage((CompletionResponse.TokenUsage) usage);
        }
        if(usage instanceof ResponsesApiResponse.Usage) {
            return responsesUsage((ResponsesApiResponse.Usage) usage);
        }
        if(usage instanceof MessageResponse.Usage) {
            return messageUsage((MessageResponse.Usage) usage);
        }
        return new MetricUsage();
    }

    private MetricUsage completionUsage(CompletionResponse.TokenUsage usage) {
        if(usage == null) {
            return new MetricUsage();
        }
        MetricUsage metricUsage = new MetricUsage();
        metricUsage.inputTokens = Math.max(0, usage.getPrompt_tokens());
        metricUsage.outputTokens = Math.max(0, usage.getCompletion_tokens());
        metricUsage.cacheTokens = completionCacheTokens(usage);
        metricUsage.reasoningTokens = completionReasoningTokens(usage);
        return metricUsage;
    }

    private MetricUsage responsesUsage(ResponsesApiResponse.Usage usage) {
        if(usage == null) {
            return new MetricUsage();
        }
        MetricUsage metricUsage = new MetricUsage();
        metricUsage.inputTokens = positive(usage.getInput_tokens());
        metricUsage.outputTokens = positive(usage.getOutput_tokens());
        if(usage.getInput_tokens_details() != null) {
            metricUsage.cacheTokens = positive(usage.getInput_tokens_details().getCached_tokens());
        }
        if(usage.getOutput_tokens_details() != null) {
            metricUsage.reasoningTokens = positive(usage.getOutput_tokens_details().getReasoning_tokens());
        }
        return metricUsage;
    }

    private MetricUsage messageUsage(MessageResponse.Usage usage) {
        if(usage == null) {
            return new MetricUsage();
        }
        MetricUsage metricUsage = new MetricUsage();
        metricUsage.inputTokens = Math.max(0, usage.getTotalInputTokens());
        metricUsage.outputTokens = Math.max(0, usage.getOutputTokens());
        metricUsage.cacheTokens = Math.max(0, usage.getCacheCreationInputTokens()) + Math.max(0, usage.getCacheReadInputTokens());
        return metricUsage;
    }

    private int completionCacheTokens(CompletionResponse.TokenUsage usage) {
        if(usage.getPrompt_tokens_details() != null) {
            return Math.max(0, usage.getPrompt_tokens_details().getCached_tokens())
                    + Math.max(0, usage.getPrompt_tokens_details().getCache_creation_tokens());
        }
        return Math.max(0, usage.getCache_read_tokens()) + Math.max(0, usage.getCache_creation_tokens());
    }

    private int completionReasoningTokens(CompletionResponse.TokenUsage usage) {
        if(usage.getCompletion_tokens_details() == null) {
            return 0;
        }
        return Math.max(0, usage.getCompletion_tokens_details().getReasoning_tokens());
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long responseMillis(EndpointProcessData log) {
        return log.getResponseMillis() > 0 ? log.getResponseMillis() : System.currentTimeMillis();
    }

    private long positiveDiff(long end, long start) {
        return end > 0 && start > 0 && end > start ? end - start : 0;
    }

    private double tps(int outputTokens, long firstPackageTime, long responseMillis) {
        if(outputTokens <= 0 || responseMillis <= firstPackageTime) {
            return 0;
        }
        double seconds = Math.max((responseMillis - firstPackageTime) / 1000.0, 1.0);
        return outputTokens / seconds;
    }

    private String inputTokenBucket(int tokens) {
        if(tokens < 1000) {
            return "0_1k";
        }
        if(tokens < 8000) {
            return "1k_8k";
        }
        if(tokens < 32000) {
            return "8k_32k";
        }
        return "32k_plus";
    }

    private String outputTokenBucket(int tokens) {
        if(tokens < 512) {
            return "0_512";
        }
        if(tokens < 2000) {
            return "512_2k";
        }
        if(tokens < 8000) {
            return "2k_8k";
        }
        return "8k_plus";
    }

    private boolean isStream(EndpointProcessData log) {
        Object request = log.getRequest();
        if(request == null) {
            return false;
        }
        for (String methodName : new String[] { "isStream", "getStream" }) {
            try {
                Method method = request.getClass().getMethod(methodName);
                if(method.getParameterTypes().length == 0) {
                    Object value = method.invoke(request);
                    if(value instanceof Boolean) {
                        return (Boolean) value;
                    }
                }
            } catch (Exception ignored) {
                // Ignore: most endpoints do not expose stream flag.
            }
        }
        return false;
    }

    private boolean markRecorded(EndpointProcessData log, long now) {
        cleanupRecordedRequests(now);
        String key = StringUtils.isNotBlank(log.getRequestId()) ? log.getRequestId() : "identity:" + System.identityHashCode(log);
        return recordedRequests.putIfAbsent(key, now) == null;
    }

    private void cleanupRecordedRequests(long now) {
        recordedRequests.entrySet().removeIf(entry -> now - entry.getValue() > RECORDED_REQUEST_TTL_MILLIS);
    }

    private String labelOrNone(String value) {
        return StringUtils.defaultIfBlank(value, NONE);
    }

    private String label(String value) {
        return StringUtils.defaultIfBlank(value, UNKNOWN);
    }

    private static class MetricUsage {
        private int inputTokens;
        private int outputTokens;
        private int cacheTokens;
        private int reasoningTokens;
    }
}
