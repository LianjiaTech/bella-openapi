package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Direct passthrough adaptor - wraps delegator for transparent passthrough
 * Similar to QueueAdaptor pattern
 *
 * Direct passthrough: InputStream → Channel → OutputStream
 * - HTTP mode: Write directly to HttpServletResponse
 * - SSE mode: Send directly to SseEmitter
 * - Async processing for logging, metrics
 */
@Slf4j
public class DirectPassthroughAdaptor implements CompletionAdaptor<CompletionProperty> {
    private final CompletionAdaptorDelegator<?> delegator;
    private final InputStream requestBody;
    private final AuthorizationProperty auth;
    private final HttpServletResponse httpResponse;
    private final SseEmitter sseEmitter;

    public DirectPassthroughAdaptor(CompletionAdaptorDelegator<?> delegator,
                                    InputStream requestBody,
                                    AuthorizationProperty auth,
                                    HttpServletResponse httpResponse) {
        this.delegator = delegator;
        this.requestBody = requestBody;
        this.auth = auth;
        this.httpResponse = httpResponse;
        this.sseEmitter = null;
    }

    public DirectPassthroughAdaptor(CompletionAdaptorDelegator<?> delegator,
                                    InputStream requestBody,
                                    AuthorizationProperty auth,
                                    SseEmitter sseEmitter) {
        this.delegator = delegator;
        this.requestBody = requestBody;
        this.auth = auth;
        this.sseEmitter = sseEmitter;
        this.httpResponse = null;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
        try {
            // Build request
            Request httpRequest = buildDirectRequest(url, property);

            // Execute request
            Response response = HttpUtils.httpRequest(httpRequest);

            // Write response directly to HttpServletResponse.OutputStream
            try (InputStream responseBody = response.body().byteStream();
                 OutputStream outputStream = httpResponse.getOutputStream()) {

                httpResponse.setContentType("application/json");
                httpResponse.setStatus(response.code());

                // Copy headers
                response.headers().toMultimap().forEach((name, values) -> {
                    values.forEach(value -> httpResponse.addHeader(name, value));
                });

                // Stream copy
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseBody.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            // Async processing for logging
            TaskExecutor.submit(() -> {
                // TODO: logging, metrics
            });

        } catch (IOException e) {
            log.error("Direct passthrough error", e);
            throw new RuntimeException(e);
        }

        return null; // Response already written
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
                                 Callbacks.StreamCompletionCallback callback) {
        Request httpRequest = buildDirectRequest(url, property);

        // Create DirectSseListener - sends immediately, processes async
        DirectSseListener directListener = new DirectSseListener(sseEmitter, callback);

        HttpUtils.streamRequest(httpRequest, directListener);
    }

    /**
     * Build HTTP request with InputStream body
     */
    private Request buildDirectRequest(String url, CompletionProperty property) {

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
        if (property.getExtraHeaders() != null) {
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
