package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Task;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VertexAdaptorTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildsMinimalVertexRequestWithModelAndBackground() throws Exception {
        VideoCreateRequest request = new VideoCreateRequest();
        request.setModel("bella-model-name");
        request.setPrompt("make the image move");
        request.setInput_reference("gs://bucket/input.png");
        request.setSeconds("5");
        request.setSize("720x1280");

        VertexProperty property = new VertexProperty();
        property.setDeployName("gemini-omni-1.1-flash-preview");

        Method method = VertexAdaptor.class.getDeclaredMethod(
                "buildRequest", VideoCreateRequest.class, VertexProperty.class);
        method.setAccessible(true);
        Object vertexRequest = method.invoke(
                new VertexAdaptor(), request, property);

        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);
        assertEquals("gemini-omni-1.1-flash-preview", body.get("model"));
        assertEquals(true, body.get("background"));
        assertFalse(body.containsKey("prompt"));
        assertFalse(body.containsKey("input_reference"));
        assertFalse(body.containsKey("seconds"));
        assertFalse(body.containsKey("size"));
        assertFalse(body.containsKey("input"));
        assertFalse(body.containsKey("response_format"));
        assertFalse(body.containsKey("store"));
        assertFalse(body.containsKey("generation_config"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsWrappedExtraBodyWithoutParsing() throws Exception {
        String json = "{"
                + "\"model\":\"gemini-omni-1.1-flash-preview\","
                + "\"extra_body\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"make a cinematic video\"},"
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"gs://bucket/frame.png\"}}"
                + "]}}";
        VideoCreateRequest request = JacksonUtils.deserialize(json, VideoCreateRequest.class);

        Object vertexRequest = invokeBuildRequest(request, new VertexProperty());
        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);
        Map<String, Object> extraBody = (Map<String, Object>) body.get("extra_body");
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) extraBody.get("content");

        assertEquals("make a cinematic video", content.get(0).get("text"));
        assertEquals("image_url", content.get(1).get("type"));
        assertEquals("gs://bucket/frame.png", ((Map<String, Object>) content.get(1).get("image_url")).get("url"));
        assertFalse(body.containsKey("input"));
        assertFalse(body.containsKey("generation_config"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTopLevelStringInputUsedByVideoApiClient() throws Exception {
        String prompt = "A marble rolling fast on a chain reaction style track, continuous smooth shot.";
        String json = "{"
                + "\"model\":\"gemini-omni-1.1-flash-preview\","
                + "\"input\":\"" + prompt + "\""
                + "}";
        VideoCreateRequest request = JacksonUtils.deserialize(json, VideoCreateRequest.class);

        Object vertexRequest = invokeBuildRequest(request, new VertexProperty());
        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);

        assertEquals(prompt, body.get("input"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesThroughTopLevelArrayInput() throws Exception {
        String json = "{"
                + "\"model\":\"gemini-omni-1.1-flash-preview\","
                + "\"input\":["
                + "{\"type\":\"text\",\"text\":\"animate this image\"},"
                + "{\"type\":\"image\",\"uri\":\"gs://bucket/frame.png\",\"mime_type\":\"image/png\"}"
                + "]} ";
        VideoCreateRequest request = JacksonUtils.deserialize(json, VideoCreateRequest.class);

        Object vertexRequest = invokeBuildRequest(request, new VertexProperty());
        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);
        java.util.List<Map<String, Object>> input = (java.util.List<Map<String, Object>>) body.get("input");

        assertEquals("animate this image", input.get(0).get("text"));
        assertEquals("image", input.get(1).get("type"));
        assertEquals("gs://bucket/frame.png", input.get(1).get("uri"));
        assertEquals("image/png", input.get(1).get("mime_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotFallbackToPromptWhenExtraBodyContentIsBlank() throws Exception {
        VideoCreateRequest request = new VideoCreateRequest();
        request.setPrompt("fallback prompt");
        Map<String, Object> blankText = new HashMap<>();
        blankText.put("type", "text");
        blankText.put("text", "");
        request.setExtraBodyField("content", Arrays.asList(blankText));

        Object vertexRequest = invokeBuildRequest(request, new VertexProperty());
        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) body.get("content");

        assertEquals(1, content.size());
        assertEquals("", content.get(0).get("text"));
        assertFalse(body.containsKey("input"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesPreviousBellaTaskIdToChannelVideoId() throws Exception {
        QueueClient queueClient = mock(QueueClient.class);
        Task previousTask = mock(Task.class);
        when(queueClient.getTaskDetail(eq("bella-task"), eq("current-ak"))).thenReturn(previousTask);
        Map<String, Object> resultBody = new HashMap<>();
        resultBody.put("channelVideoId", "vertex-interaction");
        when(queueClient.getOutputData(previousTask)).thenReturn(resultBody);

        VertexAdaptor adaptor = newVertexAdaptor(queueClient);
        VideoCreateRequest request = new VideoCreateRequest();
        request.setExtraBodyField("previous_interaction_id", "bella-task");
        request.setExtraBodyField("custom_native_field", "kept");

        adaptor.prepareRequest(request, "current-ak");

        assertEquals("vertex-interaction", request.getExtra_body().get("previous_interaction_id"));
        assertEquals("kept", request.getExtra_body().get("custom_native_field"));
    }

    @Test
    void rejectsMissingPreviousTaskBeforeResolvingOutput() throws Exception {
        QueueClient queueClient = mock(QueueClient.class);
        when(queueClient.getTaskDetail(eq("missing-task"), eq("current-ak"))).thenReturn(null);
        VertexAdaptor adaptor = newVertexAdaptor(queueClient);
        VideoCreateRequest request = new VideoCreateRequest();
        request.setExtraBodyField("previous_interaction_id", "missing-task");

        assertThrows(BellaException.ChannelException.class,
                () -> adaptor.prepareRequest(request, "current-ak"));
    }

    @Test
    void passesThroughNativeVertexFieldsFromExtraBody() throws Exception {
        VideoCreateRequest request = new VideoCreateRequest();
        request.setPrompt("fallback prompt");
        request.setExtraBodyField("metadata", java.util.Collections.singletonMap("trace", "t1"));
        request.setExtraBodyField("system_instruction", "be concise");
        request.setExtraBodyField("response_format", Arrays.asList(
                java.util.Collections.singletonMap("type", "video")));
        request.setExtraBodyField("generation_config", java.util.Collections.singletonMap(
                "video_config", java.util.Collections.singletonMap("task", "text_to_video")));
        request.setExtraBodyField("custom_native_field", "kept");

        Object vertexRequest = invokeBuildRequest(request, new VertexProperty());
        Map<String, Object> body = JacksonUtils.toMap(vertexRequest);

        assertEquals("t1", ((Map<String, Object>) body.get("metadata")).get("trace"));
        assertEquals("be concise", body.get("system_instruction"));
        assertEquals("kept", body.get("custom_native_field"));
        assertEquals("text_to_video", ((Map<String, Object>) ((Map<String, Object>) body.get("generation_config"))
                .get("video_config")).get("task"));
    }

    private VertexAdaptor newVertexAdaptor(QueueClient queueClient) throws Exception {
        VertexAdaptor adaptor = new VertexAdaptor();
        Field field = VertexAdaptor.class.getDeclaredField("queueClient");
        field.setAccessible(true);
        field.set(adaptor, queueClient);
        return adaptor;
    }

    private Object invokeBuildRequest(VideoCreateRequest request,
            VertexProperty property) throws Exception {
        Method method = VertexAdaptor.class.getDeclaredMethod(
                "buildRequest", VideoCreateRequest.class, VertexProperty.class);
        method.setAccessible(true);
        return method.invoke(new VertexAdaptor(), request, property);
    }
}
