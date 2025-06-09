package com.ke.bella.openapi.protocol.completion;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.Chat;
import com.google.genai.AsyncChat;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Google Vertex AI协议适配器
 */
@Component("VertexCompletion")
public class VertexAdaptor implements CompletionAdaptor<VertexProperty> {

    private static final Logger logger = LoggerFactory.getLogger(VertexAdaptor.class);
    public final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = new Callbacks.DefaultSseConverter();

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, VertexProperty property) {
        try {
            Client client = getClient(property);

            Chat chat = client.chats.create(property.getDeployName());
            Content userContent = buildContentFromRequest(request);
            GenerateContentConfig config = buildConfig(request);
            GenerateContentResponse response = chat.sendMessage(userContent, config);

            CompletionResponse result = buildCompletionResponse(response, request.getModel());
            ResponseHelper.splitReasoningFromContent(result, property);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, VertexProperty property, StreamCompletionCallback callback) {
        CompletionSseListener listener = new CompletionSseListener(callback, sseConverter);
        processVertexStream(request, property, listener);
    }

    private void processVertexStream(CompletionRequest request, VertexProperty property, CompletionSseListener listener) {
        listener.setConnectionInitFuture(new CompletableFuture<>());
        listener.onOpen(null, null);

        try {
            Client client = getClient(property);
            AsyncChat asyncChat = client.async.chats.create(property.getDeployName());
            Content userContent = buildContentFromRequest(request);
            GenerateContentConfig config = buildConfig(request);

            asyncChat.sendMessageStream(userContent, config)
                    .thenAccept(responseStream -> {
                        try {
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
                            responseStream.close();
                        } catch (Exception e) {
                            handleStreamError(listener, e, "流式响应处理失败");
                        } finally {
                            listener.onClosed(null);
                        }
                    })
                    .exceptionally(throwable -> {
                        handleStreamError(listener, throwable, "AsyncChat流式调用失败");
                        return null;
                    });

        } catch (Exception e) {
            handleStreamError(listener, e, "Vertex AI流式处理异常");
        }
    }

    private void sendEvent(CompletionSseListener listener, Object data) {
        String eventData = data instanceof String ? (String) data : JacksonUtils.serialize(data);
        listener.onEvent(null, null, null, eventData);
    }

    private void handleStreamError(CompletionSseListener listener, Throwable throwable, String message) {
        logger.error(message, throwable);
        sendEvent(listener, StreamCompletionResponse.builder().error(buildError(throwable.getMessage())).build());
        listener.onClosed(null);
    }

    private Client getClient(VertexProperty property) {
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
        choice.setFinish_reason(choice.getFinish_reason());
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

        int basePromptTokens = usageMetadata.promptTokenCount().orElse(0);
        int cachedTokens = usageMetadata.cachedContentTokenCount().orElse(0);
        int toolPromptTokens = usageMetadata.toolUsePromptTokenCount().orElse(0);
        int candidatesTokens = usageMetadata.candidatesTokenCount().orElse(0);
        int thoughtsTokens = usageMetadata.thoughtsTokenCount().orElse(0);
        Integer apiTotalTokens = usageMetadata.totalTokenCount().orElse(null);

        int promptTokens = basePromptTokens + cachedTokens + toolPromptTokens;
        int completionTokens = candidatesTokens + thoughtsTokens;
        int totalTokens = apiTotalTokens != null ? apiTotalTokens : (promptTokens + completionTokens);

        if(apiTotalTokens != null && apiTotalTokens < (promptTokens + completionTokens)) {
            totalTokens = promptTokens + completionTokens;
        }

        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(promptTokens);
        usage.setCompletion_tokens(completionTokens);
        usage.setTotal_tokens(totalTokens);

        return usage;
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

    private Content buildContentFromRequest(CompletionRequest request) {
        if(request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("消息列表为空");
        }

        String userMessage = request.getMessages().stream()
                .filter(msg -> "user".equals(msg.getRole()) && msg.getContent() != null)
                .reduce((first, second) -> second)
                .map(msg -> msg.getContent().toString())
                .orElseThrow(() -> new IllegalArgumentException("未找到用户消息"));

        return Content.fromParts(Part.fromText(userMessage));
    }

}
