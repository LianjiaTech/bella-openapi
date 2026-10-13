package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HuoshanEmbeddingTest {

    private final HuoshanEmbedding adaptor = new HuoshanEmbedding();

    @Test
    void buildRequestForwardsMultimodalInputAndProviderFields() throws IOException {
        EmbeddingRequest request = JacksonUtils.deserialize("{"
                + "\"model\":\"doubao-embedding-vision\","
                + "\"input\":["
                + "{\"type\":\"text\",\"text\":\"describe the room\"},"
                + "{\"type\":\"image\",\"image_url\":{\"url\":\"https://example.com/room.png\"}},"
                + "{\"type\":\"video\",\"video_url\":{\"url\":\"https://example.com/room.mp4\"}}"
                + "],"
                + "\"instructions\":\"return retrieval embeddings\","
                + "\"sparse_embedding\":{\"type\":\"enabled\"},"
                + "\"multi_embedding\":{\"type\":\"enabled\",\"compression\":\"zstd\"},"
                + "\"dimensions\":2048,"
                + "\"video_frame_interval\":2"
                + "}", EmbeddingRequest.class);

        HuoshanEmbeddingProperty property = property("ark-deploy-name");
        Request httpRequest = adaptor.buildRequest(request,
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                property);

        assertEquals("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal", httpRequest.url().toString());
        assertEquals("POST", httpRequest.method());
        assertEquals("Bearer test-api-key", httpRequest.header("Authorization"));

        Map<String, Object> body = requestBodyAsMap(httpRequest);
        assertEquals("ark-deploy-name", body.get("model"));
        assertEquals("return retrieval embeddings", body.get("instructions"));
        assertEquals("enabled", ((Map<?, ?>) body.get("sparse_embedding")).get("type"));
        assertEquals("enabled", ((Map<?, ?>) body.get("multi_embedding")).get("type"));
        assertEquals("zstd", ((Map<?, ?>) body.get("multi_embedding")).get("compression"));
        assertEquals(2048, body.get("dimensions"));
        assertEquals(2, body.get("video_frame_interval"));

        List<?> input = (List<?>) body.get("input");
        assertEquals(3, input.size());
        assertEquals("text", ((Map<?, ?>) input.get(0)).get("type"));
        assertEquals("image", ((Map<?, ?>) input.get(1)).get("type"));
        assertEquals("video", ((Map<?, ?>) input.get(2)).get("type"));
    }

    @Test
    void buildRequestKeepsRequestModelWhenDeployNameIsBlank() throws IOException {
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel("doubao-embedding-vision");
        request.setInput(multimodalInput());

        Request httpRequest = adaptor.buildRequest(request,
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                property(null));

        Map<String, Object> body = requestBodyAsMap(httpRequest);
        assertEquals("doubao-embedding-vision", body.get("model"));
    }

    @Test
    void parseEmbeddingResponseWithUsageAndProviderFields() {
        EmbeddingResponse response = JacksonUtils.deserialize("{"
                + "\"id\":\"021234\","
                + "\"created\":1710000000,"
                + "\"object\":\"list\","
                + "\"model\":\"doubao-embedding-vision\","
                + "\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1,0.2,0.3],\"index\":0,"
                + "\"sparse_embedding\":[{\"index\":1,\"value\":0.5}],"
                + "\"multi_embedding\":[[0.1,0.2],[0.3,0.4]]}],"
                + "\"usage\":{\"prompt_tokens\":17,\"total_tokens\":17,"
                + "\"prompt_tokens_details\":{\"text_tokens\":5,\"image_tokens\":12}}"
                + "}", EmbeddingResponse.class);

        assertNotNull(response);
        assertEquals("021234", response.getId());
        assertEquals(1710000000, response.getCreated());
        assertEquals("list", response.getObject());
        assertEquals("doubao-embedding-vision", response.getModel());
        assertEquals(1, response.getData().size());
        assertEquals(0, response.getData().get(0).getIndex());
        assertNotNull(response.getData().get(0).getSparseEmbedding());
        assertNotNull(response.getData().get(0).getMultiEmbedding());
        assertEquals(17, response.getUsage().getPrompt_tokens());
        assertEquals(17, response.getUsage().getTotal_tokens());
        assertNotNull(response.getUsage().getPrompt_tokens_details());
    }

    @Test
    void convertSingleObjectDataResponseToEmbeddingDataList() {
        HuoshanEmbedding.HuoshanEmbeddingResponse huoshanResponse = JacksonUtils.deserialize("{"
                + "\"created\":1782267021,"
                + "\"data\":{\"embedding\":[0.1,0.2,0.3]},"
                + "\"model\":\"doubao-embedding-vision\","
                + "\"object\":\"list\","
                + "\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}"
                + "}", HuoshanEmbedding.HuoshanEmbeddingResponse.class);

        EmbeddingResponse response = adaptor.convertResponse(huoshanResponse);

        assertNotNull(response);
        assertEquals("list", response.getObject());
        assertEquals("doubao-embedding-vision", response.getModel());
        assertEquals(1, response.getData().size());
        assertEquals("embedding", response.getData().get(0).getObject());
        assertEquals(0, response.getData().get(0).getIndex());
        assertEquals(5, response.getUsage().getPrompt_tokens());
        assertEquals(5, response.getUsage().getTotal_tokens());
    }

    private HuoshanEmbeddingProperty property(String deployName) {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthType.BEARER);
        auth.setApiKey("test-api-key");

        HuoshanEmbeddingProperty property = new HuoshanEmbeddingProperty();
        property.setAuth(auth);
        property.setDeployName(deployName);
        return property;
    }

    private List<Map<String, Object>> multimodalInput() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "hello");
        input.add(text);
        return input;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBodyAsMap(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return JacksonUtils.deserialize(buffer.readUtf8(), Map.class);
    }
}
