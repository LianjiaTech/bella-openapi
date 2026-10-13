package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TtsPrometheusRecorder {
    private final MeterRegistry meterRegistry;

    public TtsPrometheusRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void finish(EndpointProcessData processData) {
        String endpoint = VoiceMetricSupport.endpoint(processData);
        if(processData == null || processData.isMock()
                || !("/v1/audio/speech".equals(endpoint) || "/v1/audio/tts/stream".equals(endpoint))) {
            return;
        }
        Tags tags = VoiceMetricSupport.eventTags(processData);
        double inputCharacters = nonNegativeNumber(processData.getUsage());
        if(inputCharacters > 0) {
            counter("bella_tts_input_characters", tags).increment(inputCharacters);
        }
        Map<String, Object> metrics = processData.getMetrics();
        if(metrics == null) {
            return;
        }
        double ttfa = number(metrics.get("ttft_ms"));
        if(ttfa <= 0) {
            ttfa = number(metrics.get("ttft"));
        }
        if(ttfa > 0) {
            summary("bella_tts_ttfa_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(ttfa);
        }
        double frames = number(metrics.get("audio_frames"));
        if(frames > 0) {
            counter("bella_tts_audio_chunks", tags).increment(frames);
        }
        double audioBytes = number(metrics.get("audio_bytes"));
        if(audioBytes > 0) {
            counter("bella_tts_audio_bytes", tags).increment(audioBytes);
        }
        double duration = number(metrics.get("audio_duration_ms"));
        if(duration > 0) {
            counter("bella_tts_audio_duration_milliseconds", tags).increment(duration);
        }
        double firstAudio = number(metrics.get("first_audio_ms"));
        double lastAudio = number(metrics.get("last_audio_ms"));
        double outputWindowMillis = lastAudio - firstAudio;
        if(frames >= 2 && outputWindowMillis > 0) {
            summary("bella_tts_audio_chunk_rate", tags, "chunks_per_second", VoiceMetricSupport.RATE_BUCKETS)
                    .record((frames - 1) * 1000.0 / outputWindowMillis);
            double firstAudioBytes = Math.max(0, number(metrics.get("first_audio_bytes")));
            if(audioBytes > 0 && firstAudioBytes > 0) {
                double bytesAfterFirstChunk = Math.max(0, audioBytes - firstAudioBytes);
                summary("bella_tts_audio_byte_rate", tags, "bytes_per_second", VoiceMetricSupport.BYTE_RATE_BUCKETS)
                        .record(bytesAfterFirstChunk * 1000.0 / outputWindowMillis);
            }
            if(duration > 0) {
                summary("bella_tts_realtime_factor", tags, "ratio", VoiceMetricSupport.RATIO_BUCKETS).record(duration / outputWindowMillis);
            }
        }
    }

    private double nonNegativeNumber(Object value) {
        if(value instanceof Number) {
            return Math.max(0, ((Number) value).doubleValue());
        }
        if(value instanceof Map) {
            return nonNegativeNumber(((Map<?, ?>) value).get("input_characters"));
        }
        return 0;
    }

    private double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private DistributionSummary summary(String name, Tags tags, String baseUnit, double[] buckets) {
        return DistributionSummary.builder(name).baseUnit(baseUnit).serviceLevelObjectives(buckets).tags(tags).register(meterRegistry);
    }
}
