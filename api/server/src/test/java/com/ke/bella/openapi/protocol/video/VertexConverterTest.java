package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VertexConverterTest {

    @Test
    void mapsVertexStatusesToBellaVideoStatuses() {
        VertexResponse response = new VertexResponse();
        response.setStatus("in_progress");
        assertEquals(VideoJob.Status.processing.name(),
                VertexConverter.convert("id", response).getStatus());

        response.setStatus("completed");
        assertEquals(VideoJob.Status.completed.name(),
                VertexConverter.convert("id", response).getStatus());

        response.setStatus("cancelled");
        assertEquals(VideoJob.Status.cancelled.name(),
                VertexConverter.convert("id", response).getStatus());
    }

    @Test
    void resolvesUriAndDataVideoOutputs() {
        VertexResponse.Content uriContent = new VertexResponse.Content();
        uriContent.setType("video");
        uriContent.setUri("gs://bucket/video.mp4");
        VertexResponse.Step step = new VertexResponse.Step();
        step.setType("model_output");
        step.setContent(Collections.singletonList(uriContent));
        VertexResponse response = new VertexResponse();
        response.setSteps(Collections.singletonList(step));
        assertEquals("gs://bucket/video.mp4", VertexConverter.resolveVideoUri(response));

        uriContent.setUri(null);
        uriContent.setData("AAAA");
        uriContent.setMimeType("video/mp4");
        assertEquals("data:video/mp4;base64,AAAA", VertexConverter.resolveVideoUri(response));
    }

    @Test
    void ignoresNonVideoOutputs() {
        VertexResponse.Content textContent = new VertexResponse.Content();
        textContent.setType("text");
        VertexResponse.Step step = new VertexResponse.Step();
        step.setType("model_output");
        step.setContent(Collections.singletonList(textContent));
        VertexResponse response = new VertexResponse();
        response.setSteps(Collections.singletonList(step));
        assertNull(VertexConverter.resolveVideoUri(response));
    }

    @Test
    void preservesVertexFailureReason() {
        VertexResponse response = new VertexResponse();
        response.setStatus("failed");
        VertexResponse.ErrorDetail error = new VertexResponse.ErrorDetail();
        error.setCode("invalid_prompt");
        error.setMessage("Prompt violates policy");
        response.setLastError(error);

        ChannelVideoResult result = VertexConverter.convert("id", response);

        assertEquals(VideoJob.Status.failed.name(), result.getStatus());
        assertEquals("invalid_prompt", result.getError().getCode());
        assertEquals("Prompt violates policy", result.getError().getMessage());
    }

    @Test
    void includesContextWhenVertexOmitsFailureReason() {
        VertexResponse response = new VertexResponse();
        response.setId("video-1");
        response.setModel("gemini-omni-1.1-flash-preview");
        response.setObject("interaction");
        response.setStatus("failed");

        ChannelVideoResult result = VertexConverter.convert("video-1", response);

        assertEquals("failed", result.getError().getCode());
        assertEquals("Vertex task failed without error details: id=video-1, model=gemini-omni-1.1-flash-preview, object=interaction, status=failed",
                result.getError().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesNativeUsageFieldsAndTopLevelUnknowns() {
        String json = "{"
                + "\"id\":\"video-1\","
                + "\"model\":\"gemini-omni-1.1-flash-preview\","
                + "\"status\":\"completed\","
                + "\"native_field\":{\"trace\":\"t1\"},"
                + "\"usage\":{"
                + "\"total_input_tokens\":15,"
                + "\"total_output_tokens\":57920,"
                + "\"total_cached_tokens\":2,"
                + "\"total_thought_tokens\":3,"
                + "\"total_tool_use_tokens\":4,"
                + "\"total_tokens\":58278,"
                + "\"grounding_tool_count\":1,"
                + "\"input_tokens_by_modality\":[{\"modality\":\"text\",\"tokens\":15}],"
                + "\"output_tokens_by_modality\":[{\"modality\":\"video\",\"tokens\":57920}],"
                + "\"cached_tokens_by_modality\":[{\"modality\":\"text\",\"tokens\":2}],"
                + "\"tool_use_tokens_by_modality\":[{\"modality\":\"text\",\"tokens\":4}]"
                + "}}";

        VertexResponse response = JacksonUtils.deserialize(json, VertexResponse.class);
        Map<String, Object> body = JacksonUtils.toMap(response);
        Map<String, Object> usage = (Map<String, Object>) body.get("usage");

        assertEquals(15, usage.get("total_input_tokens"));
        assertEquals(57920, usage.get("total_output_tokens"));
        assertEquals(2, usage.get("total_cached_tokens"));
        assertEquals(3, usage.get("total_thought_tokens"));
        assertEquals(4, usage.get("total_tool_use_tokens"));
        assertEquals(58278, usage.get("total_tokens"));
        assertEquals(1, usage.get("grounding_tool_count"));
        assertEquals("t1", ((Map<String, Object>) body.get("native_field")).get("trace"));
        assertEquals(57920, ((Map<String, Object>) ((java.util.List<?>) usage.get("output_tokens_by_modality")).get(0)).get("tokens"));
    }

    @Test
    void convertsVertexUsageModalityTokensForGeminiBilling() {
        VertexResponse response = new VertexResponse();
        response.setStatus("completed");
        VertexResponse.Usage usage = new VertexResponse.Usage();
        usage.setTotalInputTokens(15);
        usage.setTotalOutputTokens(57920);
        usage.setTotalTokens(57935);
        VertexResponse.ModalityTokenCount text = new VertexResponse.ModalityTokenCount();
        text.setModality("TEXT");
        text.setTokenCount(120);
        VertexResponse.ModalityTokenCount video = new VertexResponse.ModalityTokenCount();
        video.setModality("VIDEO");
        video.setTokenCount(57920);
        usage.setOutputTokensByModality(java.util.Arrays.asList(text, video));
        response.setUsage(usage);

        ChannelVideoResult result = VertexConverter.convert("video-1", response);

        assertEquals(Integer.valueOf(15), result.getUsage().getPromptTokens());
        assertEquals(Integer.valueOf(57920), result.getUsage().getCompletionTokens());
        assertEquals(Integer.valueOf(120), result.getUsage().getTextOutputTokens());
        assertEquals(Integer.valueOf(57920), result.getUsage().getVideoOutputTokens());
    }

    @Test
    void ignoresNullVertexModalityTokenCounts() {
        VertexResponse response = new VertexResponse();
        response.setStatus("completed");
        VertexResponse.Usage usage = new VertexResponse.Usage();
        usage.setTotalInputTokens(15);
        usage.setTotalOutputTokens(57920);
        VertexResponse.ModalityTokenCount text = new VertexResponse.ModalityTokenCount();
        text.setModality("TEXT");
        VertexResponse.ModalityTokenCount video = new VertexResponse.ModalityTokenCount();
        video.setModality("VIDEO");
        video.setTokenCount(57920);
        usage.setOutputTokensByModality(java.util.Arrays.asList(text, video));
        response.setUsage(usage);

        ChannelVideoResult result = VertexConverter.convert("video-1", response);

        assertNull(result.getUsage().getTextOutputTokens());
        assertEquals(Integer.valueOf(57920), result.getUsage().getVideoOutputTokens());
    }
}
