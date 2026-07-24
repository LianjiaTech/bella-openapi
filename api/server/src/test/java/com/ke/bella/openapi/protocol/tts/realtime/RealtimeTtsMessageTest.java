package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeTtsMessageTest {
    @Test
    public void serializesSnakeCaseHeaderAndPayload() {
        RealtimeTtsMessage message = RealtimeTtsMessage.audioDelta("sess_1", "task_1", 1, "aud_1",
                RealtimeTtsPayload.builder()
                        .format("pcm")
                        .sampleRate(24000)
                        .channels(1)
                        .encoding("s16le")
                        .build(),
                3200, 100L, false);

        Map<String, Object> map = JacksonUtils.toMap(JacksonUtils.serialize(message));
        Map<String, Object> header = (Map<String, Object>) map.get("header");
        Map<String, Object> payload = (Map<String, Object>) map.get("payload");

        assertEquals("SpeechAudioDelta", header.get("name"));
        assertEquals("task_1", header.get("task_id"));
        assertTrue(header.containsKey("message_id"));
        assertEquals(24000, payload.get("sample_rate"));
        assertEquals(3200, payload.get("byte_length"));
        assertEquals(false, payload.get("is_final"));
    }

    @Test
    public void missingVersionDefaultsToOne() {
        RealtimeTtsMessage message = new RealtimeTtsMessage();
        message.setHeader(RealtimeTtsHeader.builder().name("Ping").build());

        assertEquals(Integer.valueOf(1), message.version());
    }

    @Test
    public void serializesExtraBodyAndProviderMetadataFields() {
        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("additions", new HashMap<String, Object>());
        Map<String, Object> providerMetadata = new HashMap<>();
        providerMetadata.put("raw", true);
        RealtimeTtsPayload payload = RealtimeTtsPayload.builder()
                .extraBody(extraBody)
                .providerMetadata(providerMetadata)
                .build();

        Map<String, Object> map = JacksonUtils.toMap(JacksonUtils.serialize(payload));

        assertTrue(map.containsKey("extra_body"));
        assertTrue(map.containsKey("provider_metadata"));
        assertFalse(map.containsKey("vendor_options"));
    }
}
