package com.ke.bella.openapi.protocol.gemini;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.VertexConverter;
import com.ke.bella.openapi.protocol.completion.VertexProperty;
import com.ke.bella.openapi.protocol.completion.gemini.Content;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.protocol.completion.gemini.Part;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Vertex Gemini 适配器
 * <p>
 * 实现 Google Vertex AI Gemini API 的透传架构。
 * 它将请求直接转发到 Vertex AI，并将响应流式传回客户端，以确保完全的协议兼容性。
 * 日志记录通过异步方式处理。
 * </p>
 *
 * @see GeminiAdaptor
 * @see com.ke.bella.openapi.protocol.completion.VertexConverter
 */
@Slf4j
@Component("VertexAdaptor")
public class VertexAdaptor implements GeminiAdaptor<VertexProperty> {

    @Autowired
    private EndpointLogger endpointLogger;

    @Override
    public String getDescription() {
        return "Vertex Gemini Adaptor - Transparent passthrough with async logging";
    }

    @Override
    public Class<?> getPropertyClass() {
        return VertexProperty.class;
    }

    @Override
    public void completion(GeminiRequest request, String url, VertexProperty property, HttpServletResponse response) {
        execute(request, url, property, response, false);
    }

    @Override
    public void streamCompletion(GeminiRequest request, String url, VertexProperty property, HttpServletResponse response) {
        execute(request, url, property, response, true);
    }

    /**
     * 执行对 Vertex AI 的请求并将响应流式传回客户端。
     *
     * @param request      Gemini 请求对象
     * @param url          渠道配置的上游 URL
     * @param property     Vertex 协议属性（认证、头信息）
     * @param httpResponse 客户端响应对象
     * @param isStream     是否使用流式模式
     */
    private void execute(GeminiRequest request, String url, VertexProperty property, HttpServletResponse httpResponse, boolean isStream) {
        // Build URL
        String vertexUrl = buildVertexUrl(url, isStream);

        // Build Request Body
        byte[] requestBytes = JacksonUtils.toByte(request);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestBytes);

        // Build Request
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(vertexUrl)
                .post(body)
                .header("Content-Type", "application/json");

        // Add extra headers
        if (MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::header);
        }

        Request upstreamRequest = builder.build();
        EndpointProcessData processData = EndpointContext.getProcessData();

        // Execute & Copy
        ByteArrayOutputStream auditBuffer = new ByteArrayOutputStream();
        try {
            Response response = HttpUtils.httpRequest(upstreamRequest);

            // Set response headers
            httpResponse.setStatus(response.code());
            String contentType = response.header("Content-Type");
            if (contentType != null) {
                httpResponse.setContentType(contentType);
            }

            // Stream Copy & Buffer
            try (InputStream responseBody = response.body().byteStream();
                 OutputStream clientStream = httpResponse.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseBody.read(buffer)) != -1) {
                    clientStream.write(buffer, 0, bytesRead);
                    auditBuffer.write(buffer, 0, bytesRead);
                    if (isStream) {
                        clientStream.flush();
                    }
                }
            }

            // Async Auditing
            TaskExecutor.submit(() -> {
                try {
                    CompletionResponse logResponse = parseResponseForLogging(auditBuffer.toByteArray(), isStream);
                    if (logResponse != null) {
                        processData.setResponse(logResponse);
                        if (logResponse.getUsage() != null) {
                            processData.setUsage(logResponse.getUsage());
                        }
                        endpointLogger.log(processData);
                    }
                } catch (Exception e) {
                    log.warn("Failed to audit gemini response", e);
                }
            });

        } catch (IOException e) {
            log.error("Gemini request failed", e);
            throw new RuntimeException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 Vertex AI 请求 URL
     *
     * @param baseUrl     Channel 配置的基础 URL
     * @param isStreaming 是否为流式请求
     * @return 完整的 API URL
     */
    private String buildVertexUrl(String baseUrl, boolean isStreaming) {
        String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (isStreaming) {
            return cleanUrl + ":streamGenerateContent?alt=sse";
        } else {
            return cleanUrl + ":generateContent";
        }
    }

    /**
     * 解析响应用于日志记录 (转换为 OpenAI 格式)
     *
     * @param responseBody 响应体字节数据
     * @param isStream     是否为流式响应
     * @return 转换后的 OpenAI 格式响应对象，解析失败返回 null
     */
    private CompletionResponse parseResponseForLogging(byte[] responseBody, boolean isStream) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }

        String responseStr = new String(responseBody);

        if (!isStream) {
            try {
                GeminiResponse geminiResponse = JacksonUtils.deserialize(responseStr, GeminiResponse.class);
                return VertexConverter.convertToOpenAIResponse(geminiResponse);
            } catch (Exception e) {
                log.warn("Failed to parse Gemini JSON response for logging", e);
                return null;
            }
        } else {
            return parseSseResponse(responseStr);
        }
    }

    /**
     * 解析 SSE 流式响应并聚合内容
     *
     * @param responseStr SSE 格式字符串
     * @return 聚合后的 CompletionResponse (包含完整文本和 Token 使用量)
     */
    private CompletionResponse parseSseResponse(String responseStr) {
        CompletionResponse finalResponse = new CompletionResponse();
        StringBuilder fullContent = new StringBuilder();
        CompletionResponse.TokenUsage finalUsage = null;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(responseStr))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String jsonData = trimmed.substring(5).trim();

                    if (jsonData.equals("[DONE]") || jsonData.isEmpty()) {
                        continue;
                    }

                    try {
                        GeminiResponse chunk = JacksonUtils.deserialize(jsonData, GeminiResponse.class);
                        if (chunk != null) {
                            if (chunk.getCandidates() != null && !chunk.getCandidates().isEmpty()) {
                                Content content = chunk.getCandidates().get(0).getContent();
                                if (content != null && content.getParts() != null) {
                                    for (Part part : content.getParts()) {
                                        if (part.getText() != null) {
                                            fullContent.append(part.getText());
                                        }
                                    }
                                }
                            }
                            if (chunk.getUsageMetadata() != null) {
                                CompletionResponse chunkResponse = VertexConverter.convertToOpenAIResponse(chunk);
                                if (chunkResponse != null && chunkResponse.getUsage() != null) {
                                    finalUsage = chunkResponse.getUsage();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parse errors for individual lines
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse SSE response", e);
        }

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        com.ke.bella.openapi.protocol.completion.Message msg = new com.ke.bella.openapi.protocol.completion.Message();
        msg.setRole("assistant");
        msg.setContent(fullContent.toString());
        choice.setMessage(msg);

        finalResponse.setChoices(Collections.singletonList(choice));
        finalResponse.setUsage(finalUsage);

        return finalResponse;
    }
}
