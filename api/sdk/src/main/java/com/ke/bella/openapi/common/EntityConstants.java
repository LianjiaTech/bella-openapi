package com.ke.bella.openapi.common;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.AUDIO2TEXT;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.AUDIO_DIALOGUE;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.CHAT;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.IMAGES;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.OCR;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.TEXT2SPEECH;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.VIDEO;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory.WEB_TECHNOLOGY;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicEndpoint.COMPLETION_ENDPOINT;

public class EntityConstants {
    public static final String ACTIVE = "active";
    public static final String INACTIVE = "inactive";
    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";
    public static final String PROTECTED = "protected";
    public static final String INNER = "inner";
    public static final String MAINLAND = "mainland";
    public static final String OVERSEAS = "overseas";
    public static final String BASIC_ROLE = "low";
    public static final String MANAGER_ROLE = "console";
    public static final List<String> DATA_DESTINATIONS = ImmutableList.of(PROTECTED, INNER, MAINLAND, OVERSEAS);
    public static final String HIGH = "high";
    public static final String NORMAL = "normal";
    public static final String LOW = "low";
    public static final List<String> CHANNEL_PRIORITY = ImmutableList.of(HIGH, NORMAL, LOW);
    public static final String MODEL = "model";
    public static final String ENDPOINT = "endpoint";
    public static final List<String> ENTITY_TYPES = ImmutableList.of(MODEL, ENDPOINT);
    public static final String SYSTEM = "system";
    public static final String ORG = "org";
    public static final String PERSON = "person";
    public static final String PROJECT = "project";
    public static final String CONSOLE = "console";
    public static final String ALL = "all";
    public static final List<String> OWNER_TYPES = ImmutableList.of(SYSTEM, ORG, PERSON, PROJECT);
    public static final List<String> AUTHORIZER_TYPES = ImmutableList.of(ORG, PERSON, PROJECT);

    public static final Byte LOWEST_SAFETY_LEVEL = 10;
    public static final Byte HIGHEST_SAFETY_LEVEL = 40;

    @AllArgsConstructor
    @Getter
    public enum SystemBasicEndpoint {
        COMPLETION_ENDPOINT("/v*/chat/completions", "智能问答", CHAT),
        RESPONSES_ENDPOINT("/v*/responses", "智能问答", CHAT),
        MESSAGES_ENDPOINT("/v*/messages", "智能问答", CHAT),
        GEMINI_ENDPOINT("/v1beta/models", "Gemini", CHAT),
        EMBEDDING_ENDPOINT("/v*/embeddings", "向量化", CHAT),
        RERANK_ENDPOINT("/v*/reranks", "重排序", CHAT),
        SPEECH_ENDPOINT("/v*/audio/speech", "语音合成", TEXT2SPEECH),
        REALTIME_TTS_ENDPOINT("/v*/audio/tts/stream", "流式语音合成", TEXT2SPEECH),
        ASR_ENDPOINT("/v*/audio/transcriptions", "语音识别", AUDIO2TEXT),
        ASR_FILE_ENDPOINT("/v*/audio/transcriptions/file", "文件语音识别", AUDIO2TEXT),
        FLASH_ASR_ENDPOINT("/v*/audio/asr/flash", "一句话语音识别", AUDIO2TEXT),
        REALTIME_ASR_ENDPOINT("/v*/audio/asr/stream", "流式语音识别", AUDIO2TEXT),
        REALTIME_AUDIO_ENDPOINT("/v*/audio/realtime", "语音对话", AUDIO_DIALOGUE),
        SPEAKER_EMBEDDING_ENDPOINT("/v*/audio/speaker/embedding", "声纹特征", AUDIO2TEXT),
        SPEAKER_DIARIZATION_ENDPOINT("/v*/audio/speaker/diarization", "说话人分离", AUDIO2TEXT),
        TEXT2IMAGE_ENDPOINT("/v*/images/generations", "文生图", IMAGES),
        IMAGE2IMAGE_ENDPOINT("/v*/images/edits", "图生图", IMAGES),
        IMAGE_VARIATION_ENDPOINT("/v*/images/variations", "图片变体", IMAGES),
        WEB_SEARCH_ENDPOINT("/v*/web/search", "联网搜索", WEB_TECHNOLOGY),
        WEB_CRAWL_ENDPOINT("/v*/web/crawl", "网页抓取", WEB_TECHNOLOGY),
        WEB_EXTRACT_ENDPOINT("/v*/web/extract", "网页提取", WEB_TECHNOLOGY),
        OCR_ENDPOINT("/v*/ocr/*", "OCR", OCR),
        VIDEO_ENDPOINT("/v*/videos", "视频生成", VIDEO);

        private final String endpoint;
        private final String name;
        private final SystemBasicCategory category;
    }

