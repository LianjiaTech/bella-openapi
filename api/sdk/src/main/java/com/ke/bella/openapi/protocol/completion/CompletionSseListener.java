package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableSet;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Set;

@Slf4j
public class CompletionSseListener extends BellaEventSourceListener {
    private Callbacks.StreamCompletionCallback callback;
    private Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter;
    private final Set<String> DONE_FLAGS = ImmutableSet.of("[DONE]");
    private String lastEventId;
    private String lastEventType;
    private boolean receivedDone;
    private final long startedAtMs = System.currentTimeMillis();
    @Setter
    private SseLogContext logContext;

    @Getter
    @Builder
    public static class SseLogContext {
        private String source;
        private String traceId;
        private String requestId;
        private String queueName;
        private String endpoint;
        private String model;
    }

    public CompletionSseListener(Callbacks.StreamCompletionCallback sseCallback,
            Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter) {
        this.callback = sseCallback;
        this.sseConverter = sseConverter;
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        callback.onOpen();
        super.onOpen(eventSource, response);
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String msg) {
        lastEventId = id;
        lastEventType = type;
        try {
            if(DONE_FLAGS.contains(msg)) {
                receivedDone = true;
                callback.done();
            } else {
                StreamCompletionResponse response = sseConverter.convert(id, type, msg);
                if(response != null) {
                    callback.callback(response);
                }
            }
        } catch (Exception e) {
            SseLogContext ctx = logContext;
            if(ctx != null) {
                log.warn("SSE onEvent callback failed, source={}, traceId={}, requestId={}, queue={}, endpoint={}, model={}, lastEventId={}, lastEventType={}, receivedDone={}, exceptionClass={}, exceptionMessage={}",
                        ctx.getSource(), ctx.getTraceId(), ctx.getRequestId(), ctx.getQueueName(), ctx.getEndpoint(), ctx.getModel(),
                        lastEventId, lastEventType, receivedDone, e.getClass().getName(), e.getMessage(), e);
            }
            throw e;
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        SseLogContext ctx = logContext;
        if(ctx != null) {
            log.info("SSE onClosed, source={}, traceId={}, requestId={}, queue={}, endpoint={}, model={}, lastEventId={}, lastEventType={}, receivedDone={}, elapsedMs={}",
                    ctx.getSource(), ctx.getTraceId(), ctx.getRequestId(), ctx.getQueueName(), ctx.getEndpoint(), ctx.getModel(),
                    lastEventId, lastEventType, receivedDone,
                    System.currentTimeMillis() - startedAtMs);
        }
        callback.finish();
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        BellaException exception = null;
        try {
            if(t == null) {
                exception = convertToException(response);
            } else {
                exception = BellaException.fromException(t);
            }
        } catch (Exception e) {
            exception = BellaException.fromException(e);
        } finally {
            if(connectionInitFuture.isDone()) {
                callback.finish(exception);
            } else {
                connectionInitFuture.completeExceptionally(exception);
            }
            SseLogContext ctx = logContext;
            if(ctx != null) {
                log.warn("SSE onFailure, source={}, traceId={}, requestId={}, queue={}, endpoint={}, model={}, lastEventId={}, lastEventType={}, receivedDone={}, responseCode={}, exceptionClass={}, exceptionMessage={}, elapsedMs={}",
                        ctx.getSource(), ctx.getTraceId(), ctx.getRequestId(), ctx.getQueueName(), ctx.getEndpoint(), ctx.getModel(),
                        lastEventId, lastEventType, receivedDone,
                        response == null ? null : response.code(),
                        t == null ? null : t.getClass().getName(),
                        t == null ? null : t.getMessage(),
                        System.currentTimeMillis() - startedAtMs,
                        t);
            }
        }
    }

    public BellaException convertToException(Response response) throws IOException {
        String msg;
        try {
            msg = response.body().string();
            StreamCompletionResponse streamCompletionResponse = sseConverter.convert(null, null, msg);
            if(streamCompletionResponse != null && streamCompletionResponse.getError() != null) {
                return new BellaException.ChannelException(response.code(), streamCompletionResponse.getError().getType(),
                        streamCompletionResponse.getError().getMessage(), streamCompletionResponse.getError());
            } else {
                return new BellaException.ChannelException(response.code(), HttpStatus.valueOf(response.code()).getReasonPhrase(), msg);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return BellaException.fromResponse(response.code(), response.message());
        }
    }
}
