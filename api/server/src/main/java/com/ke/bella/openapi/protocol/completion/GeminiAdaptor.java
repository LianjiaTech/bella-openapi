package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import com.google.common.collect.Lists;

/**
 * Google Gemini适配器 - 基于VertexAI SDK
 */
@Component("GeminiCompletion")
public class GeminiAdaptor implements CompletionAdaptorDelegator<GeminiProperty> {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAdaptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<String, CacheItem<VertexAI>> clientCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CacheItem<ChatSession>> sessionCache = new ConcurrentHashMap<>();
    private static final long CLIENT_TTL = 3600000L; // 1小时
    private static final long SESSION_TTL = 1800000L; // 30分钟

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
        validateProperty(property);
        try {
            VertexAI client = getClient(property);
            GenerativeModel model = new GenerativeModel(property.getDeployName(), client);
            ChatSession session = getSession(generateKey(request, property), model);

            String userMessage = extractUserMessage(request);
            GenerateContentResponse response = session.sendMessage(userMessage);

            CompletionResponse result = convertResponse(response, request.getModel());
            ResponseHelper.splitReasoningFromContent(result, property);
            result.setCreated(DateTimeUtils.getCurrentSeconds());
            return result;
        } catch (Exception e) {
            logger.error("Gemini调用失败", e);
            return createErrorResponse("Gemini调用失败: " + e.getMessage());
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, GeminiProperty property,
            StreamCompletionCallback callback) {
        validateProperty(property);
        try {
            VertexAI client = getClient(property);
            GenerativeModel model = new GenerativeModel(property.getDeployName(), client);
            String userMessage = extractUserMessage(request);

            model.generateContentStream(userMessage).forEach(response -> {
                StreamCompletionResponse streamResp = convertStreamResponse(response, request.getModel());
                if(streamResp != null)
                    callback.callback(streamResp);
            });

            callback.callback(createFinishResponse(request.getModel()));
            callback.finish();
        } catch (Exception e) {
            logger.error("Gemini流式调用失败", e);
            callback.callback(StreamCompletionResponse.builder()
                    .error(createError("Gemini流式调用失败: " + e.getMessage()))
                    .build());
            callback.finish();
        }
    }

