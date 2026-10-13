package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class LiveSessionTrace {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOS_PER_MICROSECOND = 1_000L;

    private final AtomicLong startedNanos = new AtomicLong(System.nanoTime());
    private final EventStats clientInput = new EventStats();
    private final EventStats providerRaw = new EventStats();
    private final EventStats providerNormalized = new EventStats();
    private final EventStats clientForward = new EventStats();
    private final AudioStats inputAudio = new AudioStats();
    private final AudioStats providerOutputAudio = new AudioStats();
    private final AudioStats clientOutputAudio = new AudioStats();
    private final TimingStats providerConversion = new TimingStats();
    private final TimingStats providerConnectCall = new TimingStats();
    private final TimingStats upstreamSend = new TimingStats();
    private final TimingStats clientSend = new TimingStats();
    private final TimingStats providerToClient = new TimingStats();
    private final AtomicLong upstreamSendFailures = new AtomicLong();
    private final AtomicLong providerConnectFailures = new AtomicLong();
    private final AtomicLong providerConvertedMessages = new AtomicLong();
    private final AtomicLong clientSendFailures = new AtomicLong();
    private final AtomicLong clientSendSkipped = new AtomicLong();

    void markStarted(long startNanos) {
        startedNanos.set(startNanos);
    }

    void recordClientEvent(LiveSessionMessage message, long receivedNanos) {
        String type = type(message);
        clientInput.record(type, receivedNanos);
        if(message != null && message.eventType() == LiveSessionEventType.INPUT_AUDIO_APPEND) {
            inputAudio.record(receivedNanos, decodedBase64Bytes(message.getAudio()));
        }
    }

    void recordProviderEvent(LiveProviderEvent event) {
        if(event == null) {
            return;
        }
        providerRaw.record(event.getType(), event.getReceivedNanos());
        providerRaw.addPayloadCharacters(event.getPayloadCharacters());
        providerConvertedMessages.addAndGet(event.getNormalizedMessageCount());
        providerConversion.record(event.getConvertedNanos() - event.getReceivedNanos());
    }

    void recordProviderMessage(LiveSessionMessage message, LiveProviderEvent event) {
        long receivedNanos = event == null ? System.nanoTime() : event.getReceivedNanos();
        providerNormalized.record(type(message), receivedNanos);
        if(message != null && message.eventType() == LiveSessionEventType.OUTPUT_AUDIO_DELTA) {
            providerOutputAudio.record(receivedNanos, decodedBase64Bytes(message.getAudio()));
        }
    }

    void recordUpstreamSend(long elapsedNanos, boolean success) {
        upstreamSend.record(elapsedNanos);
        if(!success) {
            upstreamSendFailures.incrementAndGet();
        }
    }

    void recordProviderConnectCall(long elapsedNanos, boolean success) {
        providerConnectCall.record(elapsedNanos);
        if(!success) {
            providerConnectFailures.incrementAndGet();
        }
    }

    void recordClientSend(LiveSessionMessage message, long startedNanos, long completedNanos, long providerReceivedNanos,
            boolean success) {
        clientForward.record(type(message), completedNanos);
        if(message != null && message.eventType() == LiveSessionEventType.OUTPUT_AUDIO_DELTA) {
            clientOutputAudio.record(completedNanos, decodedBase64Bytes(message.getAudio()));
        }
        clientSend.record(completedNanos - startedNanos);
        if(providerReceivedNanos > 0) {
            providerToClient.record(completedNanos - providerReceivedNanos);
        }
        if(!success) {
            clientSendFailures.incrementAndGet();
        }
    }

    void recordClientSendSkipped() {
        clientSendSkipped.incrementAndGet();
    }

    Map<String, Object> snapshot(String requestId, String traceId, String sessionId, String providerSessionId) {
        long snapshotNanos = System.nanoTime();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("schema_version", 1);
        trace.put("request_id", requestId);
        trace.put("trace_id", traceId);
        trace.put("session_id", sessionId);
        trace.put("provider_session_id", providerSessionId);
        trace.put("measurement_duration_ms", nanosToMillis(Math.max(0, snapshotNanos - startedNanos.get())));

        long traceStartedNanos = startedNanos.get();
        Map<String, Object> input = clientInput.snapshot(traceStartedNanos);
        input.put("audio", inputAudio.snapshot(startedNanos.get()));
        input.put("upstream_send", upstreamSend.snapshot());
        input.put("upstream_send_failures", upstreamSendFailures.get());
        trace.put("client_input", input);

        Map<String, Object> provider = providerRaw.snapshot(traceStartedNanos);
        provider.put("converted_message_count", providerConvertedMessages.get());
        provider.put("normalized_event_count", providerNormalized.count());
        provider.put("normalized_event_counts", providerNormalized.countsSnapshot());
        provider.put("normalized_first_event_offsets_ms", providerNormalized.firstOffsetsSnapshot(traceStartedNanos));
        provider.put("normalized_last_event_offsets_ms", providerNormalized.lastOffsetsSnapshot(traceStartedNanos));
        provider.put("connect_call", providerConnectCall.snapshot());
        provider.put("connect_failures", providerConnectFailures.get());
        provider.put("conversion", providerConversion.snapshot());
        provider.put("output_audio", providerOutputAudio.snapshot(startedNanos.get()));
        trace.put("provider", provider);

        Map<String, Object> forward = clientForward.snapshot(traceStartedNanos);
        forward.put("output_audio", clientOutputAudio.snapshot(traceStartedNanos));
        forward.put("send", clientSend.snapshot());
        forward.put("provider_to_client", providerToClient.snapshot());
        forward.put("send_failures", clientSendFailures.get());
        forward.put("send_skipped", clientSendSkipped.get());
        trace.put("client_forward", forward);
        return trace;
    }

    private String type(LiveSessionMessage message) {
        return message == null || message.getType() == null ? "unknown" : message.getType();
    }

    private long decodedBase64Bytes(String value) {
        if(value == null || value.isEmpty()) {
            return 0;
        }
        int padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
        return value.length() * 3L / 4L - padding;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / NANOS_PER_MILLISECOND;
    }

    private static long nanosToMicros(long nanos) {
        return nanos / NANOS_PER_MICROSECOND;
    }

    private static void updateMaximum(AtomicLong maximum, long value) {
        long current = maximum.get();
        while(value > current && !maximum.compareAndSet(current, value)) {
            current = maximum.get();
        }
    }

    private static final class EventStats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong payloadCharacters = new AtomicLong();
        private final ConcurrentHashMap<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> firstNanos = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> lastNanos = new ConcurrentHashMap<>();

        private void record(String type, long eventNanos) {
            String resolvedType = type == null ? "unknown" : type;
            count.incrementAndGet();
            eventCounts.computeIfAbsent(resolvedType, key -> new AtomicLong()).incrementAndGet();
            firstNanos.computeIfAbsent(resolvedType, key -> new AtomicLong()).compareAndSet(0, eventNanos);
            lastNanos.computeIfAbsent(resolvedType, key -> new AtomicLong()).set(eventNanos);
        }

        private void addPayloadCharacters(long characters) {
            payloadCharacters.addAndGet(Math.max(0, characters));
        }

        private long count() {
            return count.get();
        }

        private Map<String, Object> snapshot(long traceStartedNanos) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("event_count", count.get());
            result.put("event_counts", countsSnapshot());
            result.put("first_event_offsets_ms", firstOffsetsSnapshot(traceStartedNanos));
            result.put("last_event_offsets_ms", lastOffsetsSnapshot(traceStartedNanos));
            if(payloadCharacters.get() > 0) {
                result.put("payload_characters", payloadCharacters.get());
            }
            return result;
        }

        private Map<String, Long> countsSnapshot() {
            Map<String, Long> result = new TreeMap<>();
            eventCounts.forEach((key, value) -> result.put(key, value.get()));
            return result;
        }

        private Map<String, Long> firstOffsetsSnapshot(long traceStartedNanos) {
            return offsetsSnapshot(firstNanos, traceStartedNanos);
        }

        private Map<String, Long> lastOffsetsSnapshot(long traceStartedNanos) {
            return offsetsSnapshot(lastNanos, traceStartedNanos);
        }

        private Map<String, Long> offsetsSnapshot(ConcurrentHashMap<String, AtomicLong> source, long traceStartedNanos) {
            Map<String, Long> result = new TreeMap<>();
            source.forEach((key, value) -> result.put(key, nanosToMillis(Math.max(0, value.get() - traceStartedNanos))));
            return result;
        }
    }

    private static final class AudioStats {
        private final AtomicLong chunks = new AtomicLong();
        private final AtomicLong bytes = new AtomicLong();
        private final AtomicLong firstNanos = new AtomicLong();
        private final AtomicLong lastNanos = new AtomicLong();
        private final AtomicLong gapCount = new AtomicLong();
        private final AtomicLong gapTotalNanos = new AtomicLong();
        private final AtomicLong gapMaximumNanos = new AtomicLong();
        private final AtomicLong gapsOver200Millis = new AtomicLong();
        private final AtomicLong gapsOver500Millis = new AtomicLong();

        private void record(long receivedNanos, long audioBytes) {
            chunks.incrementAndGet();
            bytes.addAndGet(Math.max(0, audioBytes));
            firstNanos.compareAndSet(0, receivedNanos);
            long previous = lastNanos.getAndSet(receivedNanos);
            if(previous > 0 && receivedNanos >= previous) {
                long gap = receivedNanos - previous;
                gapCount.incrementAndGet();
                gapTotalNanos.addAndGet(gap);
                updateMaximum(gapMaximumNanos, gap);
                if(gap > 200 * NANOS_PER_MILLISECOND) {
                    gapsOver200Millis.incrementAndGet();
                }
                if(gap > 500 * NANOS_PER_MILLISECOND) {
                    gapsOver500Millis.incrementAndGet();
                }
            }
        }

        private Map<String, Object> snapshot(long traceStartedNanos) {
            Map<String, Object> result = new LinkedHashMap<>();
            long first = firstNanos.get();
            long last = lastNanos.get();
            long gaps = gapCount.get();
            result.put("chunks", chunks.get());
            result.put("bytes", bytes.get());
            result.put("first_offset_ms", first == 0 ? null : nanosToMillis(Math.max(0, first - traceStartedNanos)));
            result.put("last_offset_ms", last == 0 ? null : nanosToMillis(Math.max(0, last - traceStartedNanos)));
            result.put("gap_average_ms", gaps == 0 ? 0 : nanosToMillis(gapTotalNanos.get() / gaps));
            result.put("gap_maximum_ms", nanosToMillis(gapMaximumNanos.get()));
            result.put("gaps_over_200_ms", gapsOver200Millis.get());
            result.put("gaps_over_500_ms", gapsOver500Millis.get());
            return result;
        }
    }

    private static final class TimingStats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maximumNanos = new AtomicLong();
        private final AtomicLong over10Millis = new AtomicLong();
        private final AtomicLong over50Millis = new AtomicLong();
        private final AtomicLong over100Millis = new AtomicLong();

        private void record(long elapsedNanos) {
            long elapsed = Math.max(0, elapsedNanos);
            count.incrementAndGet();
            totalNanos.addAndGet(elapsed);
            updateMaximum(maximumNanos, elapsed);
            if(elapsed > 10 * NANOS_PER_MILLISECOND) {
                over10Millis.incrementAndGet();
            }
            if(elapsed > 50 * NANOS_PER_MILLISECOND) {
                over50Millis.incrementAndGet();
            }
            if(elapsed > 100 * NANOS_PER_MILLISECOND) {
                over100Millis.incrementAndGet();
            }
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            long samples = count.get();
            result.put("count", samples);
            result.put("average_us", samples == 0 ? 0 : nanosToMicros(totalNanos.get() / samples));
            result.put("maximum_us", nanosToMicros(maximumNanos.get()));
            result.put("over_10_ms", over10Millis.get());
            result.put("over_50_ms", over50Millis.get());
            result.put("over_100_ms", over100Millis.get());
            return result;
        }
    }
}
