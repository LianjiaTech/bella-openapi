package com.ke.bella.openapi.protocol.completion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.callback.StreamCompletionCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * Direct passthrough adaptor - wraps delegator for transparent passthrough
 * Similar to QueueAdaptor pattern
 * Direct passthrough: InputStream → Channel → OutputStream
 * - Write directly to HttpServletResponse (supports both streaming and
 * non-streaming)
 * - Async processing for logging, metrics
 */
@Slf4j
public class DirectPassthroughAdaptor implements CompletionAdaptor<CompletionProperty> {
    private final CompletionAdaptorDelegator<?> delegator;
    private final InputStream requestBody;
    private final CompletionProperty property;
    private final HttpServletResponse httpResponse;
    private final EndpointLogger logger;

    public DirectPassthroughAdaptor(CompletionAdaptorDelegator<?> delegator,
            InputStream requestBody,
            CompletionProperty property,
            HttpServletResponse httpResponse, EndpointLogger logger) {
        this.delegator = delegator;
        this.requestBody = requestBody;
        this.property = property;
        this.httpResponse = httpResponse;
        this.logger = logger;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
        ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

        try {
            // Build request
            Request httpRequest = buildDirectRequest(url);

            // Execute request
            Response response = HttpUtils.httpRequest(httpRequest);
            EndpointProcessData processData = EndpointContext.getProcessData();
            ApikeyInfo apikeyInfo = BellaContext.getApikey();

            // Check if response is SSE format (text/event-stream)
            String contentType = response.header("Content-Type");
            boolean isSSE = contentType != null && contentType.contains("text/event-stream");

            // Write response directly to HttpServletResponse.OutputStream
            // Automatically handles both streaming (text/event-stream) and
            // non-streaming (application/json)
            try (InputStream responseBody = response.body().byteStream();
                    OutputStream outputStream = httpResponse.getOutputStream()) {

                // Set content type from upstream response
                if(contentType != null) {
                    httpResponse.setContentType(contentType);
                }
                httpResponse.setStatus(response.code());

                // Stream copy - works for both streaming and non-streaming
                // Buffer the response data for logging
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseBody.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush(); // Flush for streaming responses
                    // Save to buffer for logging
                    responseBuffer.write(buffer, 0, bytesRead);
                }
            }

            // Async processing for deserialization and logging
            TaskExecutor.submit(() -> {
                byte[] responseBytes = responseBuffer.toByteArray();
                CompletionResponse completionResponse = null;

                if(responseBytes.length > 0) {
                    String responseStr = new String(responseBytes);

                    if(isSSE) {
                        StreamCompletionCallback callback = new StreamCompletionCallback(null, processData, apikeyInfo, logger, null);
                        // Extract data from SSE format
                        // SSE format: "data: {...}\n\ndata: {...}\n\n[DONE]"
                        // Extract all "data: " lines and parse JSON
                        String[] lines = responseStr.split("\n");
                        for (String line : lines) {
                            if(line.startsWith("data: ") || line.startsWith("data:")) {
                                int index = line.startsWith("data: ") ? 6 : 5;
                                String jsonData = line.substring(index).trim();
                                if(!jsonData.equals("[DONE]") && !jsonData.isEmpty()) {
                                    StreamCompletionResponse streamCompletionResponse = JacksonUtils.deserialize(jsonData,
                                            StreamCompletionResponse.class);
                                    if(streamCompletionResponse != null) {
                                        callback.callback(streamCompletionResponse);
                                    }
                                } else if(jsonData.equals("[DONE]")) {
                                    callback.finish();
                                }
                            }
                        }
                    } else {
                        // Non-SSE response, deserialize directly
                        try {
                            completionResponse = JacksonUtils.deserialize(responseStr, CompletionResponse.class);
                            // Set response to EndpointContext
                            processData.setResponse(completionResponse);

                            // Log
                            logger.log(processData);
                        } catch (Exception e) {
                            log.warn("Failed to deserialize response to CompletionResponse, response: {}", responseStr, e);
                        }
                    }
                }
            });

        } catch (IOException e) {
            log.error("Direct passthrough error", e);
            throw new RuntimeException(e);
        }

        return null;
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
            Callbacks.StreamCompletionCallback callback) {
        // Direct mode doesn't use streamCompletion - everything goes through
        // completion()
        throw new UnsupportedOperationException("DirectPassthroughAdaptor uses completion() for both streaming and non-streaming");
    }

    /**
     * Build HTTP request with InputStream body
     */
    private Request buildDirectRequest(String url) {
        AuthorizationProperty auth = property.getAuth();

        // Create RequestBody from InputStream
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/json");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeAll(Okio.source(requestBody));
            }
        };

        // Build request with auth
        Request.Builder builder = delegator.authorizationRequestBuilder(auth)
                .url(url)
                .post(body);

        // Add extra headers
        if(property.getExtraHeaders() != null) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }

    @Override
    public String getDescription() {
        return "DirectPassthrough-" + delegator.getDescription();
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegator.getPropertyClass();
    }
}
