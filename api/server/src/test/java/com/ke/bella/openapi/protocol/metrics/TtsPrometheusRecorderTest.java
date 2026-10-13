package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TtsPrometheusRecorderTest {
    private SimpleMeterRegistry registry;
    private TtsPrometheusRecorder recorder;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new TtsPrometheusRecorder(registry);
    }

    @Test
    public void recordsRealtimeTtsInputTtfaChunksRateAndRealtimeFactor() {
        EndpointProcessData processData = processData("tts-realtime", "/v1/audio/tts/stream");
        Map<String, Object> usage = new HashMap<>();
        usage.put("input_characters", 12);
        processData.setUsage(usage);
        processData.getMetrics().put("ttft_ms", 250);
        processData.getMetrics().put("audio_frames", 4);
        processData.getMetrics().put("audio_bytes", 1000);
        processData.getMetrics().put("first_audio_bytes", 400);
        processData.getMetrics().put("first_audio_ms", 2000);
        processData.getMetrics().put("last_audio_ms", 3000);
        processData.getMetrics().put("audio_duration_ms", 1500);

        recorder.finish(processData);

        assertEquals(12, registry.find("bella_tts_input_characters").counter().count(), 0.001);
        assertEquals(250, registry.find("bella_tts_ttfa_milliseconds").summary().totalAmount(), 0.001);
        assertEquals(4, registry.find("bella_tts_audio_chunks").counter().count(), 0.001);
        assertEquals(1000, registry.find("bella_tts_audio_bytes").counter().count(), 0.001);
        assertEquals(1500, registry.find("bella_tts_audio_duration_milliseconds").counter().count(), 0.001);
        assertEquals(3, registry.find("bella_tts_audio_chunk_rate").summary().totalAmount(), 0.001);
        assertEquals(600, registry.find("bella_tts_audio_byte_rate").summary().totalAmount(), 0.001);
        assertEquals(1.5, registry.find("bella_tts_realtime_factor").summary().totalAmount(), 0.001);
    }

    @Test
    public void recordsOfflineInputAndTtfaWithoutInventingChunkMetrics() {
        EndpointProcessData processData = processData("tts-offline", "/v1/audio/speech");
        processData.setUsage(5);
        processData.getMetrics().put("ttft", 100);

        recorder.finish(processData);

        assertEquals(5, registry.find("bella_tts_input_characters").counter().count(), 0.001);
        assertEquals(100, registry.find("bella_tts_ttfa_milliseconds").summary().totalAmount(), 0.001);
        assertNull(registry.find("bella_tts_audio_chunks").counter());
        assertNull(registry.find("bella_tts_audio_bytes").counter());
        assertNull(registry.find("bella_tts_audio_duration_milliseconds").counter());
        assertNull(registry.find("bella_tts_audio_chunk_rate").summary());
        assertNull(registry.find("bella_tts_audio_byte_rate").summary());
        assertNull(registry.find("bella_tts_realtime_factor").summary());
    }

    private EndpointProcessData processData(String requestId, String endpoint) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId(requestId);
        processData.setEndpoint(endpoint);
        processData.setModel("tts-model");
        return processData;
    }
}
