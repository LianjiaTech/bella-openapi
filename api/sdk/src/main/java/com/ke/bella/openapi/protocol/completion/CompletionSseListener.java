package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableSet;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
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
        if(DONE_FLAGS.contains(msg)) {
            callback.done();
        } else {
            StreamCompletionResponse response = sseConverter.convert(id, type, msg);
            if(response != null) {
                callback.callback(response);
            }
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        callback.finish();
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        ChannelException exception = null;
        try {
            if(t == null) {
                exception = convertToException(response);
            } else {
                exception = ChannelException.fromException(t);
            }
        } catch (Exception e) {
            exception = ChannelException.fromException(e);
        } finally {
            if(connectionInitFuture.isDone()) {
                callback.finish(exception);
            } else {
                connectionInitFuture.completeExceptionally(exception);
            }
        }
    }


    public ChannelException convertToException(Response response) throws IOException {
        String msg;
        try {
            msg = response.body().string();
            StreamCompletionResponse streamCompletionResponse = sseConverter.convert(null, null, msg);
            if(streamCompletionResponse != null && streamCompletionResponse.getError() != null) {
                return new ChannelException.OpenAIException(response.code(), streamCompletionResponse.getError().getType(),
                        streamCompletionResponse.getError().getMessage(), streamCompletionResponse.getError());
            } else {
                return new ChannelException.OpenAIException(response.code(), HttpStatus.valueOf(response.code()).getReasonPhrase(), msg);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return ChannelException.fromResponse(response.code(), response.message());
        }
    }
}
