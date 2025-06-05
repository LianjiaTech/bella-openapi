package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Gemini适配器
 */
@Component("GeminiCompletion")
public class GeminiAdaptor implements CompletionAdaptorDelegator<GeminiProperty> {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAdaptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<String, Client> clientCache = new ConcurrentHashMap<>();

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, GeminiProperty property,
            com.ke.bella.openapi.protocol.Callbacks.HttpDelegator delegator) {
        return completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, GeminiProperty property,
            StreamCompletionCallback callback, com.ke.bella.openapi.protocol.Callbacks.StreamDelegator delegator) {
        streamCompletion(request, url, property, callback);
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, GeminiProperty property) {
        try {
            Client client = getClient(property);
            String userMessage = extractUserMessage(request);
            GenerateContentConfig config = buildConfig(request);
            GenerateContentResponse response = client.models.generateContent(property.getDeployName(), userMessage, config);

            CompletionResponse result = buildCompletionResponse(response, request.getModel());
            ResponseHelper.splitReasoningFromContent(result, property);
            return result;
        } catch (Exception e) {
            logger.error("Gemini调用失败", e);
            return buildErrorResponse(e.getMessage());
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, GeminiProperty property, StreamCompletionCallback callback) {
        try {
            Client client = getClient(property);
            String userMessage = extractUserMessage(request);
            GenerateContentConfig config = buildConfig(request);

            try (ResponseStream<GenerateContentResponse> responseStream =
                    client.models.generateContentStream(property.getDeployName(), userMessage, config)) {

                for (GenerateContentResponse response : responseStream) {
                    String content = response.text();
                    if (StringUtils.isNotBlank(content)) {
                        callback.callback(buildStreamResponse(content, request.getModel()));
                    }
                }
                callback.callback(buildFinishResponse(request.getModel()));
            }
            callback.finish();
        } catch (Exception e) {
            logger.error("Gemini流式调用失败", e);
            callback.callback(StreamCompletionResponse.builder().error(buildError(e.getMessage())).build());
            callback.finish();
        }
    }

    /**
     * 获取客户端（带缓存）
     */
    private Client getClient(GeminiProperty property) throws IOException {
        validateProperty(property);
        String cacheKey = getCacheKey(property);

        return clientCache.computeIfAbsent(cacheKey, k -> {
            try {
                String jsonCreds = property.getAuth().getJsonCredentials();
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(jsonCreds.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));

                return Client.builder()
                    .vertexAI(true)
                    .project(extractProjectId(jsonCreds))
                    .location(StringUtils.defaultIfBlank(property.getLocation(), "us-central1"))
                    .credentials(credentials)
                    .build();
            } catch (Exception e) {
                throw new RuntimeException("创建Gemini客户端失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 验证配置并提取用户消息
     */
    private void validateProperty(GeminiProperty property) {
        if (property.getAuth() == null || property.getAuth().getType() != AuthorizationProperty.AuthType.GOOGLE_JSON
            || StringUtils.isBlank(property.getAuth().getJsonCredentials())
            || StringUtils.isBlank(property.getDeployName())) {
            throw new IllegalArgumentException("配置无效：需要GOOGLE_JSON认证类型、JSON凭据和模型名称");
        }
    }

    private String getCacheKey(GeminiProperty property) {
        return property.getLocation() + ":" + property.getAuth().getJsonCredentials().hashCode();
    }

    private String extractProjectId(String jsonCreds) throws IOException {
        JsonNode json = objectMapper.readTree(jsonCreds);
        if (json.has("project_id")) {
            return json.get("project_id").asText();
        }
        if (json.has("client_email")) {
            String[] parts = json.get("client_email").asText().split("@");
            if (parts.length > 1) {
                return parts[1].split("\\.")[0];
            }
        }
        throw new IllegalArgumentException("无法从JSON凭据中提取project_id");
    }

    private String extractUserMessage(CompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("消息列表为空");
        }
        return request.getMessages().stream()
            .filter(msg -> "user".equals(msg.getRole()) && msg.getContent() != null)
            .reduce((first, second) -> second)
            .map(msg -> msg.getContent().toString())
            .orElseThrow(() -> new IllegalArgumentException("未找到用户消息"));
    }

    private GenerateContentConfig buildConfig(CompletionRequest request) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        if (request.getMax_tokens() != null && request.getMax_tokens() > 0) {
            builder.maxOutputTokens(request.getMax_tokens());
        }
        if (request.getN() != null && request.getN() > 0) {
            builder.candidateCount(request.getN());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature().floatValue());
        }
        if (request.getTop_p() != null) {
            builder.topP(request.getTop_p().floatValue());
        }
        return builder.build();
    }

    private CompletionResponse buildCompletionResponse(GenerateContentResponse response, String model) {
        String content = response.text();
        long timestamp = DateTimeUtils.getCurrentSeconds();

        Message message = new Message();
        message.setRole("assistant");
        message.setContent(content);

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setIndex(0);
        choice.setFinish_reason("stop");
        choice.setMessage(message);

        CompletionResponse result = new CompletionResponse();
        result.setModel(model);
        result.setCreated(timestamp);
        result.setChoices(Lists.newArrayList(choice));
        result.setUsage(buildTokenUsage(content));
        return result;
    }

    private StreamCompletionResponse buildStreamResponse(String content, String model) {
        Message delta = new Message();
        delta.setContent(content);

        StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setDelta(delta);

        StreamCompletionResponse result = new StreamCompletionResponse();
        result.setModel(model);
        result.setCreated(DateTimeUtils.getCurrentSeconds());
        result.setChoices(Lists.newArrayList(choice));
        return result;
    }

    private StreamCompletionResponse buildFinishResponse(String model) {
        StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setFinish_reason("stop");
        choice.setDelta(new Message());

        StreamCompletionResponse result = new StreamCompletionResponse();
        result.setModel(model);
        result.setCreated(DateTimeUtils.getCurrentSeconds());
        result.setChoices(Lists.newArrayList(choice));
        return result;
    }

    private CompletionResponse.TokenUsage buildTokenUsage(String content) {
        int tokens = Math.max(1, content.length() / 4);
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setCompletion_tokens(tokens);
        usage.setTotal_tokens(tokens);
        return usage;
    }

    private OpenapiResponse.OpenapiError buildError(String message) {
        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError();
        error.setMessage("Gemini调用失败: " + message);
        error.setType("gemini_error");
        return error;
    }

    private CompletionResponse buildErrorResponse(String message) {
        CompletionResponse response = new CompletionResponse();
        response.setError(buildError(message));
        return response;
    }

    @Override
    public String getDescription() {
        return "Google Gemini协议";
    }

    @Override
    public Class<GeminiProperty> getPropertyClass() {
        return GeminiProperty.class;
    }
}

