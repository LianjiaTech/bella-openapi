package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VoiceWebSocketPrometheusRecorderTest {
    private SimpleMeterRegistry registry;
    private VoiceWebSocketPrometheusRecorder recorder;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new VoiceWebSocketPrometheusRecorder(registry);
    }

    @Test
    public void recordsWebSocketLifecycleWithBoundedTags() {
        EndpointProcessData processData = processData();

        recorder.opened(processData, "client");
        recorder.opened(processData, "client");
        recorder.closed(processData, "client", "client_close", "1000");
        recorder.closed(processData, "client", "transport_error", null);

        assertEquals(2, registry.find("bella_voice_ws_opened").counter().count(), 0.001);
        assertEquals(2, registry.find("bella_voice_ws_closed")
                .tag("api_endpoint", "/v1/audio/realtime")
                .tag("direction", "client")
                .tag("protocol", "test")
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum(), 0.001);
        assertEquals(1, registry.find("bella_voice_ws_abnormal_closed")
                .tag("api_endpoint", "/v1/audio/realtime")
                .tag("direction", "client")
                .tag("protocol", "test")
                .counter()
                .count(), 0.001);
        assertEquals(1, registry.find("bella_voice_ws_closed")
                .tag("close_reason", "client_close")
                .tag("close_code", "1000")
                .counter()
                .count(), 0.001);
        assertNull(registry.find("bella_voice_ws_opened").counter().getId().getTag("request_id"));
    }

    @Test
    public void ignoresMockRequests() {
        EndpointProcessData processData = processData();
        processData.setMock(true);

        recorder.opened(processData, "client");
        recorder.closed(processData, "client", "client_close", "1000");

        assertNull(registry.find("bella_voice_ws_opened").counter());
        assertNull(registry.find("bella_voice_ws_closed").counter());
        assertNull(registry.find("bella_voice_ws_abnormal_closed").counter());
    }

    private EndpointProcessData processData() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/realtime");
        processData.setProtocol("test");
        return processData;
    }
}
