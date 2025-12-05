package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * Direct passthrough adaptor - decorates existing adaptor to bypass processing
 * Similar to QueueAdaptor pattern, wraps the delegator for transparent passthrough
 *
 * This adaptor bypasses all request/response processing and directly streams:
 * - HTTP mode: InputStream → Channel → HttpServletResponse.OutputStream
 * - SSE mode: InputStream → Channel → SseEmitter (immediate send)
 */
@Slf4j
public class DirectPassthroughAdaptor<T extends CompletionProperty> implements CompletionAdaptor<T> {
    private final CompletionAdaptorDelegator<T> delegator;
    private final InputStream requestBody;
    private final HttpServletResponse httpResponse;
    private final SseEmitter sseEmitter;

    /**
     * Constructor for non-streaming (HTTP) mode
     */
    public DirectPassthroughAdaptor(CompletionAdaptorDelegator<T> delegator,
                                    InputStream requestBody,
                                    HttpServletResponse httpResponse) {
        this.delegator = delegator;
        this.requestBody = requestBody;
        this.httpResponse = httpResponse;
        this.sseEmitter = null;
    }

    /**
     * Constructor for streaming (SSE) mode
     */
    public DirectPassthroughAdaptor(CompletionAdaptorDelegator<T> delegator,
                                    InputStream requestBody,
                                    SseEmitter sseEmitter) {
        this.delegator = delegator;
        this.requestBody = requestBody;
        this.sseEmitter = sseEmitter;
        this.httpResponse = null;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, T property) {
        // HTTP mode - direct passthrough
        try {
            // Build request with InputStream
            Request httpRequest = buildDirectRequest(url, property);

            // Execute request and get response
            Response response = com.ke.bella.openapi.utils.HttpUtils.httpRequest(httpRequest);

            // Write response body directly to HttpServletResponse output stream
            try (InputStream responseBody = response.body().byteStream();
                 OutputStream outputStream = httpResponse.getOutputStream()) {

                // Set content type
                httpResponse.setContentType("application/json");
                httpResponse.setStatus(response.code());

                // Copy headers
                response.headers().toMultimap().forEach((name, values) -> {
                    values.forEach(value -> httpResponse.addHeader(name, value));
                });

                // Direct stream copy - no buffering
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseBody.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            // Async processing - logging, metrics, etc.
            asyncProcessNonStreaming(request, url, property, response);

        } catch (IOException e) {
            log.error("Direct passthrough error", e);
            throw new RuntimeException(e);
        }

        // Return null as response is already written
        return null;
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, T property,
                                 Callbacks.StreamCompletionCallback callback) {
        // SSE mode - direct passthrough with async processing
        Request httpRequest = buildDirectRequest(url, property);

        // Create DirectSseListener that sends immediately and processes async
        DirectSseListener directListener = new DirectSseListener(sseEmitter, callback);

        // Execute streaming request
        com.ke.bella.openapi.utils.HttpUtils.streamRequest(httpRequest, directListener);
    }

    /**
     * Build HTTP request directly from InputStream
     */
    private Request buildDirectRequest(String url, T property) {
        // Create RequestBody that streams from InputStream
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

        Request.Builder builder = delegator.authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body);

        // Add extra headers if any
        if (property.getExtraHeaders() != null && !property.getExtraHeaders().isEmpty()) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }

    /**
     * Async processing for non-streaming responses
     * Handles logging, metrics, etc. without blocking the response
     */
    private void asyncProcessNonStreaming(CompletionRequest request, String url, T property, Response response) {
        // TODO: Implement async logging and metrics
        // Can parse response body from a copy if needed for logging
        // But don't block the main response
    }

    @Override
    public String getDescription() {
        return "Direct Passthrough - " + delegator.getDescription();
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegator.getPropertyClass();
    }
}
