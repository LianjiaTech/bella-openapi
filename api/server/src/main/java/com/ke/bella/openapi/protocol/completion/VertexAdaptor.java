package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component("VertexCompletion")
public class VertexAdaptor implements CompletionAdaptor<VertexProperty> {

    @Override
    public String getDescription() {
        return "Vertex AI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return VertexProperty.class;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, VertexProperty property) {
        // Build Vertex AI URL
        String vertexUrl = buildVertexUrl(url, false);

        // Convert request to Gemini format
        GeminiRequest geminiRequest = VertexConverter.convertToVertexRequest(request, property);

        // Build HTTP request
        Request httpRequest = buildHttpRequest(vertexUrl, geminiRequest, property);
        clearLargeData(request, geminiRequest);

        // Use HttpUtils for request
        GeminiResponse geminiResponse = HttpUtils.httpRequest(httpRequest, GeminiResponse.class);

        // Convert back to OpenAI format
        return VertexConverter.convertToOpenAIResponse(geminiResponse);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, VertexProperty property,
            Callbacks.StreamCompletionCallback callback) {
        // Build Vertex AI streaming URL
        String vertexUrl = buildVertexUrl(url, true);

        // Convert request to Gemini format
        GeminiRequest geminiRequest = VertexConverter.convertToVertexRequest(request, property);

        // Build HTTP request
        Request httpRequest = buildHttpRequest(vertexUrl, geminiRequest, property);
        clearLargeData(request, geminiRequest);

        // Create SSE converter and listener
        VertexSseConverter sseConverter = new VertexSseConverter(property.getDeployName());
        CompletionSseListener listener = new CompletionSseListener(callback, sseConverter);

        // Use HttpUtils for streaming request
        HttpUtils.streamRequest(httpRequest, listener);
    }

    private String buildVertexUrl(String baseUrl, boolean isStreaming) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);

        if(isStreaming) {
            urlBuilder.append(":streamGenerateContent?alt=sse");
        } else {
            urlBuilder.append(":generateContent");
        }

        return urlBuilder.toString();
    }

    private Request buildHttpRequest(String url, GeminiRequest geminiRequest, VertexProperty property) {
        byte[] requestBytes = JacksonUtils.toByte(geminiRequest);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestBytes);

        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json");

        // Add extra headers if configured
        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::header);
        }

        return builder.build();
    }

    /**
     * Vertex AI Gemini SSE 事件转换器
     * 将 Gemini API 的 SSE 事件转换为 Chat Completion 流式响应格式
     */
    @Slf4j
    public static class VertexSseConverter implements Callbacks.SseEventConverter<StreamCompletionResponse> {

        private final long created;

        public VertexSseConverter(String model) {
            this.created = DateTimeUtils.getCurrentSeconds();
        }

        @Override
        public StreamCompletionResponse convert(String eventId, String eventType, String eventData) {
            try {
                // Gemini API 流式响应直接是 JSON 格式
                GeminiResponse geminiResponse = JacksonUtils.deserialize(eventData, GeminiResponse.class);
                if(geminiResponse != null) {
                    return VertexConverter.convertGeminiToStreamResponse(geminiResponse, created);
                }
                return null;
            } catch (Exception e) {
                log.error("Error processing Vertex AI stream event: {}", eventData, e);
                return null;
            }
        }
    }
}
