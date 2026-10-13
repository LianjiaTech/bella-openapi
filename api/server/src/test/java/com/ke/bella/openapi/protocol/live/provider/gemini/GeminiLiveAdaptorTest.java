package com.ke.bella.openapi.protocol.live.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GeminiLiveAdaptorTest {
    @Test
    public void mapsStandardConfigToGeminiSetup() {
        LiveSessionMessage start = JacksonUtils.deserialize(
                "{\"type\":\"session.start\",\"session\":{\"model\":\"public-model\",\"instructions\":\"be concise\","
                        + "\"modalities\":[\"audio\"],\"voice\":\"Aoede\",\"tools\":[{\"functionDeclarations\":[]}]}}",
                LiveSessionMessage.class);
        LiveSessionProperty property = new LiveSessionProperty();
        property.setModel("publishers/google/models/gemini-live");

        JsonNode provider = JacksonUtils.MAPPER.valueToTree(new GeminiLiveAdaptor().toProvider(start, property));
        JsonNode setup = provider.path("setup");

        assertEquals("publishers/google/models/gemini-live", setup.path("model").asText());
        assertEquals("be concise", setup.path("systemInstruction").path("parts").get(0).path("text").asText());
        assertEquals("AUDIO", setup.path("generationConfig").path("responseModalities").get(0).asText());
        assertEquals("Aoede", setup.path("generationConfig").path("speechConfig").path("voiceConfig")
                .path("prebuiltVoiceConfig").path("voiceName").asText());
        assertFalse(setup.has("payload"));
    }

    @Test
    public void convertsEveryModelPartAndTurnCompletion() {
        JsonNode provider = JacksonUtils.deserialize("{\"serverContent\":{\"modelTurn\":{\"parts\":["
                + "{\"text\":\"hello\"},{\"inlineData\":{\"mimeType\":\"audio/pcm\",\"data\":\"AQID\"}}]},"
                + "\"outputTranscription\":{\"text\":\"spoken\"},\"turnComplete\":true}}", JsonNode.class);

        List<LiveSessionMessage> messages = new GeminiLiveAdaptor().fromProvider(provider);

        assertEquals(4, messages.size());
        assertEquals(LiveSessionEventType.OUTPUT_TRANSCRIPT_DELTA, messages.get(0).eventType());
        assertEquals(LiveSessionEventType.OUTPUT_TEXT_DELTA, messages.get(1).eventType());
        assertEquals(LiveSessionEventType.OUTPUT_AUDIO_DELTA, messages.get(2).eventType());
        assertEquals(LiveSessionEventType.RESPONSE_DONE, messages.get(3).eventType());
    }
}
