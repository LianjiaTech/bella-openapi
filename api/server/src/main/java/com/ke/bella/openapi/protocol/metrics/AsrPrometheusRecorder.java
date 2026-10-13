package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.protocol.realtime.RealTimeEventType;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AsrPrometheusRecorder {
    private final MeterRegistry meterRegistry;

    public AsrPrometheusRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void audioInput(VoiceSessionMetricContext context, int bytes, String format, Integer sampleRate, long eventMillis) {
        if(context == null || bytes <= 0) {
            return;
        }
        Tags tags = VoiceMetricSupport.eventTags(context.getProcessData());
        counter("bella_asr_audio_input_chunks", tags).increment();
        counter("bella_asr_audio_input_bytes", tags).increment(bytes);

        double durationMillis = audioDurationMillis(bytes, format, sampleRate);
        context.recordAudioInput(eventMillis, bytes, durationMillis);
        if(durationMillis > 0) {
            counter("bella_asr_audio_input_duration_milliseconds", tags).increment(durationMillis);
        }
    }

    public boolean serverMessage(VoiceSessionMetricContext context, RealTimeMessage message, long eventMillis) {
        if(context == null || message == null || message.getHeader() == null) {
            return false;
        }
        RealTimeEventType eventType = RealTimeEventType.fromString(message.getHeader().getName());
        long latency = VoiceMetricSupport.positiveDiff(eventMillis, context.getStartMillis());
        Tags tags = VoiceMetricSupport.eventTags(context.getProcessData());
        if(eventType == RealTimeEventType.TRANSCRIPTION_RESULT_CHANGED) {
            recordResult(message, tags, "partial");
            if(context.getFirstPartialRecorded().compareAndSet(false, true) && latency > 0) {
                summary("bella_asr_first_partial_latency_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(latency);
            }
        }
        if(eventType == RealTimeEventType.SENTENCE_END) {
            recordResult(message, tags, "final");
            context.setLastFinalMillis(eventMillis);
            if(context.getFirstFinalRecorded().compareAndSet(false, true) && latency > 0) {
                summary("bella_asr_first_final_latency_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(latency);
            }
        }
        return eventType == RealTimeEventType.TRANSCRIPTION_COMPLETED;
    }

    public void sessionFinished(VoiceSessionMetricContext context) {
        if(context == null) {
            return;
        }
        long latency = VoiceMetricSupport.positiveDiff(context.getLastFinalMillis(), context.getLastAudioMillis());
        Tags tags = VoiceMetricSupport.eventTags(context.getProcessData());
        if(latency > 0) {
            summary("bella_asr_final_after_last_audio_milliseconds", tags, "milliseconds", VoiceMetricSupport.LATENCY_BUCKETS_MS).record(latency);
        }
        long inputWindowMillis = VoiceMetricSupport.positiveDiff(context.getLastAudioMillis(), context.getFirstAudioMillis());
        if(inputWindowMillis > 0 && context.getAudioInputChunks() >= 2) {
            summary("bella_asr_audio_input_chunk_rate", tags, "chunks_per_second", VoiceMetricSupport.RATE_BUCKETS)
                    .record((context.getAudioInputChunks() - 1) * 1000.0 / inputWindowMillis);
            long bytesAfterFirstChunk = context.getAudioInputBytes() - context.getFirstAudioBytes();
            summary("bella_asr_audio_input_byte_rate", tags, "bytes_per_second", VoiceMetricSupport.BYTE_RATE_BUCKETS)
                    .record(bytesAfterFirstChunk * 1000.0 / inputWindowMillis);
            if(context.getAudioInputDurationMillis() > 0) {
                summary("bella_asr_audio_ingest_realtime_ratio", tags, "ratio", VoiceMetricSupport.RATIO_BUCKETS)
                        .record(context.getAudioInputDurationMillis() / inputWindowMillis);
            }
        }
    }

    private double audioDurationMillis(int bytes, String format, Integer sampleRate) {
        if(sampleRate == null || sampleRate <= 0 || bytes < 2 || bytes % 2 != 0
                || !("pcm".equalsIgnoreCase(format) || "raw".equalsIgnoreCase(format))) {
            return 0;
        }
        return bytes * 1000.0 / (sampleRate * 2.0);
    }

    private void recordResult(RealTimeMessage message, Tags tags, String type) {
        Tags resultTags = tags.and("type", type);
        counter("bella_asr_result_events", resultTags).increment();
        String result = message.getPayload() == null ? null : message.getPayload().getResult();
        if(StringUtils.isNotBlank(result)) {
            counter("bella_asr_result_chars", resultTags).increment(result.length());
        }
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private DistributionSummary summary(String name, Tags tags, String baseUnit, double[] buckets) {
        return DistributionSummary.builder(name).baseUnit(baseUnit).serviceLevelObjectives(buckets).tags(tags).register(meterRegistry);
    }
}
