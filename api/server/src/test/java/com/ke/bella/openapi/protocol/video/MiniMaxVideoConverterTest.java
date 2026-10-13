package com.ke.bella.openapi.protocol.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ke.bella.openapi.utils.JacksonUtils;

public class MiniMaxVideoConverterTest {

    @Test
    public void convertFromMiniMaxQueryMapsNewUsageFields() {
        MiniMaxVideoResponse response = JacksonUtils.deserialize("{"
                + "\"task\":{"
                + "\"id\":\"424010985738629\","
                + "\"status\":\"succeeded\","
                + "\"resolution\":\"2K\","
                + "\"duration\":5,"
                + "\"content\":{\"url\":\"https://example.com/output.mp4\"},"
                + "\"usage\":{"
                + "\"total_seconds\":5,"
                + "\"input_seconds\":0,"
                + "\"output_seconds\":5,"
                + "\"input_image_count\":1,"
                + "\"input_audio_seconds\":6,"
                + "\"total_tokens\":273890,"
                + "\"prompt_tokens\":13500,"
                + "\"completion_tokens\":260390"
                + "}"
                + "}"
                + "}", MiniMaxVideoResponse.class);

        ChannelVideoResult result = MiniMaxVideoConverter.convertFromMiniMaxQuery(response);

        assertEquals(VideoJob.Status.completed.name(), result.getStatus());
        assertEquals(Double.valueOf(5D), result.getUsage().getDuration());
        assertEquals(Double.valueOf(5D), result.getUsage().getTotalSeconds());
        assertEquals(Double.valueOf(0D), result.getUsage().getInputSeconds());
        assertEquals(Double.valueOf(5D), result.getUsage().getOutputSeconds());
        assertEquals(Integer.valueOf(1), result.getUsage().getImageCount());
        assertEquals(Double.valueOf(6D), result.getUsage().getInputAudioSeconds());
        assertEquals(Integer.valueOf(273890), result.getUsage().getTotalTokens());
        assertEquals(Integer.valueOf(13500), result.getUsage().getPromptTokens());
        assertEquals(Integer.valueOf(260390), result.getUsage().getCompletionTokens());

        String usageJson = JacksonUtils.serialize(result.getUsage());
        assertTrue(usageJson.contains("\"input_image_count\":1"));
        assertTrue(usageJson.contains("\"input_audio_seconds\":6.0"));
        assertTrue(usageJson.contains("\"total_seconds\":5.0"));
    }

    @Test
    public void miniMaxResponseContentSupportsPrompt() {
        MiniMaxVideoResponse response = JacksonUtils.deserialize("{"
                + "\"task\":{"
                + "\"id\":\"426586401755526\","
                + "\"status\":\"succeeded\","
                + "\"task_type\":\"h3_context_ir\","
                + "\"modality\":\"text\","
                + "\"content\":{\"prompt\":\"enhanced prompt\"},"
                + "\"usage\":{\"total_tokens\":9090,\"prompt_tokens\":5664,\"completion_tokens\":3426}"
                + "}"
                + "}", MiniMaxVideoResponse.class);

        assertEquals("enhanced prompt", response.getTask().getContent().getPrompt());
        assertEquals(Integer.valueOf(9090), response.getTask().getUsage().getTotalTokens());
    }
}
