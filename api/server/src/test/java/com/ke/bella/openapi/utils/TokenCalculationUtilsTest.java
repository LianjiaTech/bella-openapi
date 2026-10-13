package com.ke.bella.openapi.utils;

import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.rerank.RerankRequest;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCalculationUtilsTest {

    @Test
    void calculateEmbeddingTokensSkipsNonStringInputElements() {
        EmbeddingRequest request = new EmbeddingRequest();
        List<Object> input = new ArrayList<>();
        input.add("hello world");
        Map<String, Object> image = new HashMap<>();
        image.put("type", "image");
        image.put("image_url", "https://example.com/image.png");
        input.add(image);
        request.setInput(input);

        int tokens = assertDoesNotThrow(() ->
                TokenCalculationUtils.calculateEmbeddingTokens(request, EncodingType.CL100K_BASE));

        assertTrue(tokens > 0);
    }

    @Test
    void calculateRerankTokensUsesTextInputs() {
        RerankRequest request = new RerankRequest();
        request.setQuery("family hotel");

        List<String> documents = new ArrayList<>();
        documents.add("near subway");
        documents.add("has kids club");
        request.setDocuments(documents);

        int queryTokens = TokenCalculationUtils.calculateRerankItemTokens(request.getQuery(), EncodingType.CL100K_BASE);
        int documentTokens = documents.stream()
                .mapToInt(document -> TokenCalculationUtils.calculateRerankItemTokens(document, EncodingType.CL100K_BASE))
                .sum();

        int tokens = assertDoesNotThrow(() ->
                TokenCalculationUtils.calculateRerankTokens(request, EncodingType.CL100K_BASE));

        assertEquals(queryTokens * documents.size() + documentTokens, tokens);
    }
}
