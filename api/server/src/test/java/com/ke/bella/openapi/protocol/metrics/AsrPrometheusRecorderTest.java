package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AsrPrometheusRecorderTest {
    private SimpleMeterRegistry registry;
    private AsrPrometheusRecorder recorder;
    private VoiceSessionMetricContext context;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new AsrPrometheusRecorder(registry);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        processData.setModel("asr-model");
        context = new VoiceSessionMetricContext(processData, 1000);
    }

    @Test
    public void recordsPcmDurationAndFirstStandardEvents() {
        recorder.audioInput(context, 32000, "pcm", 16000, 1100);
        recorder.audioInput(context, 32000, "pcm", 16000, 1300);
        RealTimeMessage.Payload partialPayload = new RealTimeMessage.Payload();
        partialPayload.setResult("hello");
        RealTimeMessage.Payload finalPayload = new RealTimeMessage.Payload();
        finalPayload.setResult("hello world");
        recorder.serverMessage(context, RealTimeMessage.resultChange("task", partialPayload), 1200);
        recorder.serverMessage(context, RealTimeMessage.resultChange("task", partialPayload), 1250);
        recorder.serverMessage(context, RealTimeMessage.sentenceEnd("task", finalPayload), 1700);
        recorder.serverMessage(context, RealTimeMessage.sentenceEnd("task", finalPayload), 1700);
        recorder.sessionFinished(context);

        assertEquals(2, registry.find("bella_asr_audio_input_chunks").counter().count(), 0.001);
        assertEquals(64000, registry.find("bella_asr_audio_input_bytes").counter().count(), 0.001);
        assertEquals(2000, registry.find("bella_asr_audio_input_duration_milliseconds").counter().count(), 0.001);
        assertEquals(2, registry.find("bella_asr_result_events").tag("type", "partial").counter().count(), 0.001);
        assertEquals(2, registry.find("bella_asr_result_events").tag("type", "final").counter().count(), 0.001);
        assertEquals(10, registry.find("bella_asr_result_chars").tag("type", "partial").counter().count(), 0.001);
        assertEquals(22, registry.find("bella_asr_result_chars").tag("type", "final").counter().count(), 0.001);
        assertEquals(200, registry.find("bella_asr_first_partial_latency_milliseconds").summary().totalAmount(), 0.001);
        assertEquals(700, registry.find("bella_asr_first_final_latency_milliseconds").summary().totalAmount(), 0.001);
        assertEquals(400, registry.find("bella_asr_final_after_last_audio_milliseconds").summary().totalAmount(), 0.001);
        assertEquals(5, registry.find("bella_asr_audio_input_chunk_rate").summary().totalAmount(), 0.001);
        assertEquals(160000, registry.find("bella_asr_audio_input_byte_rate").summary().totalAmount(), 0.001);
        assertEquals(10, registry.find("bella_asr_audio_ingest_realtime_ratio").summary().totalAmount(), 0.001);
    }

    @Test
    public void recordsCompressedAudioPositionWithoutInventingDuration() {
        recorder.audioInput(context, 32000, "mp3", 16000, 1100);
        recorder.audioInput(context, 32000, "pcm", null, 1200);
        recorder.audioInput(context, 3, "pcm", 16000, 1300);
        recorder.serverMessage(context, RealTimeMessage.sentenceEnd("task", new RealTimeMessage.Payload()), 1500);
        recorder.sessionFinished(context);

        assertEquals(3, registry.find("bella_asr_audio_input_chunks").counter().count(), 0.001);
        assertEquals(64003, registry.find("bella_asr_audio_input_bytes").counter().count(), 0.001);
        assertNull(registry.find("bella_asr_audio_input_duration_milliseconds").counter());
        assertEquals(200, registry.find("bella_asr_final_after_last_audio_milliseconds").summary().totalAmount(), 0.001);
        assertNull(registry.find("bella_asr_audio_ingest_realtime_ratio").summary());
    }
}
