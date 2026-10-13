package com.ke.bella.openapi.db.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConsoleLogRepoTest {

    @Test
    public void imageLogReductionDoesNotDropWholeResponseWhenBase64ExceedsLimit() {
        ConsoleLogRepo repo = new ConsoleLogRepo();
        setMaxLogSizeBytes(repo, 600);

        ImagesRequest request = new ImagesRequest();
        request.setPrompt("draw");
        request.setModel("gpt-image-1");
        request.setResponse_format("b64_json");

        ImagesResponse.ImageData data = new ImagesResponse.ImageData();
        data.setB64_json(repeat("a", 1200));
        data.setRevised_prompt("revised prompt");
        data.setOutput_format("png");
        data.setQuality("high");
        data.setSize("1024x1024");

        ImagesResponse response = new ImagesResponse();
        response.setCreated(123L);
        response.setData(Collections.singletonList(data));

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/generations");
        processData.setRequest(request);
        processData.setResponse(response);

        String reduced = repo.reduceForLogging(processData, JacksonUtils.serialize(processData));

        assertFalse(reduced.contains(repeat("a", 1200)));
        assertTrue(reduced.contains("[REMOVED: image payload, 1200 bytes]"));
        assertTrue(reduced.contains("revised prompt"));
        assertTrue(reduced.contains("\"output_format\":\"png\""));

        JsonNode root = JacksonUtils.deserialize(reduced);
        assertNotNull(root.get("request"));
        assertNotNull(root.get("response"));
        assertNotNull(root.get("response").get("data"));
    }

    @Test
    public void imageLogReductionFallsBackWhenSanitizedLogStillExceedsLimit() {
        ConsoleLogRepo repo = new ConsoleLogRepo();
        setMaxLogSizeBytes(repo, 600);

        ImagesRequest request = new ImagesRequest();
        request.setPrompt(repeat("p", 2000));
        request.setModel("gpt-image-1");
        request.setResponse_format("b64_json");

        ImagesResponse.ImageData data = new ImagesResponse.ImageData();
        data.setB64_json(repeat("a", 1200));
        data.setRevised_prompt("revised prompt");
        data.setOutput_format("png");
        data.setQuality("high");
        data.setSize("1024x1024");

        ImagesResponse response = new ImagesResponse();
        response.setCreated(123L);
        response.setData(Collections.singletonList(data));

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/generations");
        processData.setRequest(request);
        processData.setResponse(response);

        String reduced = repo.reduceForLogging(processData, JacksonUtils.serialize(processData));

        assertTrue(reduced.getBytes(StandardCharsets.UTF_8).length <= 600);
        assertFalse(reduced.contains(repeat("a", 1200)));
        assertFalse(reduced.contains(repeat("p", 2000)));
        assertTrue(reduced.contains("[REMOVED: Log size exceeded 600 bytes]"));
        assertTrue(reduced.contains("[REMOVED: image payload, 1200 bytes]"));

        JsonNode root = JacksonUtils.deserialize(reduced);
        assertNotNull(root.get("response"));
        assertNotNull(root.get("response").get("data"));
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private void setMaxLogSizeBytes(ConsoleLogRepo repo, int maxLogSizeBytes) {
        try {
            java.lang.reflect.Field field = ConsoleLogRepo.class.getDeclaredField("maxLogSizeBytes");
            field.setAccessible(true);
            field.set(repo, maxLogSizeBytes);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
