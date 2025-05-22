package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * A wrapper for SSE emitters that handles fallbacks and error cases
 */
@Slf4j
public class BellaStreamCallback {
    private final SseEmitter sse;
    private boolean hasError = false;
    private boolean isCompleted = false;

    public BellaStreamCallback(SseEmitter sse) {
        this.sse = sse;
    }

    /**
     * Send a stream response event
     */
    public void sendEvent(StreamCompletionResponse response) {
        if (isCompleted || hasError) {
            return;
        }
        try {
            SseHelper.sendEvent(sse, response);
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage());
            hasError = true;
        }
    }

    /**
     * Send an error response and complete the stream
     */
    public void sendError(Exception e) {
        if (isCompleted || hasError) {
            return;
        }
        hasError = true;
        
        OpenapiResponse.OpenapiError openapiError;
        if (e instanceof ChannelException) {
            openapiError = ((ChannelException) e).convertToOpenapiError();
        } else {
            openapiError = new OpenapiResponse.OpenapiError("internal_error", e.getMessage());
        }
        
        StreamCompletionResponse response = StreamCompletionResponse.builder()
                .error(openapiError)
                .build();
                
        try {
            SseHelper.sendEvent(sse, response);
            sse.complete();
        } catch (Exception ex) {
            log.error("Error sending error SSE event: {}", ex.getMessage());
        }
    }

    /**
     * Complete the stream
     */
    public void complete() {
        if (isCompleted || hasError) {
            return;
        }
        isCompleted = true;
        sse.complete();
    }
}
