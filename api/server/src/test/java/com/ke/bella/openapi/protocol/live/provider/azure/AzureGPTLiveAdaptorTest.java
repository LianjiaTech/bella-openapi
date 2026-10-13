package com.ke.bella.openapi.protocol.live.provider.azure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AzureGPTLiveAdaptorTest {
    @Test
    public void preservesStandardSessionAndOverridesProviderModel() {
        LiveSessionMessage start = JacksonUtils.deserialize(
                "{\"type\":\"session.start\",\"session\":{\"model\":\"public-model\",\"voice\":\"alloy\"}}",
                LiveSessionMessage.class);
        LiveSessionProperty property = new LiveSessionProperty();
        property.setModel("azure-deployment");

        JsonNode provider = JacksonUtils.MAPPER.valueToTree(new AzureGPTLiveAdaptor().toProvider(start, property));

        assertEquals("session.start", provider.path("type").asText());
        assertEquals("azure-deployment", provider.path("session").path("model").asText());
        assertEquals("alloy", provider.path("session").path("voice").asText());
        assertFalse(provider.has("payload"));
    }

    @Test
    public void emitsTopLevelAudio() {
        LiveSessionMessage audio = JacksonUtils.deserialize(
                "{\"type\":\"session.input_audio.append\",\"audio\":\"AQID\"}", LiveSessionMessage.class);

        JsonNode provider = JacksonUtils.MAPPER.valueToTree(new AzureGPTLiveAdaptor().toProvider(audio, null));

        assertEquals("AQID", provider.path("audio").asText());
    }

    @Test
    public void forwardsTextWithoutDelegationId() {
        LiveSessionMessage text = JacksonUtils.deserialize(
                "{\"type\":\"session.input_text.append\",\"text\":\"hello\"}", LiveSessionMessage.class);

        JsonNode provider = JacksonUtils.MAPPER.valueToTree(new AzureGPTLiveAdaptor().toProvider(text, null));

        assertEquals("session.thinking.append", provider.path("type").asText());
        assertEquals("hello", provider.path("content").asText());
        assertFalse(provider.has("delegation_id"));
    }

    @Test
    public void appendsConfiguredAzureApiVersion() {
        LiveSessionProperty property = new LiveSessionProperty();
        property.setApiVersion("2025-10-01-preview");

        String url = new AzureGPTLiveAdaptor().resolveUrl("wss://example.openai.azure.com/openai/v1/live", property);

        assertEquals("wss://example.openai.azure.com/openai/v1/live?api-version=2025-10-01-preview", url);
    }

    @Test
    public void mapsAzureConfirmedSecondsToSessionDuration() {
        JsonNode provider = JacksonUtils.deserialize(
                "{\"type\":\"session.closed\",\"usage\":{\"seconds\":2.0}}", JsonNode.class);

        LiveSessionMessage closed = new AzureGPTLiveAdaptor().fromProvider(provider).get(0);

        assertEquals("session.closed", closed.getType());
        assertEquals(Long.valueOf(2), closed.getUsage().getSessionDurationSeconds());
        assertTrue(closed.getUsage().getFinalUsageConfirmed());
    }

    @Test
    public void doesNotConfirmAzureClosedEventWithoutUsage() {
        JsonNode provider = JacksonUtils.deserialize(
                "{\"type\":\"session.closed\",\"usage\":{}}", JsonNode.class);

        LiveSessionMessage closed = new AzureGPTLiveAdaptor().fromProvider(provider).get(0);

        assertFalse(closed.getUsage().getFinalUsageConfirmed());
    }
}
