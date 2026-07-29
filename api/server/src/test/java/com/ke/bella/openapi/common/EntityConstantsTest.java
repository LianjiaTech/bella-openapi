package com.ke.bella.openapi.common;

import com.ke.bella.openapi.console.MetadataValidator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityConstantsTest {
    @Test
    void systemEndpointsMatchCostCalculatorEndpoints() {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "/v*/chat/completions", "/v*/messages", "/v*/responses", "/v1beta/models", "/v*/embeddings",
                "/v*/reranks", "/v*/audio/speech", "/v*/audio/tts/stream", "/v*/audio/asr/flash",
                "/v*/audio/asr/stream", "/v*/audio/realtime", "/v*/audio/transcriptions",
                "/v*/audio/transcriptions/file", "/v*/audio/speaker/embedding", "/v*/audio/speaker/diarization",
                "/v*/images/generations", "/v*/images/edits", "/v*/images/variations", "/v*/web/search",
                "/v*/web/crawl", "/v*/web/extract", "/v*/ocr/*", "/v*/videos"));

        Set<String> actual = Arrays.stream(EntityConstants.SystemBasicEndpoint.values())
                .map(EntityConstants.SystemBasicEndpoint::getEndpoint)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    @Test
    void systemEndpointPatternsSupportConservativeWildcards() {
        assertTrue(MetadataValidator.matchPath("/v*/ocr/*", "/v1/ocr/general"));
        assertTrue(MetadataValidator.matchPath("/v*/chat/completions", "/v2/chat/completions"));
        assertFalse(MetadataValidator.matchPath("/v*/ocr/*", "/v1/ocr/general/extra"));
        assertFalse(MetadataValidator.matchPath("/v*/ocr/*", "/v1/web/search"));
        assertFalse(MetadataValidator.matchPath("/v*/chat/completions", "/vbeta/chat/completions"));
        assertFalse(MetadataValidator.matchPath("/v*/chat/completions", "/v/chat/completions"));
    }

    @Test
    void systemCategoriesMatchMetadataTable() {
        Map<String, String> expected = new HashMap<>();
        expected.put("0001", "语言类");
        expected.put("0002", "语音类");
        expected.put("0003", "图像类");
        expected.put("0004", "文档解析");
        expected.put("0004-0001", "知识检索");
        expected.put("0002-0003", "语音对话");
        expected.put("0004-0002", "文档处理");
        expected.put("0005", "平台能力");
        expected.put("0006", "Web技术");
        expected.put("0007", "OCR");
        expected.put("0008", "批量推理");
        expected.put("0009", "视频类");
        expected.put("0002-0001", "语音合成");
        expected.put("0002-0002", "语音识别");

        Map<String, String> actual = Arrays.stream(EntityConstants.SystemBasicCategory.values())
                .collect(Collectors.toMap(EntityConstants.SystemBasicCategory::getCode,
                        EntityConstants.SystemBasicCategory::getName));

        assertEquals(expected, actual);
        assertEquals(EntityConstants.SystemBasicCategory.AUDIO,
                EntityConstants.SystemBasicCategory.AUDIO_DIALOGUE.getParent());
        assertEquals(EntityConstants.SystemBasicCategory.DOCUMENT_PARSE,
                EntityConstants.SystemBasicCategory.KNOWLEDGE_RETRIEVAL.getParent());
        assertEquals(EntityConstants.SystemBasicCategory.DOCUMENT_PARSE,
                EntityConstants.SystemBasicCategory.DOCUMENT_PROCESSING.getParent());
    }

    @Test
    void endpointCategoryMappingsUseCurrentMetadata() {
        assertEquals(EntityConstants.SystemBasicCategory.CHAT,
                EntityConstants.SystemBasicEndpoint.RERANK_ENDPOINT.getCategory());
        assertEquals(EntityConstants.SystemBasicCategory.AUDIO_DIALOGUE,
                EntityConstants.SystemBasicEndpoint.REALTIME_AUDIO_ENDPOINT.getCategory());
        assertEquals(EntityConstants.SystemBasicCategory.WEB_TECHNOLOGY,
                EntityConstants.SystemBasicEndpoint.WEB_SEARCH_ENDPOINT.getCategory());
        assertEquals(EntityConstants.SystemBasicCategory.OCR,
                EntityConstants.SystemBasicEndpoint.OCR_ENDPOINT.getCategory());
        assertEquals(EntityConstants.SystemBasicCategory.VIDEO,
                EntityConstants.SystemBasicEndpoint.VIDEO_ENDPOINT.getCategory());
    }

    @Test
    void protocolsCoverRegisteredAdaptorCodes() {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "AliAdaptor", "AliBatchAdaptor", "AliCosyVoiceAdaptor", "AliRerankAdaptor", "AnthropicAdaptor",
                "AwsAdaptor", "AwsMessageAdaptor", "BaiduAdaptor", "GoogleAdaptor", "HuoShanAdaptor",
                "HuoShanV3Adaptor", "HuoshanAdaptor", "HuoshanBigModelFlashAsr", "HuoshanEmbedding",
                "HuoshanJobBatchAdaptor", "HuoshanLMAdaptor", "HuoshanRealtimeTtsAdaptor", "KeAdaptor",
                "KeRerankAdaptor", "LarkAdaptor", "MiniMaxAdaptor", "MockAdaptor", "OpenAIAdaptor",
                "OpenAIBatchAdaptor", "OpenAIResponsesAdaptor", "OpenAIVariationAdaptor", "PaddleOCRAdaptor",
                "QwenAdaptor", "ResponsesApiAdaptor", "TavilyCrawlAdaptor", "TavilyExtractAdaptor",
                "TavilySearchAdaptor", "TencentAdaptor", "VertexAdaptor", "VertexBatchAdaptor", "YidaoAdaptor"));

        Set<String> actual = Arrays.stream(EntityConstants.Protocol.values())
                .map(EntityConstants.Protocol::getCode)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }
}
