package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImagesLogHandlerTest {

    private static final String GPT_IMAGE_2_RESPONSE = "{"
            + "\"created\":1786445613,"
            + "\"background\":\"opaque\","
            + "\"data\":[{\"b64_json\":\"raw-output-base64\"}],"
            + "\"output_format\":\"png\","
            + "\"quality\":\"low\","
            + "\"size\":\"1254x1254\","
            + "\"usage\":{"
            + "\"input_tokens\":1040,"
            + "\"input_tokens_details\":{\"image_tokens\":1024,\"text_tokens\":16},"
            + "\"output_tokens\":229,"
            + "\"output_tokens_details\":{\"image_tokens\":229,\"text_tokens\":0},"
            + "\"total_tokens\":1269"
            + "}}";

    @Test
    public void sharedResponseDtoPreservesTopLevelMetadataAndOutputTokenDetails() {
        ImagesResponse response = JacksonUtils.deserialize(GPT_IMAGE_2_RESPONSE, ImagesResponse.class);

        assertNotNull(response);
        assertEquals("png", response.getOutput_format());
        assertEquals("low", response.getQuality());
        assertEquals("1254x1254", response.getSize());
        assertNotNull(response.getUsage().getOutput_tokens_details());
        assertEquals(Integer.valueOf(229), response.getUsage().getOutput_tokens_details().getImage_tokens());
        assertEquals(Integer.valueOf(0), response.getUsage().getOutput_tokens_details().getText_tokens());

        String serialized = JacksonUtils.serialize(response);
        assertTrue(serialized.contains("\"output_format\":\"png\""));
        assertTrue(serialized.contains("\"quality\":\"low\""));
        assertTrue(serialized.contains("\"size\":\"1254x1254\""));
        assertTrue(serialized.contains("\"output_tokens_details\":{\"image_tokens\":229,\"text_tokens\":0}"));
    }

    @Test
    public void sharedResponseMetadataSurvivesSanitizingAcrossLoggedImageEndpoints() {
        for (String endpoint : Arrays.asList("/v1/images/generations", "/v1/images/edits", "/v1/images/variations")) {
            EndpointProcessData processData = new EndpointProcessData();
            processData.setEndpoint(endpoint);
            processData.setResponseRaw(GPT_IMAGE_2_RESPONSE);

            handler(endpoint).process(processData);

            assertFalse(processData.getResponseRaw().contains("raw-output-base64"));
            JsonNode responseRaw = JacksonUtils.deserialize(processData.getResponseRaw());
            assertEquals("png", responseRaw.get("output_format").asText());
            assertEquals("low", responseRaw.get("quality").asText());
            assertEquals("1254x1254", responseRaw.get("size").asText());
            assertEquals(229, responseRaw.get("usage").get("output_tokens_details").get("image_tokens").asInt());
            assertEquals(0, responseRaw.get("usage").get("output_tokens_details").get("text_tokens").asInt());
            ImagesResponse.Usage usage = (ImagesResponse.Usage) processData.getUsage();
            assertEquals(Integer.valueOf(1024), usage.getInput_tokens_details().getImage_tokens());
            assertEquals(Integer.valueOf(16), usage.getInput_tokens_details().getText_tokens());
            assertEquals(Integer.valueOf(229), usage.getOutput_tokens_details().getImage_tokens());
            assertEquals(Integer.valueOf(0), usage.getOutput_tokens_details().getText_tokens());
            assertEquals("low", usage.getQuality());
            assertEquals("1254x1254", usage.getSize());
        }
    }

    @Test
    public void explicitBillingKeepsUsageMetadataBeforeOtherSources() {
        ImagesRequest request = new ImagesRequest();
        request.setQuality("standard");
        request.setSize("512x512");

        ImagesResponse response = imageResponse();
        response.setQuality("low");
        response.setSize("1254x1254");
        response.getData().get(0).setQuality("medium");
        response.getData().get(0).setSize("1024x1536");
        response.getUsage().setQuality("high");
        response.getUsage().setSize("1024x1024");

        EndpointProcessData processData = processData(request, response);
        handler("/v1/images/generations").process(processData);

        ImagesResponse.Usage usage = (ImagesResponse.Usage) processData.getUsage();
        assertEquals("high", usage.getQuality());
        assertEquals("1024x1024", usage.getSize());
    }

    @Test
    public void explicitBillingFallsBackThroughResponseDataRequestAndDefaults() {
        ImagesRequest request = new ImagesRequest();
        request.setQuality("standard");
        request.setSize("512x512");
        ImagesResponse response = imageResponse();
        response.getUsage().setQuality(null);
        response.getUsage().setSize(null);
        response.setQuality("low");
        response.setSize("1254x1254");

        EndpointProcessData responseFallback = processData(request, response);
        handler("/v1/images/generations").process(responseFallback);
        ImagesResponse.Usage responseUsage = (ImagesResponse.Usage) responseFallback.getUsage();
        assertEquals("low", responseUsage.getQuality());
        assertEquals("1254x1254", responseUsage.getSize());

        response.getUsage().setQuality(null);
        response.getUsage().setSize(null);
        response.setQuality(null);
        response.setSize(null);
        EndpointProcessData dataFallback = processData(request, response);
        handler("/v1/images/generations").process(dataFallback);
        ImagesResponse.Usage dataUsage = (ImagesResponse.Usage) dataFallback.getUsage();
        assertEquals("high", dataUsage.getQuality());
        assertEquals("1024x1024", dataUsage.getSize());

        response.getUsage().setQuality(null);
        response.getUsage().setSize(null);
        response.getData().get(0).setQuality(null);
        response.getData().get(0).setSize(null);
        EndpointProcessData requestFallback = processData(request, response);
        handler("/v1/images/generations").process(requestFallback);
        ImagesResponse.Usage requestUsage = (ImagesResponse.Usage) requestFallback.getUsage();
        assertEquals("medium", requestUsage.getQuality());
        assertEquals("512x512", requestUsage.getSize());

        response.getUsage().setQuality(null);
        response.getUsage().setSize(null);
        request.setQuality(null);
        request.setSize(null);
        EndpointProcessData defaultFallback = processData(request, response);
        handler("/v1/images/generations").process(defaultFallback);
        ImagesResponse.Usage defaultUsage = (ImagesResponse.Usage) defaultFallback.getUsage();
        assertEquals("high", defaultUsage.getQuality());
        assertEquals("1024x1024", defaultUsage.getSize());
    }

    @Test
    public void generationLogRemovesBase64ResponsePayloadAndKeepsMetadata() {
        ImagesRequest request = new ImagesRequest();
        request.setPrompt("draw a quiet workspace");
        request.setModel("gpt-image-1");
        request.setN(1);
        request.setSize("1024x1024");
        request.setQuality("high");
        request.setResponse_format("b64_json");

        ImagesResponse response = imageResponse();
        response.getData().get(0).setB64_json("base64-image-payload");

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/generations");
        processData.setRequest(request);
        processData.setResponse(response);

        handler("/v1/images/generations").process(processData);

        String serialized = JacksonUtils.serialize(processData);
        assertFalse(serialized.contains("base64-image-payload"));
        assertTrue(serialized.contains("[REMOVED: image payload, 20 bytes]"));
        assertTrue(serialized.contains("draw a quiet workspace"));
        assertTrue(serialized.contains("revised prompt"));
        assertTrue(serialized.contains("\"output_format\":\"png\""));
        assertTrue(serialized.contains("\"quality\":\"high\""));
        assertTrue(serialized.contains("\"size\":\"1024x1024\""));
        assertNotNull(processData.getUsage());
        assertEquals("base64-image-payload", response.getData().get(0).getB64_json());
    }

    @Test
    public void editsLogRemovesInputImagesAndUrlResponsePayloadButKeepsDetails() {
        ImagesEditRequest request = new ImagesEditRequest();
        request.setPrompt("replace the sky");
        request.setModel("gpt-image-1");
        request.setN(2);
        request.setSize("1024x1024");
        request.setResponse_format("url");
        request.setImage_b64_json(new String[] { "input-base64-image" });
        request.setImage(new MultipartFile[] { multipart(1234) });
        request.setMask(multipart(4321));

        ImagesResponse response = imageResponse();
        response.getData().get(0).setUrl("https://example.test/image.png");

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/edits");
        processData.setRequest(request);
        processData.setResponse(response);

        handler("/v1/images/edits").process(processData);

        String serialized = JacksonUtils.serialize(processData);
        assertFalse(serialized.contains("input-base64-image"));
        assertFalse(serialized.contains("https://example.test/image.png"));
        assertTrue(serialized.contains("replace the sky"));
        assertTrue(serialized.contains("\"model\":\"gpt-image-1\""));
        assertTrue(serialized.contains("\"n\":2"));
        assertTrue(serialized.contains("\"response_format\":\"url\""));
        assertTrue(serialized.contains("[REMOVED: image payload, 18 bytes]"));
        assertTrue(serialized.contains("[REMOVED: image payload, 1234 bytes]"));
        assertTrue(serialized.contains("[REMOVED: image payload, 4321 bytes]"));
        assertTrue(serialized.contains("[REMOVED: image url, 30 bytes]"));
        assertTrue(serialized.contains("revised prompt"));
        assertTrue(serialized.contains("\"total_tokens\":42"));
        assertEquals("https://example.test/image.png", response.getData().get(0).getUrl());
    }

    @Test
    public void rawPayloadsAreSanitizedForImageLogs() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/edits");
        processData.setRequestRaw("{\"prompt\":\"edit\",\"image_b64_json\":[\"raw-input-base64\"],\"mask\":\"raw-mask\"}");
        processData.setResponseRaw("{\"created\":1,\"data\":[{\"b64_json\":\"raw-output-base64\",\"revised_prompt\":\"rp\",\"size\":\"1024x1024\"}]}");

        handler("/v1/images/edits").process(processData);

        String serialized = JacksonUtils.serialize(processData);
        assertFalse(serialized.contains("raw-input-base64"));
        assertFalse(serialized.contains("raw-mask"));
        assertFalse(serialized.contains("raw-output-base64"));
        assertTrue(serialized.contains("[REMOVED: image payload, 16 bytes]"));
        assertTrue(serialized.contains("[REMOVED: image payload, 8 bytes]"));
        assertTrue(serialized.contains("[REMOVED: image payload, 17 bytes]"));

        JsonNode responseRaw = JacksonUtils.deserialize(processData.getResponseRaw());
        assertEquals("rp", responseRaw.get("data").get(0).get("revised_prompt").asText());
    }

    private ImagesLogHandler handler(String endpoint) {
        return new ImagesLogHandler() {
            @Override
            public String endpoint() {
                return endpoint;
            }
        };
    }

    private EndpointProcessData processData(ImagesRequest request, ImagesResponse response) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/images/generations");
        processData.setRequest(request);
        processData.setResponse(response);
        processData.setPriceInfo("{\"billingMode\":\"token\"}");
        return processData;
    }

    private ImagesResponse imageResponse() {
        ImagesResponse.ImageData data = new ImagesResponse.ImageData();
        data.setRevised_prompt("revised prompt");
        data.setOutput_format("png");
        data.setQuality("high");
        data.setSize("1024x1024");

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setNum(1);
        usage.setSize("1024x1024");
        usage.setQuality("high");
        usage.setTotal_tokens(42);

        ImagesResponse response = new ImagesResponse();
        response.setCreated(123L);
        response.setBackground("transparent");
        response.setData(Collections.singletonList(data));
        response.setUsage(usage);
        return response;
    }

    private MultipartFile multipart(long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(size);
        return file;
    }
}