    @AllArgsConstructor
    @Getter
    public enum SystemBasicCategory {
        CHAT("0001", "语言类", null),
        AUDIO("0002", "语音类", null),
        IMAGES("0003", "图像类", null),
        DOCUMENT_PARSE("0004", "文档解析", null),
        PLATFORM_CAPABILITY("0005", "平台能力", null),
        WEB_TECHNOLOGY("0006", "Web技术", null),
        OCR("0007", "OCR", null),
        BATCH_INFERENCE("0008", "批量推理", null),
        VIDEO("0009", "视频类", null),
        TEXT2SPEECH("0002-0001", "语音合成", AUDIO),
        AUDIO2TEXT("0002-0002", "语音识别", AUDIO),
        AUDIO_DIALOGUE("0002-0003", "语音对话", AUDIO),
        KNOWLEDGE_RETRIEVAL("0004-0001", "知识检索", DOCUMENT_PARSE),
        DOCUMENT_PROCESSING("0004-0002", "文档处理", DOCUMENT_PARSE),
        ;

        private final String code;
        private final String name;
        private final SystemBasicCategory parent;
    }

    @AllArgsConstructor
    @Getter
    public enum ModelJsonKey {
        MAX_INPUT(COMPLETION_ENDPOINT.endpoint, "properties", "max_input_context", Integer.class, "最大输入"),
        MAX_OUTPUT(COMPLETION_ENDPOINT.endpoint, "properties", "max_output_context", Integer.class, "最大输出"),
        FUNCTION_CALL(COMPLETION_ENDPOINT.endpoint, "features", "function_call", Boolean.class, "是否支持工具调用"),
        STEAM(COMPLETION_ENDPOINT.endpoint, "features", "stream", Boolean.class, "是否支持流式"),
        STREAM_FUNCTION_CALL(COMPLETION_ENDPOINT.endpoint, "features", "stream_function_call", Boolean.class, "是否支持流式工具调用"),
        PARALLEL_TOOL_CALLS(COMPLETION_ENDPOINT.endpoint, "features", "parallel_tool_calls", Boolean.class, "是否支持并行工具调用"),
        VISION(COMPLETION_ENDPOINT.endpoint, "features", "vision", Boolean.class, "是否支持图片输入"),
        JSON_FORMAT(COMPLETION_ENDPOINT.endpoint, "features", "json_format", Boolean.class, "是否支持json模式"),
        ;

        private final String endpoint;
        private final String fied;
        private final String code;
        private final Class<?> type;
        private final String description;
    }

    @AllArgsConstructor
    @Getter
    public enum Protocol {
        OPENAPI("OpenAIAdaptor"),
        ALI("AliAdaptor"),
        AWS("AwsAdaptor"),
        HUOSHAN("HuoShanAdaptor"),
        ALI_BATCH("AliBatchAdaptor"),
        ALI_COSY_VOICE("AliCosyVoiceAdaptor"),
        ALI_RERANK("AliRerankAdaptor"),
        ANTHROPIC("AnthropicAdaptor"),
        AWS_MESSAGE("AwsMessageAdaptor"),
        BAIDU("BaiduAdaptor"),
        GOOGLE("GoogleAdaptor"),
        HUOSHAN_GENERAL("HuoshanAdaptor"),
        HUOSHAN_V3("HuoShanV3Adaptor"),
        HUOSHAN_BIG_MODEL_FLASH_ASR("HuoshanBigModelFlashAsr"),
        HUOSHAN_EMBEDDING("HuoshanEmbedding"),
        HUOSHAN_JOB_BATCH("HuoshanJobBatchAdaptor"),
        HUOSHAN_LM("HuoshanLMAdaptor"),
        HUOSHAN_REALTIME_TTS("HuoshanRealtimeTtsAdaptor"),
        KE("KeAdaptor"),
        KE_RERANK("KeRerankAdaptor"),
        LARK("LarkAdaptor"),
        MINIMAX("MiniMaxAdaptor"),
        MOCK("MockAdaptor"),
        OPENAI_BATCH("OpenAIBatchAdaptor"),
        OPENAI_RESPONSES("OpenAIResponsesAdaptor"),
        OPENAI_VARIATION("OpenAIVariationAdaptor"),
        PADDLE_OCR("PaddleOCRAdaptor"),
        QWEN("QwenAdaptor"),
        RESPONSES_API("ResponsesApiAdaptor"),
        TAVILY_CRAWL("TavilyCrawlAdaptor"),
        TAVILY_EXTRACT("TavilyExtractAdaptor"),
        TAVILY_SEARCH("TavilySearchAdaptor"),
        TENCENT("TencentAdaptor"),
        VERTEX("VertexAdaptor"),
        VERTEX_BATCH("VertexBatchAdaptor"),
        YIDAO("YidaoAdaptor"),
        ;

        private final String code;
    }

    @AllArgsConstructor
    @Getter
    public enum Supplier {
        Ke("ke", "贝壳"),
        ALI("阿里百炼", "阿里百炼"),
        HUO_SHAN("火山方舟", "火山方舟"),
        AWS("Bedrock", "Bedrock"),
        AZURE("Azure", "Azure");

        private final String code;
        private final String name;
    }

}
