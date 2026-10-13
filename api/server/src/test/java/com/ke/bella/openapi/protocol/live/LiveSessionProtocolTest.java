package com.ke.bella.openapi.protocol.live;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LiveSessionProtocolTest {
    @Test
    public void setupAliasIsAcceptedAsClientStartEvent() {
        LiveSessionMessage message = JacksonUtils.deserialize("{\"type\":\"setup\",\"model\":\"gemini-live\"}", LiveSessionMessage.class);

        assertNotNull(message);
        assertEquals(LiveSessionEventType.SETUP, message.eventType());
        assertTrue(message.eventType().isClientEvent());
    }

    @Test
    public void standardSessionStartResolvesNestedModel() {
        LiveSessionMessage message = JacksonUtils.deserialize(
                "{\"type\":\"session.start\",\"session\":{\"model\":\"gpt-live-1\",\"voice\":\"alloy\"}}",
                LiveSessionMessage.class);

        assertEquals("gpt-live-1", message.resolveModel());
        assertEquals("alloy", message.resolveSessionConfig().get("voice"));
    }

    @Test
    public void standardTopLevelAudioIsAccepted() {
        LiveSessionMessage message = JacksonUtils.deserialize(
                "{\"type\":\"session.input_audio.append\",\"audio\":\"AQID\"}", LiveSessionMessage.class);

        assertEquals("AQID", message.payloadValue("audio"));
    }

    @Test
    public void errorUsesStableEnvelopeFields() {
        LiveSessionMessage message = LiveSessionMessage.error("live_1", "invalid_event", "bad event");
        Map<String, Object> serialized = JacksonUtils.toMap(JacksonUtils.serialize(message));

        assertEquals("error", serialized.get("type"));
        assertEquals("live_1", serialized.get("session_id"));
        assertTrue(serialized.containsKey("error"));
    }

    @Test
    public void cumulativeUsageUsesMonotonicMaximum() {
        LiveSessionUsage usage = new LiveSessionUsage();
        usage.setInputTextTokens(10L);
        LiveSessionUsage olderSnapshot = new LiveSessionUsage();
        olderSnapshot.setInputTextTokens(8L);
        olderSnapshot.setOutputAudioTokens(4L);

        usage.mergeMaximum(olderSnapshot);

        assertEquals(Long.valueOf(10), usage.getInputTextTokens());
        assertEquals(Long.valueOf(4), usage.getOutputAudioTokens());
    }
}
