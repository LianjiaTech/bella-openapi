package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class VoiceSessionMetricContext {
    private final EndpointProcessData processData;
    private final long startMillis;
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final AtomicBoolean firstPartialRecorded = new AtomicBoolean(false);
    private final AtomicBoolean firstFinalRecorded = new AtomicBoolean(false);
    @Setter
    private long lastAudioMillis;
    @Setter
    private long lastFinalMillis;
    private long firstAudioMillis;
    private long firstAudioBytes;
    private long audioInputChunks;
    private long audioInputBytes;
    private double audioInputDurationMillis;

    public VoiceSessionMetricContext(EndpointProcessData processData, long startMillis) {
        this.processData = processData;
        this.startMillis = startMillis;
    }

    public void recordAudioInput(long eventMillis, int bytes, double durationMillis) {
        if(firstAudioMillis <= 0) {
            firstAudioMillis = eventMillis;
            firstAudioBytes = bytes;
        }
        lastAudioMillis = eventMillis;
        audioInputChunks++;
        audioInputBytes += bytes;
        if(durationMillis > 0) {
            audioInputDurationMillis += durationMillis;
        }
    }
}
