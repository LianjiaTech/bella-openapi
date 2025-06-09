package com.ke.bella.openapi.protocol.completion;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Google Vertex AI协议适配器
 */
@Component("VertexCompletion")
public class VertexAdaptor implements CompletionAdaptorDelegator<VertexProperty> {

    private static final Logger logger = LoggerFactory.getLogger(VertexAdaptor.class);
    private static final ConcurrentHashMap<String, Client> clientCache = new ConcurrentHashMap<>();

    // SSE事件转换器
    public final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = new Callbacks.DefaultSseConverter();

    Callbacks.ChannelErrorCallback<CompletionResponse> errorCallback = (errorResponse, res) -> {
        if(errorResponse.getError() != null) {
            errorResponse.getError().setHttpCode(res.code());
        }
    };

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, VertexProperty property,
            com.ke.bella.openapi.protocol.Callbacks.HttpDelegator delegator) {
        return completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, VertexProperty property,
            StreamCompletionCallback callback, Callbacks.StreamDelegator delegator) {
        CompletionSseListener listener = new CompletionSseListener(callback, sseConverter);
        if(delegator == null) {
            processVertexStream(request, property, listener);
        } else {
            delegator.request(request, listener);
        }
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, VertexProperty property) {
        try {
            Client client = getClient(property);
            String userMessage = extractUserMessage(request);
            GenerateContentConfig config = buildConfig(request);
            GenerateContentResponse response = client.models.generateContent(property.getDeployName(), userMessage, config);

            CompletionResponse result = buildCompletionResponse(response, request.getModel());
            ResponseHelper.splitReasoningFromContent(result, property);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, VertexProperty property, StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    /**
     * 流式响应处理
     */
    private void processVertexStream(CompletionRequest request, VertexProperty property, CompletionSseListener listener) {
        CompletableFuture.runAsync(() -> {
            listener.setConnectionInitFuture(new CompletableFuture<>());
            listener.onOpen(null, null);

            try (ResponseStream<GenerateContentResponse> responseStream = getClient(property).models
                    .generateContentStream(property.getDeployName(), extractUserMessage(request), buildConfig(request))) {

                for (GenerateContentResponse response : responseStream) {
                    String content = response.text();
                    if(StringUtils.isNotBlank(content)) {
                        Message delta = new Message();
                        delta.setContent(content);
                        sendEvent(listener, createStreamResponse(request.getModel(), delta, null));
                    }
                }

                sendEvent(listener, createStreamResponse(request.getModel(), new Message(), "stop"));
                sendEvent(listener, "[DONE]");

            } catch (Exception e) {
                logger.error("Vertex AI流式处理异常", e);
                sendEvent(listener, StreamCompletionResponse.builder().error(buildError(e.getMessage())).build());
            } finally {
                listener.onClosed(null);
            }
        });
    }

    private void sendEvent(CompletionSseListener listener, Object data) {
        String eventData = data instanceof String ? (String) data : JacksonUtils.serialize(data);
        listener.onEvent(null, null, null, eventData);
    }

    /**
     * 获取客户端实例
     */
    private Client getClient(VertexProperty property) {
        String cacheKey = getCacheKey(property);

        return clientCache.computeIfAbsent(cacheKey, k -> {
            try {
                Map<String, Object> creds = property.getVertexAICredentials();
                String jsonCreds = JacksonUtils.serialize(creds);
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(jsonCreds.getBytes(StandardCharsets.UTF_8)))
                        .createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));

                return Client.builder()
                        .vertexAI(true)
                        .project(creds.get("project_id").toString())
                        .location(StringUtils.defaultIfBlank(property.getLocation(), "us-central1"))
                        .credentials(credentials)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("创建Vertex AI客户端失败: " + e.getMessage(), e);
            }
        });
    }

    private String getCacheKey(VertexProperty property) {
        Map<String, Object> creds = property.getVertexAICredentials();
        String projectId = creds != null && creds.get("project_id") != null ? creds.get("project_id").toString() : "unknown";
        return property.getLocation() + ":" + projectId;
    }

    private String extractUserMessage(CompletionRequest request) {
        if(request.getMessages() == null || request.getMessages().isEmpty()) {
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

        Optional.ofNullable(request.getMax_tokens())
                .filter(tokens -> tokens > 0)
                .ifPresent(builder::maxOutputTokens);

        Optional.ofNullable(request.getN())
                .filter(n -> n > 0)
                .ifPresent(builder::candidateCount);

        Optional.ofNullable(request.getTemperature())
                .ifPresent(temp -> builder.temperature(temp.floatValue()));

        Optional.ofNullable(request.getTop_p())
                .ifPresent(topP -> builder.topP(topP.floatValue()));

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
        result.setUsage(buildTokenUsage(response));
        return result;
    }

    private StreamCompletionResponse createStreamResponse(String model, Message delta, String finishReason) {
        StreamCompletionResponse.Choice choice = StreamCompletionResponse.Choice.builder()
                .index(0)
                .delta(delta)
                .finish_reason(finishReason)
                .build();

        return StreamCompletionResponse.builder()
                .model(model)
                .created(DateTimeUtils.getCurrentSeconds())
                .choices(Lists.newArrayList(choice))
                .build();
    }

    private CompletionResponse.TokenUsage buildTokenUsage(GenerateContentResponse response) {
        GenerateContentResponseUsageMetadata usageMetadata = response.usageMetadata().get();

        int basePromptTokens = safeOptionalIntValue(usageMetadata.promptTokenCount());
        int cachedTokens = safeOptionalIntValue(usageMetadata.cachedContentTokenCount());
        int toolPromptTokens = safeOptionalIntValue(usageMetadata.toolUsePromptTokenCount());
        int candidatesTokens = safeOptionalIntValue(usageMetadata.candidatesTokenCount());
        int thoughtsTokens = safeOptionalIntValue(usageMetadata.thoughtsTokenCount());
        Integer apiTotalTokens = usageMetadata.totalTokenCount().orElse(null);

        int promptTokens = basePromptTokens + cachedTokens + toolPromptTokens;
        int completionTokens = candidatesTokens + thoughtsTokens;
        int totalTokens = apiTotalTokens != null ? apiTotalTokens : (promptTokens + completionTokens);

        if(apiTotalTokens != null && apiTotalTokens < (promptTokens + completionTokens)) {
            logger.warn("Vertex AI API返回的totalTokenCount({})小于计算的输入+输出({}+{}={}), 使用计算值",
                    apiTotalTokens, promptTokens, completionTokens, promptTokens + completionTokens);
            totalTokens = promptTokens + completionTokens;
        }

        logger.debug("Vertex AI token分解: prompt={}(base:{}, cached:{}, tool:{}), completion={}(candidates:{}, thoughts:{}), total={}",
                promptTokens, basePromptTokens, cachedTokens, toolPromptTokens,
                completionTokens, candidatesTokens, thoughtsTokens, totalTokens);

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(promptTokens);
        usage.setCompletion_tokens(completionTokens);
        usage.setTotal_tokens(totalTokens);

        return usage;
    }

    private int safeOptionalIntValue(Optional<Integer> optionalValue) {
        return optionalValue.orElse(0);
    }

    private OpenapiResponse.OpenapiError buildError(String message) {
        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError();
        error.setMessage("Vertex AI调用失败: " + message);
        error.setType("vertex_error");
        return error;
    }

    @Override
    public void validateChannelInfo(String channelInfo) {
        try {
            VertexProperty property = JacksonUtils.deserialize(channelInfo, VertexProperty.class);
            Map<String, Object> creds = property.getVertexAICredentials();
            if(creds == null || creds.isEmpty() || StringUtils.isBlank(property.getDeployName())) {
                throw new IllegalArgumentException("配置无效：需要vertexAICredentials和deployName");
            }

            // 验证必要字段
            if(creds.get("project_id") == null || creds.get("private_key") == null || creds.get("client_email") == null) {
                throw new IllegalArgumentException("缺少必要字段：project_id、private_key、client_email");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Vertex AI渠道配置验证失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        return "Google Vertex AI协议";
    }

    @Override
    public Class<VertexProperty> getPropertyClass() {
        return VertexProperty.class;
    }
}