    /**
     * 验证配置
     */
    private void validateProperty(GeminiProperty property) {
        if(property.getAuth() == null || property.getAuth().getType() != AuthorizationProperty.AuthType.GOOGLE_JSON) {
            throw new IllegalArgumentException("需要GOOGLE_JSON认证类型");
        }
        if(StringUtils.isBlank(property.getAuth().getJsonCredentials())) {
            throw new IllegalArgumentException("JSON凭据不能为空");
        }
        if(StringUtils.isBlank(property.getDeployName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        try {
            JsonNode json = objectMapper.readTree(property.getAuth().getJsonCredentials());
            if(!json.has("project_id") || !json.has("private_key")) {
                throw new IllegalArgumentException("无效的JSON凭据格式");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON凭据解析失败: " + e.getMessage());
        }
    }

    /**
     * 获取VertexAI客户端(带缓存)
     */
    private VertexAI getClient(GeminiProperty property) throws IOException {
        String jsonCreds = property.getAuth().getJsonCredentials();
        JsonNode json = objectMapper.readTree(jsonCreds);
        String projectId = json.get("project_id").asText();
        String location = StringUtils.defaultIfBlank(property.getLocation(), "us-central1");
        String key = projectId + ":" + location + ":" + jsonCreds.hashCode();

        CacheItem<VertexAI> cached = clientCache.get(key);
        if(cached != null && !cached.isExpired()) {
            return cached.getValue();
        }

        try {
            VertexAI client = createClient(projectId, location, jsonCreds);
            clientCache.put(key, new CacheItem<>(client, CLIENT_TTL));
            return client;
        } catch (Exception e) {
            throw new IOException("创建VertexAI客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建VertexAI客户端
     */
    private VertexAI createClient(String projectId, String location, String jsonCreds) throws Exception {
        File tempFile = null;
        try {
            // 创建临时凭据文件
            tempFile = new File(System.getProperty("user.home"),
                    "gcp-" + System.currentTimeMillis() + ".json");
            tempFile.deleteOnExit();

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writer.write(jsonCreds);
            }

            // 设置环境变量
            setEnvVar("GOOGLE_APPLICATION_CREDENTIALS", tempFile.getAbsolutePath());
            System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", tempFile.getAbsolutePath());

            return new VertexAI(projectId, location);
        } finally {
            System.clearProperty("GOOGLE_APPLICATION_CREDENTIALS");
            if(tempFile != null) {
                scheduleFileCleanup(tempFile);
            }
        }
    }

    /**
     * 设置环境变量
     */
    @SuppressWarnings("unchecked")
    private void setEnvVar(String key, String value) {
        try {
            Class<?> processEnvClass = Class.forName("java.lang.ProcessEnvironment");
            java.lang.reflect.Field envField = processEnvClass.getDeclaredField("theEnvironment");
            envField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) envField.get(null);
            env.put(key, value);

            java.lang.reflect.Field ciEnvField = processEnvClass.getDeclaredField("theCaseInsensitiveEnvironment");
            ciEnvField.setAccessible(true);
            Map<String, String> ciEnv = (Map<String, String>) ciEnvField.get(null);
            ciEnv.put(key, value);
        } catch (Exception e) {
            // 回退到System.getenv()修改
            try {
                Map<String, String> env = System.getenv();
                java.lang.reflect.Field field = env.getClass().getDeclaredField("m");
                field.setAccessible(true);
                Map<String, String> map = (Map<String, String>) field.get(env);
                map.put(key, value);
            } catch (Exception ignored) {
                // 最后的回退 - 只设置系统属性
            }
        }
    }

    /**
     * 异步清理临时文件
     */
    private void scheduleFileCleanup(File file) {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if(file.exists())
                    file.delete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 获取ChatSession(带缓存)
     */
    private ChatSession getSession(String key, GenerativeModel model) {
        CacheItem<ChatSession> cached = sessionCache.get(key);
        if(cached != null && !cached.isExpired()) {
            return cached.getValue();
        }

        ChatSession session = new ChatSession(model);
        sessionCache.put(key, new CacheItem<>(session, SESSION_TTL));
        return session;
    }

    /**
     * 生成缓存键
     */
    private String generateKey(CompletionRequest request, GeminiProperty property) {
        return String.valueOf((request.toString() + property.getDeployName()).hashCode());
    }

    /**
     * 提取用户消息
     */
    private String extractUserMessage(CompletionRequest request) {
        if(request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("消息列表为空");
        }

        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            Message msg = request.getMessages().get(i);
            if("user".equals(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent().toString();
            }
        }
        throw new IllegalArgumentException("未找到用户消息");
    }

    /**
     * 转换响应格式
     */
    private CompletionResponse convertResponse(GenerateContentResponse response, String model) {
        String content = ResponseHandler.getText(response);

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setIndex(0);
        choice.setFinish_reason("stop");

        Message message = new Message();
        message.setRole("assistant");
        message.setContent(content);
        choice.setMessage(message);

        CompletionResponse result = new CompletionResponse();
        result.setModel(model);
        result.setCreated(DateTimeUtils.getCurrentSeconds());
        result.setChoices(Lists.newArrayList(choice));

        // Token统计
        int tokens = Math.max(1, content.length() / 4);
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setCompletion_tokens(tokens);
        usage.setTotal_tokens(tokens);
        result.setUsage(usage);

        return result;
    }

    /**
     * 转换流式响应
     */
    private StreamCompletionResponse convertStreamResponse(GenerateContentResponse response, String model) {
        String content = ResponseHandler.getText(response);
        if(StringUtils.isBlank(content))
            return null;

        StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
        choice.setIndex(0);

        Message delta = new Message();
        delta.setContent(content);
        choice.setDelta(delta);

        StreamCompletionResponse result = new StreamCompletionResponse();
        result.setModel(model);
        result.setCreated(DateTimeUtils.getCurrentSeconds());
        result.setChoices(Lists.newArrayList(choice));

        return result;
    }

    /**
     * 创建结束响应
     */
    private StreamCompletionResponse createFinishResponse(String model) {
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

    /**
     * 创建错误对象
     */
    private OpenapiResponse.OpenapiError createError(String message) {
        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError();
        error.setMessage(message);
        error.setType("gemini_error");
        return error;
    }

    /**
     * 创建错误响应
     */
    private CompletionResponse createErrorResponse(String message) {
        CompletionResponse response = new CompletionResponse();
        response.setError(createError(message));
        return response;
    }

    @Override
    public String getDescription() {
        return "Google Gemini协议 (VertexAI SDK)";
    }

    @Override
    public Class<GeminiProperty> getPropertyClass() {
        return GeminiProperty.class;
    }

    /**
     * 通用缓存项
     */
    private static class CacheItem<T> {
        private final T value;
        private final long expiry;

        CacheItem(T value, long ttl) {
            this.value = value;
            this.expiry = System.currentTimeMillis() + ttl;
        }

        T getValue() {
            return value;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }
}
