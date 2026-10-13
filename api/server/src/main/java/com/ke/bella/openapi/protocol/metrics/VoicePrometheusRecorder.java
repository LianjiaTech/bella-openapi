package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VoicePrometheusRecorder {
    private final MeterRegistry meterRegistry;

    public VoicePrometheusRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void finish(EndpointProcessData processData) {
        if(!VoiceMetricSupport.supports(processData)) {
            return;
        }
        long responseMillis = processData.getResponseMillis() > 0 ? processData.getResponseMillis() : System.currentTimeMillis();
        try {
            String statusClass = VoiceMetricSupport.statusClass(processData);
            int httpStatus = VoiceMetricSupport.httpStatus(processData);
            Tags tags = VoiceMetricSupport.requestTags(processData, statusClass, httpStatus, VoiceMetricSupport.errorSource(processData));
            counter("bella_voice_requests", tags).increment();
            long systemLatency = VoiceMetricSupport.positiveDiff(responseMillis, processData.getRequestMillis());
            if(systemLatency > 0) {
                summary("bella_voice_system_ttlt_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(systemLatency);
            }
            long channelLatency = VoiceMetricSupport.positiveDiff(responseMillis, processData.getForwardMillis());
            if(channelLatency > 0 && !"route_failure".equals(statusClass)) {
                summary("bella_voice_channel_ttlt_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(channelLatency);
            }
        } catch (Exception e) {
            log.warn("record voice prometheus metric failed, requestId={}", processData.getRequestId(), e);
        }
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private DistributionSummary summary(String name, Tags tags, String baseUnit, double[] buckets) {
        return DistributionSummary.builder(name).baseUnit(baseUnit).serviceLevelObjectives(buckets).tags(tags).register(meterRegistry);
    }
}
