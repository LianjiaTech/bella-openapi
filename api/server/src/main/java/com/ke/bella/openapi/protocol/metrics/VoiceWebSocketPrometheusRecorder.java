package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class VoiceWebSocketPrometheusRecorder {
    private final MeterRegistry meterRegistry;

    public VoiceWebSocketPrometheusRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void opened(EndpointProcessData processData, String direction) {
        if(!VoiceMetricSupport.supports(processData)) {
            return;
        }
        Tags tags = tags(processData, direction);
        counter("bella_voice_ws_opened", tags).increment();
    }

    public void closed(EndpointProcessData processData, String direction, String closeReason, String closeCode) {
        if(!VoiceMetricSupport.supports(processData)) {
            return;
        }
        Tags tags = tags(processData, direction);

        Tags closeTags = tags.and(
                "close_reason", VoiceMetricSupport.labelOrNone(closeReason),
                "close_code", VoiceMetricSupport.labelOrNone(closeCode));
        counter("bella_voice_ws_closed", closeTags).increment();
        if(isAbnormal(closeReason, closeCode)) {
            counter("bella_voice_ws_abnormal_closed", tags).increment();
        }
    }

    private Tags tags(EndpointProcessData processData, String direction) {
        return Tags.of(
                "api_endpoint", VoiceMetricSupport.label(VoiceMetricSupport.endpoint(processData)),
                "direction", VoiceMetricSupport.labelOrNone(direction),
                "protocol", VoiceMetricSupport.labelOrNone(processData.getProtocol()));
    }

    private boolean isAbnormal(String closeReason, String closeCode) {
        if("client_close".equals(closeReason)) {
            return closeCode != null && !"1000".equals(closeCode) && !"1001".equals(closeCode);
        }
        return !"completed".equals(closeReason)
                && !"cancelled".equals(closeReason)
                && !"idle_timeout".equals(closeReason)
                && !"reuse_disabled".equals(closeReason)
                && !"task_finalized_before_upstream_assigned".equals(closeReason);
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }
}
