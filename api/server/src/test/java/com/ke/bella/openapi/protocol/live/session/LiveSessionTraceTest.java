package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import org.junit.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LiveSessionTraceTest {
    @Test
    public void aggregatesProviderAndForwardTimingsByRequest() {
        LiveSessionTrace trace = new LiveSessionTrace();
        long startedNanos = System.nanoTime();
        trace.markStarted(startedNanos);

        LiveSessionMessage input = LiveSessionMessage.event(LiveSessionEventType.INPUT_AUDIO_APPEND, null);
        input.setAudio(Base64.getEncoder().encodeToString(new byte[8192]));
        trace.recordClientEvent(input, startedNanos + millis(100));
        trace.recordClientEvent(input, startedNanos + millis(271));
        trace.recordUpstreamSend(millis(2), true);

        LiveProviderEvent firstProviderEvent = new LiveProviderEvent("session.output_audio.delta", 6500, 1,
                startedNanos + millis(500), startedNanos + millis(502));
        LiveProviderEvent secondProviderEvent = new LiveProviderEvent("session.output_audio.delta", 6500, 1,
                startedNanos + millis(750), startedNanos + millis(754));
        LiveSessionMessage output = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_AUDIO_DELTA, null);
        output.setAudio(Base64.getEncoder().encodeToString(new byte[4800]));
        trace.recordProviderEvent(firstProviderEvent);
        trace.recordProviderMessage(output, firstProviderEvent);
        trace.recordClientSend(output, startedNanos + millis(503), startedNanos + millis(504),
                firstProviderEvent.getReceivedNanos(), true);
        trace.recordProviderEvent(secondProviderEvent);
        trace.recordProviderMessage(output, secondProviderEvent);
        trace.recordClientSend(output, startedNanos + millis(755), startedNanos + millis(758),
                secondProviderEvent.getReceivedNanos(), true);

        Map<String, Object> snapshot = trace.snapshot("request-1", "trace-1", "session-1", "provider-1");

        assertEquals("request-1", snapshot.get("request_id"));
        assertEquals("session-1", snapshot.get("session_id"));
        Map<String, Object> clientInput = map(snapshot.get("client_input"));
        assertEquals(2L, clientInput.get("event_count"));
        Map<String, Object> inputAudio = map(clientInput.get("audio"));
        assertEquals(2L, inputAudio.get("chunks"));
        assertEquals(8192L * 2, inputAudio.get("bytes"));
        assertEquals(171L, inputAudio.get("gap_maximum_ms"));

        Map<String, Object> provider = map(snapshot.get("provider"));
        assertEquals(2L, provider.get("event_count"));
        assertEquals(2L, provider.get("normalized_event_count"));
        Map<String, Object> providerAudio = map(provider.get("output_audio"));
        assertEquals(250L, providerAudio.get("gap_maximum_ms"));

        Map<String, Object> clientForward = map(snapshot.get("client_forward"));
        Map<String, Object> forwardedAudio = map(clientForward.get("output_audio"));
        assertEquals(254L, forwardedAudio.get("gap_maximum_ms"));
        Map<String, Object> providerToClient = map(clientForward.get("provider_to_client"));
        assertEquals(8_000L, providerToClient.get("maximum_us"));
    }

    private long millis(long value) {
        return value * 1_000_000L;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
